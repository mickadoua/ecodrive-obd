package tech.dm.conso.data

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

data class Vehicle(
    val id: String,
    val name: String,
    val vinHash: String? = null,
    val obdAddress: String? = null,
    val obdName: String? = null,
    val carAddress: String? = null,
    val carName: String? = null,
    val simulator: Boolean = false,
)

enum class IdentifiedBy { VIN, MAC }

sealed interface Identification {
    data class Known(val vehicle: Vehicle, val by: IdentifiedBy) : Identification
    data object Unknown : Identification
    data object Manual : Identification
}

object VehicleIdentifier {
    fun vinHash(vin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + vin.uppercase()).toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    fun identify(vehicles: List<Vehicle>, vinHash: String?, obdAddress: String?): Identification {
        val cars = vehicles.filterNot { it.simulator }
        if (vinHash != null) cars.firstOrNull { it.vinHash == vinHash }?.let { return Identification.Known(it, IdentifiedBy.VIN) }
        if (obdAddress != null) {
            cars.firstOrNull { it.obdAddress == obdAddress && (vinHash == null || it.vinHash == null) }
                ?.let { return Identification.Known(it, IdentifiedBy.MAC) }
        }
        return if (vinHash == null && obdAddress == null) Identification.Manual else Identification.Unknown
    }
}

class VehicleRegistry(private val root: File) {
    private val dir = File(root, "vehicles")
    private val activeFile = File(dir, "active")

    val salt: String by lazy {
        val f = File(dir, "salt")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { dir.mkdirs(); f.writeText(it) }
    }

    fun dir(id: String) = File(dir, id)

    fun all(): List<Vehicle> =
        dir.listFiles().orEmpty().filter { File(it, FILE).exists() }.map { read(it) }.sortedBy { it.id }

    fun active(): Vehicle {
        val list = all()
        val id = activeFile.takeIf { it.exists() }?.readText()?.trim()
        return list.firstOrNull { it.id == id } ?: list.firstOrNull { !it.simulator } ?: create(DEFAULT_NAME)
    }

    fun setActive(id: String) {
        dir.mkdirs()
        activeFile.writeText(id)
    }

    fun create(name: String): Vehicle {
        var id = System.currentTimeMillis().toString(36)
        while (dir(id).exists()) id += "x"
        return Vehicle(id, name).also(::save)
    }

    fun simulator(): Vehicle =
        all().firstOrNull { it.simulator } ?: Vehicle(SIMULATOR_ID, "Simulateur", simulator = true).also(::save)

    fun byCar(address: String): Vehicle? = all().firstOrNull { it.carAddress == address }

    fun attach(vehicle: Vehicle, vinHash: String?, obdAddress: String?, obdName: String?): Vehicle {
        if (obdAddress != null) {
            all().filter { it.id != vehicle.id && it.obdAddress == obdAddress }
                .forEach { save(it.copy(obdAddress = null, obdName = null)) }
        }
        return vehicle.copy(
            vinHash = vinHash ?: vehicle.vinHash,
            obdAddress = obdAddress ?: vehicle.obdAddress,
            obdName = obdName ?: vehicle.obdName,
        ).also(::save)
    }

    fun migrateLegacy(name: String): Vehicle? {
        if (dir.exists()) return null
        val v = create(name)
        LEGACY_FILES.map { File(root, it) }.filter { it.exists() }.forEach { it.renameTo(File(dir(v.id), it.name)) }
        setActive(v.id)
        return v
    }

    fun save(v: Vehicle) {
        val props = Properties()
        props["id"] = v.id
        props["name"] = v.name
        props["simulator"] = v.simulator.toString()
        v.vinHash?.let { props["vinHash"] = it }
        v.obdAddress?.let { props["obd"] = it }
        v.obdName?.let { props["obdName"] = it }
        v.carAddress?.let { props["car"] = it }
        v.carName?.let { props["carName"] = it }
        val d = dir(v.id).apply { mkdirs() }
        File(d, FILE).outputStream().use { props.store(it, null) }
    }

    private fun read(d: File): Vehicle {
        val p = Properties().apply { File(d, FILE).inputStream().use(::load) }
        return Vehicle(
            id = d.name,
            name = p.getProperty("name", DEFAULT_NAME),
            vinHash = p.getProperty("vinHash"),
            obdAddress = p.getProperty("obd"),
            obdName = p.getProperty("obdName"),
            carAddress = p.getProperty("car"),
            carName = p.getProperty("carName"),
            simulator = p.getProperty("simulator") == "true",
        )
    }

    companion object {
        const val FILE = "vehicle.properties"
        const val SIMULATOR_ID = "simulateur"
        const val DEFAULT_NAME = "Ma voiture"
        val LEGACY_FILES = listOf("history.json", "segments.jsonl", "trip.json", "trips.jsonl")
    }
}
