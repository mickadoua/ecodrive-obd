package tech.dm.conso.eco

import tech.dm.conso.obd.FuelType
import kotlin.math.max

data class VehicleModel(
    val massKg: Double = 1300.0,
    val rollingResistance: Double = 0.012,
    val dragArea: Double = 0.65,
    val idleLh: Double = 0.6,
) {
    fun tractionPowerW(speedMs: Double, grade: Double, accelMs2: Double): Double {
        val force = massKg * G * (rollingResistance + grade) +
            0.5 * AIR_DENSITY * dragArea * speedMs * speedMs +
            massKg * accelMs2
        return force * speedMs
    }

    fun fuelLh(powerW: Double, fuel: FuelType): Double {
        val efficiency = if (fuel == FuelType.DIESEL) 0.30 else 0.25
        val energyPerLiter = if (fuel == FuelType.DIESEL) 36e6 else 32e6
        return idleLh + max(0.0, powerW) / (efficiency * energyPerLiter) * 3600.0
    }

    companion object {
        const val G = 9.81
        const val AIR_DENSITY = 1.2
    }
}
