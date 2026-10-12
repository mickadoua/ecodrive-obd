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
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

sealed interface ObdSource {
    val label: String
    data object Simulator : ObdSource { override val label = "Simulateur" }
    data class Bluetooth(val device: BluetoothDevice, override val label: String) : ObdSource
}

data class Sample(val timeMs: Long, val speedKmh: Double, val litersPerHour: Double?, val altitudeM: Double?)

data class LiveState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val sourceLabel: String? = null,
    val error: String? = null,
    val method: FuelMethod = FuelMethod.NONE,
    val fuelType: FuelType = FuelType.GASOLINE,
    val speedKmh: Double = 0.0,
    val rpm: Double? = null,
    val litersPerHour: Double? = null,
    val litersPer100: Double? = null,
    val tripKm: Double = 0.0,
    val tripLiters: Double = 0.0,
    val altitudeM: Double? = null,
    val grade: Double = 0.0,
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
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion.asStateFlow()

    var displacementL = 1.6
    private var job: Job? = null

    fun connect(source: ObdSource) {
        disconnect()
        job = scope.launch { run(source) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        history.save()
        _historyVersion.update { it + 1 }
        _state.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
    }

    private suspend fun run(source: ObdSource) {
        _state.value = LiveState(status = ConnectionStatus.CONNECTING, sourceLabel = source.label)
        val altitude: AltitudeSource
        val elm: Elm327
        when (source) {
            is ObdSource.Simulator -> SimulatedElm327().let { altitude = it; elm = Elm327(it) }
            is ObdSource.Bluetooth -> {
                altitude = DeviceAltitudeSource(appContext)
                elm = Elm327(BluetoothTransport(source.device))
            }
        }
        try {
            elm.initialize()
            val fuelType = if (elm.supports(Pids.FUEL_TYPE)) {
                elm.query(Pids.FUEL_TYPE)?.firstOrNull()?.let(FuelType::fromPid51) ?: FuelType.GASOLINE
            } else FuelType.GASOLINE
            val method = FuelCalculator.chooseMethod(elm::supports, fuelType)
            _state.update { it.copy(status = ConnectionStatus.CONNECTED, method = method, fuelType = fuelType) }
            altitude.start()
            poll(elm, altitude, method, fuelType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = ConnectionStatus.ERROR, error = e.message ?: e.javaClass.simpleName) }
        } finally {
            altitude.stop()
            elm.close()
            history.save()
        }
    }

    private suspend fun poll(elm: Elm327, altitude: AltitudeSource, method: FuelMethod, fuelType: FuelType) {
        val scorer = EcoScorer(fuel = fuelType)
        val grade = GradeEstimator()
        var lastTime = SystemClock.elapsedRealtime()
        var lastSpeed = 0.0
        var accel = 0.0
        var distanceM = 0.0
        var lastSave = lastTime

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
                        FuelCalculator.fromMaf(FuelCalculator.mafFromSpeedDensity(rpm, map, iat, displacementL), fuelType)
                    } else null
                }
                FuelMethod.NONE -> null
            }

            val now = SystemClock.elapsedRealtime()
            val dt = ((now - lastTime) / 1000.0).coerceIn(0.0, 5.0)
            lastTime = now
            val stepM = speed / 3.6 * dt
            distanceM += stepM
            if (dt > 0) accel += ((speed - lastSpeed) / 3.6 / dt - accel) * 0.25
            lastSpeed = speed
            val alt = altitude.altitudeM
            val g = grade.update(distanceM, alt)
            val eco = scorer.evaluate(speed, accel, g, rpm, lh)
            val fuelStep = (lh ?: 0.0) * dt / 3600.0
            history.add(LocalDate.now(), fuelStep, stepM / 1000.0, if (speed >= 3) eco.score else null)

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
                    eco = eco,
                    samples = (s.samples + Sample(now, speed, lh, alt)).takeLast(MAX_SAMPLES),
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
        const val MAX_SAMPLES = 300
        const val POLL_INTERVAL_MS = 250L
    }
}
