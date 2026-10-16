package tech.dm.conso.eco

import tech.dm.conso.obd.FuelType
import kotlin.math.abs

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
    IDLE("À l'arrêt")
}

data class GearAdvice(val currentGear: Int, val targetGear: Int)

data class EcoResult(
    val score: Double,
    val instantScore: Double,
    val hint: EcoHint,
    val expectedLh: Double,
    val message: String = hint.text,
)

class EcoScorer(private val model: VehicleModel = VehicleModel(), private val fuel: FuelType = FuelType.GASOLINE) {
    private var smoothed = 80.0

    fun evaluate(
        speedKmh: Double,
        accelMs2: Double,
        grade: Double,
        rpm: Double?,
        actualLh: Double?,
        speedLimitKmh: Int? = null,
        gear: GearAdvice? = null,
    ): EcoResult {
        val v = speedKmh / 3.6
        val belowLimit = speedLimitKmh == null || speedKmh <= speedLimitKmh + LIMIT_MARGIN_KMH
        val legitAccel = if (belowLimit) accelMs2.coerceIn(0.0, SMOOTH_ACCEL) else 0.0
        val expectedLh = model.fuelLh(model.tractionPowerW(v, grade, legitAccel), fuel)
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
                if (excess > 10) hints += Triple(excess, EcoHint.OVER_CONSUMPTION, null)
            }
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
        return EcoResult(smoothed, instant, hint, expectedLh, top?.third ?: hint.text)
    }

    companion object {
        const val SMOOTH_ACCEL = 1.2
        const val HARSH_ACCEL = 1.5
        const val LIMIT_MARGIN_KMH = 3

        fun ordinal(gear: Int) = if (gear == 1) "1re" else "${gear}e"
    }
}
