package tech.dm.conso.obd

enum class FuelType(val stoichAfr: Double, val densityGl: Double) {
    GASOLINE(14.7, 745.0),
    DIESEL(14.5, 832.0);

    companion object {
        fun fromPid51(code: Int) = when (code) {
            0x04, 0x13, 0x17 -> DIESEL
            else -> GASOLINE
        }
    }
}

enum class Powertrain(val label: String, val rateUnit: String, val per100Unit: String, val totalUnit: String, val totalLabel: String, val consumptionScale: Double) {
    ICE("Thermique", "L/h", "L/100 km", "L", "Carburant", 1.0),
    HYBRID("Hybride", "L/h", "L/100 km", "L", "Carburant", 1.0),
    ELECTRIC("Électrique", "kW", "kWh/100 km", "kWh", "Énergie", 3.0);

    val per100Short get() = per100Unit.removeSuffix(" km")

    companion object {
        fun fromPid51(code: Int) = when (code) {
            0x08 -> ELECTRIC
            in 0x0F..0x16 -> HYBRID
            else -> ICE
        }

        fun resolve(manual: Powertrain, pid51: Int?): Powertrain {
            val detected = pid51?.let(::fromPid51) ?: return manual
            return if (detected.ordinal > manual.ordinal) detected else manual
        }
    }
}

enum class FuelMethod(val label: String) {
    FUEL_RATE("débit carburant (PID 5E)"),
    MAF("débitmètre d'air (PID 10)"),
    SPEED_DENSITY("pression collecteur (PID 0B)"),
    ELECTRIC_MODEL("estimation par modèle physique"),
    NONE("indisponible")
}

object FuelCalculator {
    private const val AIR_MOLAR_MASS = 28.97
    private const val GAS_CONSTANT = 8.314
    private const val VOLUMETRIC_EFFICIENCY = 0.85

    fun fromMaf(mafGs: Double, fuel: FuelType) = mafGs / fuel.stoichAfr / fuel.densityGl * 3600.0

    fun mafFromSpeedDensity(rpm: Double, mapKpa: Double, intakeTempC: Double, displacementL: Double): Double {
        val imap = rpm * mapKpa / (intakeTempC + 273.15) / 2.0
        return imap / 60.0 * VOLUMETRIC_EFFICIENCY * displacementL * AIR_MOLAR_MASS / GAS_CONSTANT
    }

    fun per100km(litersPerHour: Double, speedKmh: Double): Double? =
        if (speedKmh < 5.0) null else litersPerHour / speedKmh * 100.0

    fun chooseMethod(supports: (Int) -> Boolean, fuel: FuelType, powertrain: Powertrain = Powertrain.ICE): FuelMethod = when {
        powertrain == Powertrain.ELECTRIC -> FuelMethod.ELECTRIC_MODEL
        supports(Pids.FUEL_RATE) -> FuelMethod.FUEL_RATE
        fuel == FuelType.DIESEL -> FuelMethod.NONE
        supports(Pids.MAF) -> FuelMethod.MAF
        supports(Pids.MAP) && supports(Pids.RPM) && supports(Pids.INTAKE_TEMP) -> FuelMethod.SPEED_DENSITY
        else -> FuelMethod.NONE
    }
}
