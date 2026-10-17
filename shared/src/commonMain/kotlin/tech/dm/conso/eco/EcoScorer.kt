package tech.dm.conso.eco

import tech.dm.conso.data.Ahead
import tech.dm.conso.data.AheadKind
import tech.dm.conso.fixed
import tech.dm.conso.obd.FuelType
import kotlin.math.abs
import kotlin.math.roundToInt

enum class EcoHint(val text: String) {
    GOOD("Conduite souple, continuez"),
    UPHILL("Montée : surconsommation normale"),
    ACCELERATING("Accélération souple : normal de consommer plus"),
    DOWNHILL_COAST("Descente : levez le pied pour couper l'injection"),
    HARSH_ACCEL("Accélération trop vive"),
    HARSH_BRAKE("Freinage brusque : anticipez"),
    HIGH_RPM("Régime élevé : passez le rapport supérieur"),
    LOW_RPM("Régime trop bas : rétrogradez"),
    OVER_SPEED("Au-dessus de la limitation"),
    OVER_CONSUMPTION("Consommation élevée pour la pente"),
    ANTICIPATE("Levez le pied : ralentissement à venir"),
    WASTED_ACCEL("Accélération suivie d'un freinage : anticipez"),
    IDLE("À l'arrêt")
}

data class GearAdvice(val currentGear: Int, val targetGear: Int)

data class EcoResult(
    val score: Double,
    val instantScore: Double,
    val hint: EcoHint,
    val expectedLh: Double,
    val message: String = hint.text,
    val lostL: Double = 0.0,
)

class EcoScorer(private val model: VehicleModel = VehicleModel(), private val fuel: FuelType = FuelType.GASOLINE) {
    private var smoothed = 80.0
    private var phaseStartKmh = 0.0
    private var phasePeakKmh = 0.0
    private var phaseAccelS = 0.0
    private var sinceAccelS = Double.MAX_VALUE
    private var wastedFlagged = false

    fun evaluate(
        speedKmh: Double,
        accelMs2: Double,
        grade: Double,
        rpm: Double?,
        actualLh: Double?,
        speedLimitKmh: Int? = null,
        gear: GearAdvice? = null,
        ahead: List<Ahead> = emptyList(),
        dtS: Double = 0.25,
    ): EcoResult {
        val v = speedKmh / 3.6
        val raised = ahead.firstOrNull {
            it.kind == AheadKind.LIMIT && it.distanceM <= RAISE_HORIZON_M && speedLimitKmh != null && it.kmh > speedLimitKmh
        }
        val effectiveLimit = raised?.kmh ?: speedLimitKmh
        val slowdown = if (accelMs2 > LIFT_ACCEL) {
            ahead.firstOrNull { it.kmh < speedKmh - 5 && coastDistanceM(v, it.kmh / 3.6) >= it.distanceM }
        } else null
        val belowLimit = effectiveLimit == null || speedKmh <= effectiveLimit + LIMIT_MARGIN_KMH
        val legitAccel = if (belowLimit && slowdown == null) accelMs2.coerceIn(0.0, SMOOTH_ACCEL) else 0.0
        val expectedLh = model.fuelLh(model.tractionPowerW(v, grade, legitAccel), fuel)
        val wasted = trackAccelThenBrake(speedKmh, accelMs2, dtS)
        if (speedKmh < 3.0) return EcoResult(smoothed, smoothed, EcoHint.IDLE, expectedLh)

        var score = 100.0
        val hints = mutableListOf<Triple<Double, EcoHint, String?>>()

        if (actualLh != null) {
            if (grade < -0.03 && actualLh > model.idleLh * 1.5 && accelMs2 <= 0.3) {
                val p = ((actualLh / (model.idleLh * 1.5) - 1) * 10.0).coerceIn(10.0, 30.0)
                score -= p
                hints += Triple(p, EcoHint.DOWNHILL_COAST, null)
            } else {
                val excess = ((actualLh / expectedLh - 1.15).coerceAtLeast(0.0) * 40.0).coerceAtMost(40.0)
                score -= excess
                if (excess > 10 && slowdown == null) hints += Triple(excess, EcoHint.OVER_CONSUMPTION, null)
            }
        }
        if (slowdown != null) {
            val p = (20.0 + accelMs2 * 10.0).coerceAtMost(35.0)
            score -= p
            hints += Triple(p, EcoHint.ANTICIPATE, liftMessage(slowdown))
        }
        if (wasted != null) {
            val p = (phasePeakKmh - phaseStartKmh).coerceIn(15.0, 35.0)
            score -= p
            hints += Triple(p, EcoHint.WASTED_ACCEL, "Accélération perdue : freinage ${sinceAccelS.fixed(0)} s après")
        }
        if (accelMs2 > HARSH_ACCEL) {
            val p = ((accelMs2 - HARSH_ACCEL) * 25.0).coerceAtMost(35.0)
            score -= p
            hints += Triple(p, EcoHint.HARSH_ACCEL, null)
        }
        if (accelMs2 < -2.5) {
            val p = ((abs(accelMs2) - 2.5) * 15.0).coerceAtMost(25.0)
            score -= p
            hints += Triple(p, EcoHint.HARSH_BRAKE, null)
        }
        if (speedLimitKmh != null && speedKmh > speedLimitKmh * 1.1 + 2) {
            val p = ((speedKmh - speedLimitKmh) * 1.0).coerceAtMost(25.0)
            score -= p
            hints += Triple(p, EcoHint.OVER_SPEED, "Limitée à $speedLimitKmh : levez le pied, chaque km/h en trop coûte")
        }
        when {
            gear != null && gear.targetGear > gear.currentGear -> {
                val p = rpm?.let { ((it - 2200) / 40.0).coerceIn(5.0, 25.0) } ?: 10.0
                score -= p
                hints += Triple(p, EcoHint.HIGH_RPM, "Passez la ${ordinal(gear.targetGear)}")
            }
            gear != null && gear.targetGear < gear.currentGear ->
                hints += Triple(4.0, EcoHint.LOW_RPM, "Rétrogradez en ${ordinal(gear.targetGear)}")
            rpm != null && rpm > 3000 -> {
                val p = ((rpm - 3000) / 40.0).coerceAtMost(25.0)
                score -= p
                hints += Triple(p, EcoHint.HIGH_RPM, null)
            }
        }

        val instant = score.coerceIn(0.0, 100.0)
        smoothed += (instant - smoothed) * 0.05
        val top = hints.maxByOrNull { it.first }
        val hint = top?.second ?: when {
            grade > 0.03 -> EcoHint.UPHILL
            accelMs2 > 0.3 && legitAccel > 0 -> EcoHint.ACCELERATING
            else -> EcoHint.GOOD
        }
        return EcoResult(smoothed, instant, hint, expectedLh, top?.third ?: hint.text, wasted ?: 0.0)
    }

    private fun trackAccelThenBrake(speedKmh: Double, accelMs2: Double, dtS: Double): Double? {
        if (accelMs2 > PHASE_ACCEL) {
            if (sinceAccelS > PHASE_GAP_S || phaseAccelS == 0.0) {
                phaseStartKmh = speedKmh
                phasePeakKmh = speedKmh
                phaseAccelS = 0.0
                wastedFlagged = false
            }
            sinceAccelS = 0.0
            phaseAccelS += dtS
            phasePeakKmh = maxOf(phasePeakKmh, speedKmh)
            return null
        }
        sinceAccelS += dtS
        val gained = phasePeakKmh - phaseStartKmh
        val braking = accelMs2 < -BRAKE_ACCEL && phaseAccelS >= MIN_PHASE_S && sinceAccelS <= WASTE_WINDOW_S &&
            gained >= MIN_GAIN_KMH && phasePeakKmh - speedKmh >= gained / 2
        if (braking) {
            if (wastedFlagged) return 0.0
            wastedFlagged = true
            val vPeak = phasePeakKmh / 3.6
            val vStart = phaseStartKmh / 3.6
            val energyJ = 0.5 * model.massKg * (vPeak * vPeak - vStart * vStart)
            return (model.fuelLh(energyJ, fuel) - model.idleLh) / 3600.0
        }
        if (wastedFlagged && accelMs2 > -0.3 || sinceAccelS > WASTE_WINDOW_S) {
            phaseAccelS = 0.0
            wastedFlagged = false
        }
        return null
    }

    private fun liftMessage(a: Ahead): String {
        val m = (a.distanceM / 10).roundToInt() * 10
        return if (a.kind == AheadKind.LIMIT) "Levez le pied, limitation à ${a.kmh} dans $m m"
        else "Levez le pied, ${a.kind.label} dans $m m"
    }

    companion object {
        const val SMOOTH_ACCEL = 1.2
        const val HARSH_ACCEL = 1.5
        const val LIMIT_MARGIN_KMH = 3
        const val RAISE_HORIZON_M = 200.0
        const val LIFT_ACCEL = 0.3
        const val COAST_DECEL = 0.6
        const val PHASE_ACCEL = 0.5
        const val BRAKE_ACCEL = 1.0
        const val MIN_PHASE_S = 2.0
        const val PHASE_GAP_S = 3.0
        const val MIN_GAIN_KMH = 8.0
        const val WASTE_WINDOW_S = 15.0

        fun coastDistanceM(fromMs: Double, toMs: Double) = (fromMs * fromMs - toMs * toMs) / (2 * COAST_DECEL)

        fun lookaheadM(speedKmh: Double): Double {
            val v = speedKmh / 3.6
            return (v * 4 + v * v / (2 * COAST_DECEL)).coerceIn(80.0, 400.0)
        }

        fun ordinal(gear: Int) = if (gear == 1) "1re" else "${gear}e"
    }
}
