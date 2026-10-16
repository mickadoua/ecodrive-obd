package tech.dm.conso.data

import android.content.Context
import tech.dm.conso.eco.VehicleModel
import tech.dm.conso.obd.FuelType

enum class CarSegment(val label: String, val massKg: Double, val dragArea: Double) {
    CITY("Citadine", 1100.0, 0.60),
    COMPACT("Compacte", 1300.0, 0.65),
    SEDAN("Berline", 1500.0, 0.66),
    SUV("SUV", 1550.0, 0.85),
    VAN("Monospace / utilitaire", 1700.0, 0.95),
}

data class VehicleProfile(
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val fuel: FuelType = FuelType.GASOLINE,
    val displacementL: Double = 1.6,
    val segment: CarSegment = CarSegment.COMPACT,
    val manualGearbox: Boolean = true,
) {
    val isConfigured get() = make.isNotBlank() && model.isNotBlank()
    val vehicleClass: String get() = "${fuel.name}-${segment.name}-${displacementBucket()}"
    val vehicleModel get() = VehicleModel(massKg = segment.massKg, dragArea = segment.dragArea)
    val label get() = listOf(make, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Véhicule non renseigné" }

    private fun displacementBucket() = when {
        displacementL < 1.2 -> "S"
        displacementL < 1.7 -> "M"
        displacementL < 2.3 -> "L"
        else -> "XL"
    }
}

class VehicleProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("vehicle", Context.MODE_PRIVATE)

    fun load() = VehicleProfile(
        make = prefs.getString("make", "") ?: "",
        model = prefs.getString("model", "") ?: "",
        year = prefs.getInt("year", 0).takeIf { it > 0 },
        fuel = runCatching { FuelType.valueOf(prefs.getString("fuel", "")!!) }.getOrDefault(FuelType.GASOLINE),
        displacementL = prefs.getFloat("displacement", 1.6f).toDouble(),
        segment = runCatching { CarSegment.valueOf(prefs.getString("segment", "")!!) }.getOrDefault(CarSegment.COMPACT),
        manualGearbox = prefs.getBoolean("manual", true),
    )

    fun loadGearRatios(): List<Double> =
        prefs.getString("gearRatios", "").orEmpty().split(',').mapNotNull { it.toDoubleOrNull() }

    fun saveGearRatios(ratios: List<Double>) {
        prefs.edit().putString("gearRatios", ratios.joinToString(",")).apply()
    }

    fun save(p: VehicleProfile) {
        prefs.edit()
            .putString("make", p.make)
            .putString("model", p.model)
            .putInt("year", p.year ?: 0)
            .putString("fuel", p.fuel.name)
            .putFloat("displacement", p.displacementL.toFloat())
            .putString("segment", p.segment.name)
            .putBoolean("manual", p.manualGearbox)
            .apply()
    }
}
