package tech.dm.conso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.dm.conso.data.IdentifiedBy
import tech.dm.conso.data.Identification
import tech.dm.conso.data.Vehicle
import tech.dm.conso.data.VehicleIdentifier
import tech.dm.conso.data.VehicleRegistry
import java.io.File

class VehiclesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val vin = "VF1RFB00X12345678"
    private val clio = Vehicle("a", "Clio", vinHash = "h-clio", obdAddress = "AA:AA")
    private val golf = Vehicle("b", "Golf", obdAddress = "BB:BB")
    private val sim = Vehicle("simulateur", "Simulateur", vinHash = "h-sim", obdAddress = "CC:CC", simulator = true)
    private val all = listOf(clio, golf, sim)

    @Test
    fun vinHashIsTruncatedAndDoesNotLeakVin() {
        val h = VehicleIdentifier.vinHash(vin, "salt")
        assertEquals(16, h.length)
        assertEquals(h, VehicleIdentifier.vinHash(vin.lowercase(), "salt"))
        assertNotEquals(h, VehicleIdentifier.vinHash(vin, "other"))
        assertFalse(h.contains("12345678"))
    }

    @Test
    fun vinWinsOverMac() {
        assertEquals(Identification.Known(clio, IdentifiedBy.VIN), VehicleIdentifier.identify(all, "h-clio", "BB:BB"))
    }

    @Test
    fun macUsedWhenNoVin() {
        assertEquals(Identification.Known(golf, IdentifiedBy.MAC), VehicleIdentifier.identify(all, null, "BB:BB"))
    }

    @Test
    fun macLearnsVinOnVehicleWithoutOne() {
        assertEquals(Identification.Known(golf, IdentifiedBy.MAC), VehicleIdentifier.identify(all, "h-new", "BB:BB"))
    }

    @Test
    fun moduleMovedToAnotherCarIsUnknown() {
        assertEquals(Identification.Unknown, VehicleIdentifier.identify(all, "h-new", "AA:AA"))
    }

    @Test
    fun unknownMacIsUnknown() {
        assertEquals(Identification.Unknown, VehicleIdentifier.identify(all, null, "DD:DD"))
    }

    @Test
    fun noIdentifierFallsBackToManual() {
        assertEquals(Identification.Manual, VehicleIdentifier.identify(all, null, null))
    }

    @Test
    fun simulatorNeverMatchesARealConnection() {
        assertEquals(Identification.Unknown, VehicleIdentifier.identify(all, "h-sim", "CC:CC"))
    }

    @Test
    fun attachMovesModuleAndKeepsVin() {
        val registry = VehicleRegistry(tmp.root)
        val a = registry.attach(registry.create("A"), "h-a", "AA:AA", "OBDII")
        val b = registry.attach(registry.create("B"), null, "AA:AA", "OBDII")
        assertEquals("h-a", registry.all().first { it.id == a.id }.vinHash)
        assertNull(registry.all().first { it.id == a.id }.obdAddress)
        assertEquals("AA:AA", registry.all().first { it.id == b.id }.obdAddress)
    }

    @Test
    fun migrationMovesLegacyFilesIntoFirstVehicle() {
        val root = tmp.root
        VehicleRegistry.LEGACY_FILES.forEach { File(root, it).writeText("data-$it") }
        File(root, "roads.json").writeText("roads")

        val v = VehicleRegistry(root).migrateLegacy("Clio")!!

        val registry = VehicleRegistry(root)
        assertEquals(listOf(v), registry.all())
        assertEquals(v, registry.active())
        VehicleRegistry.LEGACY_FILES.forEach {
            assertFalse(File(root, it).exists())
            assertEquals("data-$it", File(registry.dir(v.id), it).readText())
        }
        assertEquals("roads", File(root, "roads.json").readText())
        assertNull(registry.migrateLegacy("Clio"))
        assertEquals(1, registry.all().size)
    }

    @Test
    fun freshInstallGetsOneEmptyVehicle() {
        val v = VehicleRegistry(tmp.root).migrateLegacy(VehicleRegistry.DEFAULT_NAME)!!
        assertTrue(VehicleRegistry(tmp.root).dir(v.id).listFiles()!!.all { it.name == VehicleRegistry.FILE })
    }

    @Test
    fun simulatorHasItsOwnFolder() {
        val registry = VehicleRegistry(tmp.root)
        val car = registry.migrateLegacy("Clio")!!
        val simulator = registry.simulator()
        assertNotEquals(registry.dir(car.id), registry.dir(simulator.id))
        assertEquals(simulator, registry.simulator())
        assertEquals(car, registry.active())
    }
}
