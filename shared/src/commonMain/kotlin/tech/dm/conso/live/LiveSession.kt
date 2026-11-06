package tech.dm.conso.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tech.dm.conso.data.GradeEstimator
import tech.dm.conso.data.PositionSource
import tech.dm.conso.eco.EcoScorer
import tech.dm.conso.eco.VehicleModel
import tech.dm.conso.monotonicMs
import tech.dm.conso.obd.Elm327
import tech.dm.conso.obd.FuelCalculator
import tech.dm.conso.obd.FuelMethod
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.ObdTransport
import tech.dm.conso.obd.Pids
import kotlin.coroutines.cancellation.CancellationException

data class LiveReading(
    val speedKmh: Double,
    val rpm: Double?,
    val litersPerHour: Double?,
    val litersPer100: Double?,
    val grade: Double,
    val ecoScore: Double,
    val instantScore: Double,
    val hint: String,
    val method: String,
    val tripKm: Double,
    val tripLiters: Double,
)

class LiveSession(
    private val transport: ObdTransport,
    private val fuel: FuelType = FuelType.GASOLINE,
    private val displacementL: Double = 1.6,
    private val model: VehicleModel = VehicleModel(),
    private val scope: CoroutineScope = MainScope(),
) {
    private var job: Job? = null

    fun start(onReading: (LiveReading) -> Unit, onError: (String) -> Unit) {
        job?.cancel()
        job = scope.launch {
            val elm = Elm327(transport)
            try {
                elm.initialize()
                poll(elm, onReading)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Erreur OBD")
            } finally {
                elm.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun CoroutineScope.poll(elm: Elm327, onReading: (LiveReading) -> Unit) {
        val method = FuelCalculator.chooseMethod(elm::supports, fuel, model.powertrain)
        val scorer = EcoScorer(model, fuel)
        val grades = GradeEstimator()
        val position = transport as? PositionSource
        var last = monotonicMs()
        var lastSpeed = 0.0
        var accel = 0.0
        var distanceM = 0.0
        var tripLiters = 0.0
        while (isActive) {
            val speed = elm.query(Pids.SPEED)?.let(Pids::speedKmh) ?: 0.0
            val rpm = elm.query(Pids.RPM)?.let(Pids::rpm)
            val lh = when (method) {
                FuelMethod.FUEL_RATE -> elm.query(Pids.FUEL_RATE)?.let(Pids::fuelRateLh)
                FuelMethod.MAF -> elm.query(Pids.MAF)?.let { FuelCalculator.fromMaf(Pids.mafGs(it), fuel) }
                FuelMethod.SPEED_DENSITY -> {
                    val map = elm.query(Pids.MAP)?.let(Pids::mapKpa)
                    val iat = elm.query(Pids.INTAKE_TEMP)?.let(Pids::intakeTempC)
                    if (map != null && iat != null && rpm != null) {
                        FuelCalculator.fromMaf(FuelCalculator.mafFromSpeedDensity(rpm, map, iat, displacementL), fuel)
                    } else null
                }
                FuelMethod.ELECTRIC_MODEL, FuelMethod.NONE -> null
            }
            val now = monotonicMs()
            val dt = ((now - last) / 1000.0).coerceIn(0.0, 5.0)
            last = now
            distanceM += speed / 3.6 * dt
            if (dt > 0) accel += ((speed - lastSpeed) / 3.6 / dt - accel) * 0.25
            lastSpeed = speed
            tripLiters += (lh ?: 0.0) * dt / 3600.0
            val grade = grades.update(distanceM, position?.altitudeM)
            val eco = scorer.evaluate(speed, accel, grade, rpm, lh, dtS = dt)
            onReading(
                LiveReading(
                    speed, rpm, lh, lh?.let { FuelCalculator.per100km(it, speed) }, grade,
                    eco.score, eco.instantScore, eco.message, method.label, distanceM / 1000.0, tripLiters,
                )
            )
            delay(POLL_MS)
        }
    }

    companion object {
        private const val POLL_MS = 250L
    }
}
