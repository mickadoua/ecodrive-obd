package tech.dm.conso.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import tech.dm.conso.auto.AutoStartStore
import tech.dm.conso.eco.EcoResult
import tech.dm.conso.eco.EcoScorer
import tech.dm.conso.eco.GearAdvice
import tech.dm.conso.eco.GearEstimator
import tech.dm.conso.obd.BluetoothTransport
import tech.dm.conso.obd.Elm327
import tech.dm.conso.obd.FuelCalculator
import tech.dm.conso.obd.FuelMethod
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.Pids
import tech.dm.conso.obd.Powertrain
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

data class LiveState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val sourceLabel: String? = null,
    val error: String? = null,
    val retrying: Boolean = false,
    val method: FuelMethod = FuelMethod.NONE,
    val fuelType: FuelType = FuelType.GASOLINE,
    val powertrain: Powertrain = Powertrain.ICE,
    val batteryPct: Double? = null,
    val electricKm: Double = 0.0,
    val detectedVehicle: VinInfo? = null,
    val unknownVehicle: String? = null,
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
    val gear: GearAdvice? = null,
    val speedLimitKmh: Int? = null,
    val tripScore: Double? = null,
    val samples: List<Sample> = emptyList(),
) {
    val tripAvgL100: Double? get() = if (tripKm > 0.2) tripLiters / tripKm * 100.0 else null
    val electricSharePct: Double? get() = if (powertrain == Powertrain.HYBRID && tripKm > 0.2) electricKm / tripKm * 100.0 else null
}

class TelemetryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    val vehicles = VehicleRegistry(appContext.filesDir).also(::migrate)
    private val _vehicle = MutableStateFlow(vehicles.active())
    val vehicle: StateFlow<Vehicle> = _vehicle.asStateFlow()
    private val _vehicleList = MutableStateFlow(vehicles.all())
    val vehicleList: StateFlow<List<Vehicle>> = _vehicleList.asStateFlow()
    private var vehicleChoice: CompletableDeferred<String?>? = null

    var history = DailyHistory(vehicleFile("history.json"))
        private set
    var segments = SegmentStore(vehicleText("segments.jsonl"))
        private set
    val roads = RoadNames(appContext, File(appContext.filesDir, "roads.json"))
    private var tripStore = TripStore(vehicleText("trip.json"))
    private var tripId = 0L
    private var engineStartMs: Long? = null
    private var tripBuilder: SegmentBuilder? = null
    private var tripStartedAt = 0L
    private var tripStats = TripStats()
    private var tripFinalized = false
    private val speedLimits = SpeedLimits()
    var summaries = TripSummaryStore(vehicleText("trips.jsonl"))
        private set
    private val _summariesVersion = MutableStateFlow(0)
    val summariesVersion: StateFlow<Int> = _summariesVersion.asStateFlow()
    var onTripFinished: ((TripSummary) -> Unit)? = null
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion.asStateFlow()

    private var profileStore = VehicleProfileStore(appContext, _vehicle.value.id)
    private val _profile = MutableStateFlow(profileStore.load())
    val profile: StateFlow<VehicleProfile> = _profile.asStateFlow()

    private val terrain = TerrainElevation()
    var autoReconnectTried = false
    private var job: Job? = null

    fun saveProfile(p: VehicleProfile) {
        profileStore.save(p)
        _profile.value = p
    }

    fun selectVehicle(id: String) {
        if (job?.isCompleted == false) return
        vehicles.all().firstOrNull { it.id == id }?.let(::open)
    }

    fun createVehicle(name: String) {
        if (job?.isCompleted == false) return
        open(vehicles.create(name))
    }

    fun updateVehicle(v: Vehicle) {
        vehicles.save(v)
        if (v.id == _vehicle.value.id) _vehicle.value = v
        _vehicleList.value = vehicles.all()
    }

    fun chooseVehicle(id: String?) {
        vehicleChoice?.complete(id)
    }

    private fun vehicleFile(name: String) = File(vehicles.dir(_vehicle.value.id), name)
    private fun vehicleText(name: String) = TextFile(vehicleFile(name).path)

    private fun open(v: Vehicle) {
        if (v.id != _vehicle.value.id) {
            history.save()
            _vehicle.value = v
            history = DailyHistory(vehicleFile("history.json"))
            segments = SegmentStore(vehicleText("segments.jsonl"))
            tripStore = TripStore(vehicleText("trip.json"))
            summaries = TripSummaryStore(vehicleText("trips.jsonl"))
            profileStore = VehicleProfileStore(appContext, v.id)
            _profile.value = profileStore.load()
            _state.update { LiveState(status = it.status, sourceLabel = it.sourceLabel) }
            vehicles.setActive(v.id)
            _historyVersion.update { it + 1 }
            _summariesVersion.update { it + 1 }
        }
        _vehicle.value = v
        _vehicleList.value = vehicles.all()
    }

    private fun migrate(registry: VehicleRegistry) {
        val legacy = appContext.getSharedPreferences("vehicle", Context.MODE_PRIVATE)
        val name = listOfNotNull(legacy.getString("make", null), legacy.getString("model", null))
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { VehicleRegistry.DEFAULT_NAME }
        val v = registry.migrateLegacy(name) ?: return
        val target = appContext.getSharedPreferences("vehicle_${v.id}", Context.MODE_PRIVATE).edit()
        legacy.all.forEach { (k, value) ->
            when (value) {
                is String -> target.putString(k, value)
                is Int -> target.putInt(k, value)
                is Float -> target.putFloat(k, value)
                is Boolean -> target.putBoolean(k, value)
                is Long -> target.putLong(k, value)
            }
        }
        target.commit()
        val (car, carName) = AutoStartStore(appContext).legacyCar()
        val obd = AutoStartStore(appContext).load()
        registry.save(v.copy(carAddress = car, carName = carName, obdAddress = obd.obdAddress, obdName = obd.obdName))
    }

    private suspend fun identify(vin: String?, source: ObdSource.Bluetooth) {
        val hash = vin?.let { VehicleIdentifier.vinHash(it, vehicles.salt) }
        val address = source.device.address
        val found = when (val id = VehicleIdentifier.identify(vehicles.all(), hash, address)) {
            is Identification.Known -> id.vehicle
            Identification.Manual -> _vehicle.value
            Identification.Unknown -> askVehicle(vin?.let(Vin::decode))
        }
        open(vehicles.attach(found, hash, address, source.label))
    }

    private suspend fun askVehicle(info: VinInfo?): Vehicle {
        val suggested = listOfNotNull(info?.make, info?.modelYear?.toString()).joinToString(" ")
            .ifBlank { "Véhicule ${vehicles.all().count { !it.simulator } + 1}" }
        val choice = CompletableDeferred<String?>().also { vehicleChoice = it }
        val id = try {
            _state.update { it.copy(unknownVehicle = suggested) }
            withTimeoutOrNull(VEHICLE_CHOICE_TIMEOUT_MS) { choice.await() }
        } finally {
            vehicleChoice = null
            _state.update { it.copy(unknownVehicle = null) }
        }
        return vehicles.all().firstOrNull { it.id == id && !it.simulator } ?: vehicles.create(suggested)
    }

    fun connect(source: ObdSource, retry: Boolean = false, maxAttempts: Int = MAX_RETRIES) {
        if (retry && job?.isActive == true) return
        val previous = job
        disconnect()
        job = scope.launch {
            previous?.join()
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

    fun endTrip() {
        scope.launch {
            finalizeCurrentTrip()
            disconnect()
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
        tripId = 0L
        _state.value = LiveState(status = ConnectionStatus.CONNECTING, sourceLabel = source.label)
        val position: PositionSource
        val elm: Elm327
        if (source is ObdSource.Simulator) open(vehicles.simulator())
        when (source) {
            is ObdSource.Simulator -> SimulatedElm327(powertrain = _profile.value.powertrain).let { position = it; elm = Elm327(it) }
            is ObdSource.Bluetooth -> {
                position = DevicePositionSource(appContext)
                elm = Elm327(BluetoothTransport(source.device))
            }
        }
        try {
            elm.initialize()
            val vin = elm.readVin()
            if (source is ObdSource.Bluetooth) identify(vin, source)
            val pid51 = if (elm.supports(Pids.FUEL_TYPE)) elm.query(Pids.FUEL_TYPE)?.firstOrNull() else null
            val fuelType = pid51?.let(FuelType::fromPid51) ?: _profile.value.fuel
            val powertrain = Powertrain.resolve(_profile.value.powertrain, pid51)
            if (powertrain != _profile.value.powertrain) saveProfile(_profile.value.copy(powertrain = powertrain))
            val detected = vin?.let(Vin::decode)
            val method = FuelCalculator.chooseMethod(elm::supports, fuelType, powertrain)
            beginOrResumeTrip(readEngineStart(elm))
            _state.update {
                it.copy(status = ConnectionStatus.CONNECTED, method = method, fuelType = fuelType, powertrain = powertrain, detectedVehicle = detected)
            }
            position.start()
            val terrainJob = if (position.hasPreciseAltitude) null else terrain.start(scope)
            try {
                poll(elm, position, method, fuelType, powertrain)
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
            tripBuilder?.flush()
            tripBuilder = null
            saveTrip()
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
        powertrain: Powertrain,
    ) {
        val model = _profile.value.vehicleModel
        val scorer = EcoScorer(model, fuelType)
        val manual = _profile.value.gearAdviceEnabled
        val hasBattery = elm.supports(Pids.HYBRID_BATTERY)
        var lastBatteryAt = 0L
        var lastGrade = 0.0
        val gears = GearEstimator(profileStore.loadGearRatios(), fuelType)
        var engineOffSince: Long? = null
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
        var headingFrom: GeoPoint? = null
        var heading: Double? = null

        while (scope.isActive) {
            val speedData = elm.query(Pids.SPEED)
            val speed = speedData?.let(Pids::speedKmh) ?: 0.0
            val rpm = if (powertrain == Powertrain.ELECTRIC) null else elm.query(Pids.RPM)?.let(Pids::rpm)
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
                FuelMethod.ELECTRIC_MODEL -> model.electricKw(model.tractionPowerW(speed / 3.6, lastGrade, accel))
                FuelMethod.NONE -> null
            }

            val now = SystemClock.elapsedRealtime()
            val battery = if (hasBattery && now - lastBatteryAt >= BATTERY_PERIOD_MS) {
                lastBatteryAt = now
                elm.query(Pids.HYBRID_BATTERY)?.let(Pids::hybridBatteryPct)
            } else null
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
            lastGrade = g
            speedLimits.prefetch(scope, loc, now)
            val limit = speedLimits.limitAt(loc)
            if (loc != null) {
                val from = headingFrom
                if (from == null) headingFrom = loc
                else if (Geo.distanceM(from, loc) >= HEADING_MIN_M) {
                    heading = Geo.bearingDeg(from, loc)
                    headingFrom = loc
                }
            }
            val ahead = speedLimits.ahead(loc, heading, limit, EcoScorer.lookaheadM(speed))
            val gear = if (manual) gears.update(speed, rpm, accel) else null
            val eco = scorer.evaluate(speed, accel, g, rpm, lh.takeIf { method != FuelMethod.ELECTRIC_MODEL }, limit, gear, ahead, dt)
            val fuelStep = (lh ?: 0.0) * dt / 3600.0
            val expectedStep = eco.expectedLh * dt / 3600.0
            history.add(LocalDate.now(), fuelStep, stepM / 1000.0, if (speed >= 3) eco.instantScore else null)

            val engineRunning = if (powertrain == Powertrain.ICE) rpm != null && rpm > 300 else speedData != null
            val electricStep = if (powertrain == Powertrain.HYBRID && speed >= 3 && (rpm ?: 0.0) <= 300) stepM / 1000.0 else 0.0
            if (tripFinalized && engineRunning) beginOrResumeTrip(readEngineStart(elm))
            if (!tripFinalized) {
                if (lh != null) tripBuilder?.add(wall, loc, stepM / 1000.0, fuelStep, expectedStep, g)
                tripStats.add(dt, speed, stepM / 1000.0, fuelStep, expectedStep, eco.hint, eco.instantScore, loc, eco.lostL)
            }
            engineOffSince = if (!engineRunning && speed < 1) engineOffSince ?: now else null
            if (!tripFinalized && engineOffSince != null && now - engineOffSince!! > ENGINE_OFF_MS) finalizeCurrentTrip()

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
                    electricKm = s.electricKm + electricStep,
                    batteryPct = battery ?: s.batteryPct,
                    altitudeM = alt,
                    grade = g,
                    location = loc,
                    altitudeSource = altitudeSource,
                    eco = eco,
                    gear = gear,
                    speedLimitKmh = limit,
                    tripScore = tripStats.score,
                    samples = if (takeSample && !tripFinalized) (s.samples + Sample(wall, speed, lh, alt, g, ecoTrend)).takeLast(MAX_SAMPLES) else s.samples,
                )
            }

            if (now - lastSave > 30_000) {
                val engineStart = readEngineStart(elm)
                if (engineStart != null && engineStartMs != null &&
                    kotlin.math.abs(engineStart - engineStartMs!!) > TripSnapshot.ENGINE_START_TOLERANCE_MS
                ) {
                    saveTrip()
                    beginOrResumeTrip(engineStart)
                }
                saveTrip()
                if (manual) profileStore.saveGearRatios(gears.learnedRatios)
                history.save()
                _historyVersion.update { it + 1 }
                lastSave = now
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun readEngineStart(elm: Elm327): Long? =
        if (elm.supports(Pids.ENGINE_RUNTIME)) {
            elm.query(Pids.ENGINE_RUNTIME)?.let(Pids::engineRuntimeS)
                ?.takeIf { it > 0 }
                ?.let { System.currentTimeMillis() - it * 1000L }
        } else null

    private suspend fun beginOrResumeTrip(engineStart: Long?) {
        tripBuilder?.flush()
        val now = System.currentTimeMillis()
        val stored = tripStore.load()?.takeIf { !it.finalized }
        if (stored != null && !stored.isSameTrip(engineStart, now)) finalizeSnapshot(stored)
        val saved = stored?.takeIf { it.isSameTrip(engineStart, now) }
        tripId = saved?.tripId ?: now
        tripStartedAt = saved?.startedAt ?: now
        tripStats = saved?.stats ?: TripStats()
        tripFinalized = false
        engineStartMs = engineStart ?: saved?.engineStartMs
        tripBuilder = SegmentBuilder(tripId) {
            segments.append(it)
            _historyVersion.update { v -> v + 1 }
        }
        _state.update {
            it.copy(
                tripKm = saved?.tripKm ?: 0.0,
                tripLiters = saved?.tripLiters ?: 0.0,
                samples = saved?.samples.orEmpty(),
            )
        }
    }

    private suspend fun finalizeCurrentTrip() {
        if (tripId == 0L || tripFinalized) return
        tripBuilder?.flush()
        tripFinalized = true
        val s = _state.value
        val snapshot = TripSnapshot(
            tripId, engineStartMs, System.currentTimeMillis(), s.tripKm, s.tripLiters, s.samples,
            tripStartedAt, tripStats, finalized = true,
        )
        runCatching { tripStore.save(snapshot) }
        publishSummary(snapshot)
    }

    private suspend fun finalizeSnapshot(snapshot: TripSnapshot) {
        runCatching { tripStore.save(snapshot.copy(finalized = true)) }
        publishSummary(snapshot)
    }

    private suspend fun publishSummary(snapshot: TripSnapshot) {
        if (snapshot.stats.distanceKm < MIN_TRIP_KM) return
        val summary = TripSummary(
            id = snapshot.tripId,
            startedAt = snapshot.startedAt,
            endedAt = snapshot.lastUpdateMs,
            stats = snapshot.stats,
            startCity = snapshot.stats.start?.let { roads.localityAt(it) },
            endCity = snapshot.stats.end?.let { roads.localityAt(it) },
        )
        summaries.add(summary)
        _summariesVersion.update { it + 1 }
        onTripFinished?.invoke(summary)
    }

    private fun saveTrip() {
        if (tripId == 0L) return
        val s = _state.value
        if (tripFinalized) return
        runCatching {
            tripStore.save(
                TripSnapshot(tripId, engineStartMs, System.currentTimeMillis(), s.tripKm, s.tripLiters, s.samples, tripStartedAt, tripStats)
            )
        }
    }

    companion object {
        const val MAX_SAMPLES = 4 * 3600
        const val SAMPLE_PERIOD_MS = 1_000L
        const val POLL_INTERVAL_MS = 250L
        const val MAX_RETRIES = 20
        const val ENGINE_OFF_MS = 8_000L
        const val MIN_TRIP_KM = 0.3
        const val HEADING_MIN_M = 15.0
        const val RETRY_DELAY_MS = 15_000L
        const val BATTERY_PERIOD_MS = 5_000L
        const val VEHICLE_CHOICE_TIMEOUT_MS = 60_000L
    }
}
