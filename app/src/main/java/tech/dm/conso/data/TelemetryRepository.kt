package tech.dm.conso.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tech.dm.conso.eco.EcoResult
import tech.dm.conso.eco.EcoScorer
import tech.dm.conso.obd.BluetoothTransport
import tech.dm.conso.obd.Elm327
import tech.dm.conso.obd.FuelCalculator
import tech.dm.conso.obd.FuelMethod
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.Pids
import tech.dm.conso.obd.SimulatedElm327
import tech.dm.conso.obd.Vin
import tech.dm.conso.obd.VinInfo
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

sealed interface ObdSource {
    val label: String
    data object Simulator : ObdSource { override val label = "Simulateur" }
    data class Bluetooth(val device: BluetoothDevice, override val label: String) : ObdSource
}

data class Sample(
    val wallMs: Long,
    val speedKmh: Double,
    val litersPerHour: Double?,
    val altitudeM: Double?,
    val grade: Double,
    val ecoScore: Double?,
)

data class LiveState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val sourceLabel: String? = null,
    val error: String? = null,
    val retrying: Boolean = false,
    val method: FuelMethod = FuelMethod.NONE,
    val fuelType: FuelType = FuelType.GASOLINE,
    val detectedVehicle: VinInfo? = null,
    val speedKmh: Double = 0.0,
    val rpm: Double? = null,
    val litersPerHour: Double? = null,
    val litersPer100: Double? = null,
    val tripKm: Double = 0.0,
    val tripLiters: Double = 0.0,
    val altitudeM: Double? = null,
    val grade: Double = 0.0,
    val location: GeoPoint? = null,
    val altitudeSource: String? = null,
    val eco: EcoResult? = null,
    val samples: List<Sample> = emptyList(),
) {
    val tripAvgL100: Double? get() = if (tripKm > 0.2) tripLiters / tripKm * 100.0 else null
}

class TelemetryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    val history = DailyHistory(File(appContext.filesDir, "history.json"))
    val segments = SegmentStore(File(appContext.filesDir, "segments.jsonl"))
    val roads = RoadNames(appContext, File(appContext.filesDir, "roads.json"))
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion.asStateFlow()

    private val profileStore = VehicleProfileStore(appContext)
    private val _profile = MutableStateFlow(profileStore.load())
    val profile: StateFlow<VehicleProfile> = _profile.asStateFlow()

    private val terrain = TerrainElevation()
    private var job: Job? = null

    fun saveProfile(p: VehicleProfile) {
        profileStore.save(p)
        _profile.value = p
    }

    fun connect(source: ObdSource, retry: Boolean = false, maxAttempts: Int = MAX_RETRIES) {
        if (retry && job?.isActive == true) return
        disconnect()
        job = scope.launch {
            var attempt = 0
            while (true) {
                val willRetry = retry && attempt + 1 < maxAttempts
                run(source, willRetry)
                if (_state.value.status != ConnectionStatus.ERROR || !willRetry) break
                attempt++
                delay(RETRY_DELAY_MS)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        history.save()
        _historyVersion.update { it + 1 }
        _state.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
    }

    private suspend fun run(source: ObdSource, willRetry: Boolean) {
        _state.value = LiveState(status = ConnectionStatus.CONNECTING, sourceLabel = source.label)
        val position: PositionSource
        val elm: Elm327
        when (source) {
            is ObdSource.Simulator -> SimulatedElm327().let { position = it; elm = Elm327(it) }
            is ObdSource.Bluetooth -> {
                position = DevicePositionSource(appContext)
                elm = Elm327(BluetoothTransport(source.device))
            }
        }
        val tripBuilder = SegmentBuilder(System.currentTimeMillis()) {
            segments.append(it)
            _historyVersion.update { v -> v + 1 }
        }
        try {
            elm.initialize()
            val fuelType = if (elm.supports(Pids.FUEL_TYPE)) {
                elm.query(Pids.FUEL_TYPE)?.firstOrNull()?.let(FuelType::fromPid51) ?: FuelType.GASOLINE
            } else _profile.value.fuel
            val detected = elm.readVin()?.let(Vin::decode)
            val method = FuelCalculator.chooseMethod(elm::supports, fuelType)
            _state.update {
                it.copy(status = ConnectionStatus.CONNECTED, method = method, fuelType = fuelType, detectedVehicle = detected)
            }
            position.start()
            val terrainJob = if (position.hasPreciseAltitude) null else terrain.start(scope)
            try {
                poll(elm, position, method, fuelType, tripBuilder)
            } finally {
                terrainJob?.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = if (source is ObdSource.Bluetooth && e is java.io.IOException) {
                "Module ${source.label} injoignable : contact coupé ou hors de portée"
            } else e.message ?: e.javaClass.simpleName
            _state.update { it.copy(status = ConnectionStatus.ERROR, error = message, retrying = willRetry) }
        } finally {
            tripBuilder.flush()
            position.stop()
            elm.close()
            history.save()
        }
    }

    private suspend fun poll(
        elm: Elm327,
        position: PositionSource,
        method: FuelMethod,
        fuelType: FuelType,
        tripBuilder: SegmentBuilder,
    ) {
        val scorer = EcoScorer(_profile.value.vehicleModel, fuelType)
        val grade = GradeEstimator()
        val terrainGrade = TerrainGrade(terrain)
        val altitudeSource = when {
            position is SimulatedElm327 -> "simulateur"
            position.hasPreciseAltitude -> "baromètre"
            else -> "modèle de terrain"
        }
        var lastTime = SystemClock.elapsedRealtime()
        var lastSpeed = 0.0
        var accel = 0.0
        var distanceM = 0.0
        var lastSave = lastTime
        var lastSampleAt = 0L
        var ecoTrend: Double? = null

        while (scope.isActive) {
            val speed = elm.query(Pids.SPEED)?.let(Pids::speedKmh) ?: 0.0
            val rpm = elm.query(Pids.RPM)?.let(Pids::rpm)
            val lh = when (method) {
                FuelMethod.FUEL_RATE -> elm.query(Pids.FUEL_RATE)?.let(Pids::fuelRateLh)
                FuelMethod.MAF -> elm.query(Pids.MAF)?.let { FuelCalculator.fromMaf(Pids.mafGs(it), fuelType) }
                FuelMethod.SPEED_DENSITY -> {
                    val map = elm.query(Pids.MAP)?.let(Pids::mapKpa)
                    val iat = elm.query(Pids.INTAKE_TEMP)?.let(Pids::intakeTempC)
                    if (map != null && iat != null && rpm != null) {
                        val maf = FuelCalculator.mafFromSpeedDensity(rpm, map, iat, _profile.value.displacementL)
                        FuelCalculator.fromMaf(maf, fuelType)
                    } else null
                }
                FuelMethod.NONE -> null
            }

            val now = SystemClock.elapsedRealtime()
            val wall = System.currentTimeMillis()
            val dt = ((now - lastTime) / 1000.0).coerceIn(0.0, 5.0)
            lastTime = now
            val stepM = speed / 3.6 * dt
            distanceM += stepM
            if (dt > 0) accel += ((speed - lastSpeed) / 3.6 / dt - accel) * 0.25
            lastSpeed = speed
            val loc = position.location
            val (alt, g) = if (position.hasPreciseAltitude) {
                position.altitudeM.let { it to grade.update(distanceM, it) }
            } else {
                terrainGrade.update(distanceM, loc)
            }
            val eco = scorer.evaluate(speed, accel, g, rpm, lh)
            val fuelStep = (lh ?: 0.0) * dt / 3600.0
            history.add(LocalDate.now(), fuelStep, stepM / 1000.0, if (speed >= 3) eco.score else null)
            if (lh != null) tripBuilder.add(wall, loc, stepM / 1000.0, fuelStep, eco.expectedLh * dt / 3600.0, g)

            if (speed >= 3) ecoTrend = ecoTrend?.let { it + (eco.instantScore - it) * 0.2 } ?: eco.instantScore
            val takeSample = now - lastSampleAt >= SAMPLE_PERIOD_MS
            if (takeSample) lastSampleAt = now
            _state.update { s ->
                s.copy(
                    speedKmh = speed,
                    rpm = rpm,
                    litersPerHour = lh,
                    litersPer100 = lh?.let { FuelCalculator.per100km(it, speed) },
                    tripKm = s.tripKm + stepM / 1000.0,
                    tripLiters = s.tripLiters + fuelStep,
                    altitudeM = alt,
                    grade = g,
                    location = loc,
                    altitudeSource = altitudeSource,
                    eco = eco,
                    samples = if (takeSample) (s.samples + Sample(wall, speed, lh, alt, g, ecoTrend)).takeLast(MAX_SAMPLES) else s.samples,
                )
            }

            if (now - lastSave > 30_000) {
                history.save()
                _historyVersion.update { it + 1 }
                lastSave = now
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    companion object {
        const val MAX_SAMPLES = 4 * 3600
        const val SAMPLE_PERIOD_MS = 1_000L
        const val POLL_INTERVAL_MS = 250L
        const val MAX_RETRIES = 20
        const val RETRY_DELAY_MS = 15_000L
    }
}
