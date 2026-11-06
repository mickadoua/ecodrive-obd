package tech.dm.conso.eco

import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.Powertrain
import kotlin.math.max
import kotlin.math.min

data class VehicleModel(
    val massKg: Double = 1300.0,
    val rollingResistance: Double = 0.012,
    val dragArea: Double = 0.65,
    val powertrain: Powertrain = Powertrain.ICE,
    val idleLh: Double = idleFor(powertrain),
    val driveEfficiency: Double = 0.88,
    val regenEfficiency: Double = 0.6,
    val regenCapW: Double = 50_000.0,
    val auxW: Double = 400.0,
) {
    fun tractionPowerW(speedMs: Double, grade: Double, accelMs2: Double): Double {
        val force = massKg * G * (rollingResistance + grade) +
            0.5 * AIR_DENSITY * dragArea * speedMs * speedMs +
            massKg * accelMs2
        return force * speedMs
    }

    fun fuelLh(powerW: Double, fuel: FuelType): Double {
        if (powertrain == Powertrain.ELECTRIC) return electricKw(powerW)
        val efficiency = when {
            fuel == FuelType.DIESEL -> 0.30
            powertrain == Powertrain.HYBRID -> 0.32
            else -> 0.25
        }
        val energyPerLiter = if (fuel == FuelType.DIESEL) 36e6 else 32e6
        return idleLh + max(0.0, powerW) / (efficiency * energyPerLiter) * 3600.0
    }

    fun electricKw(powerW: Double): Double {
        val recovered = min(regenCapW, max(0.0, -powerW)) * regenEfficiency
        return (max(0.0, powerW) / driveEfficiency + auxW - recovered) / 1000.0
    }

    companion object {
        const val G = 9.81
        const val AIR_DENSITY = 1.2

        fun idleFor(powertrain: Powertrain) = when (powertrain) {
            Powertrain.ICE -> 0.6
            Powertrain.HYBRID -> 0.2
            Powertrain.ELECTRIC -> 0.4
        }

        fun extraMassKg(powertrain: Powertrain) = when (powertrain) {
            Powertrain.ICE -> 0.0
            Powertrain.HYBRID -> 100.0
            Powertrain.ELECTRIC -> 300.0
        }
    }
}
