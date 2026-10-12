package tech.dm.conso.eco

import tech.dm.conso.obd.FuelType
import kotlin.math.abs

enum class EcoHint(val text: String) {
    GOOD("Conduite souple, continuez"),
    UPHILL("Montée : surconsommation normale"),
    DOWNHILL_COAST("Descente : levez le pied pour couper l'injection"),
    HARSH_ACCEL("Accélération trop vive"),
    HARSH_BRAKE("Freinage brusque : anticipez"),
    HIGH_RPM("Régime élevé : passez le rapport supérieur"),
    OVER_CONSUMPTION("Consommation élevée pour la pente"),
    IDLE("À l'arrêt")
}

data class EcoResult(val score: Double, val instantScore: Double, val hint: EcoHint, val expectedLh: Double)

class EcoScorer(private val model: VehicleModel = VehicleModel(), private val fuel: FuelType = FuelType.GASOLINE) {
    private var smoothed = 80.0

    fun evaluate(speedKmh: Double, accelMs2: Double, grade: Double, rpm: Double?, actualLh: Double?): EcoResult {
        val v = speedKmh / 3.6
        val expectedLh = model.fuelLh(model.tractionPowerW(v, grade, 0.0), fuel)
        if (speedKmh < 3.0) return EcoResult(smoothed, smoothed, EcoHint.IDLE, expectedLh)

        var score = 100.0
        val hints = mutableListOf<Pair<Double, EcoHint>>()

        if (actualLh != null) {
            if (grade < -0.03 && actualLh > model.idleLh * 1.5) {
                val p = ((actualLh / (model.idleLh * 1.5) - 1) * 10.0).coerceIn(10.0, 30.0)
                score -= p
                hints += p to EcoHint.DOWNHILL_COAST
            } else {
                val excess = ((actualLh / expectedLh - 1.15).coerceAtLeast(0.0) * 40.0).coerceAtMost(40.0)
                score -= excess
                if (excess > 10) hints += excess to EcoHint.OVER_CONSUMPTION
            }
        }
        if (accelMs2 > 1.5) {
            val p = ((accelMs2 - 1.5) * 25.0).coerceAtMost(35.0)
            score -= p
            hints += p to EcoHint.HARSH_ACCEL
        }
        if (accelMs2 < -2.5) {
            val p = ((abs(accelMs2) - 2.5) * 15.0).coerceAtMost(25.0)
            score -= p
            hints += p to EcoHint.HARSH_BRAKE
        }
        if (rpm != null && rpm > 3000) {
            val p = ((rpm - 3000) / 40.0).coerceAtMost(25.0)
            score -= p
            hints += p to EcoHint.HIGH_RPM
        }

        val instant = score.coerceIn(0.0, 100.0)
        smoothed += (instant - smoothed) * 0.05
        val hint = hints.maxByOrNull { it.first }?.second
            ?: if (grade > 0.03) EcoHint.UPHILL else EcoHint.GOOD
        return EcoResult(smoothed, instant, hint, expectedLh)
    }
}
