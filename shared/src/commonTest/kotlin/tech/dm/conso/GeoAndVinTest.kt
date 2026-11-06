package tech.dm.conso

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import tech.dm.conso.data.Geo
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.Segment
import tech.dm.conso.data.SegmentBuilder
import tech.dm.conso.obd.Vin

class GeoAndVinTest {
    @Test
    fun geohashMatchesReference() {
        assertEquals("u4pruyd", Geo.geohash(GeoPoint(57.64911, 10.40744), 7))
    }

    @Test
    fun parsesCanMultiFrameVin() {
        val raw = "014\r0: 49 02 01 57 50 30\r1: 5A 5A 5A 39 39 5A 54\r2: 53 33 39 32 31 32 34\r\r"
        assertEquals("WP0ZZZ99ZTS392124", Vin.parse(raw))
    }

    @Test
    fun parsesLegacyMultiLineVin() {
        val raw = "49 02 01 00 00 00 31\r49 02 02 44 34 47 50\r49 02 03 30 30 52 35\r49 02 04 35 42 31 32\r49 02 05 33 34 35 36\r"
        assertEquals("1D4GP00R55B123456", Vin.parse(raw))
    }

    @Test
    fun vinRoundTripThroughSimulatorFraming() {
        val vin = "UU1SDENL5K6012345"
        assertEquals(vin, Vin.parse(Vin.encodeForSimulator(vin)))
        val info = Vin.decode(vin)
        assertEquals("Dacia", info.make)
        assertEquals(2019, info.modelYear)
    }

    @Test
    fun noVinWhenUnsupported() {
        assertNull(Vin.parse("NO DATA\r\r"))
    }

    @Test
    fun builderSplitsByGeohashCellWithStableKeys() {
        fun drive(): List<Segment> {
            val out = mutableListOf<Segment>()
            val b = SegmentBuilder(1L) { out += it }
            var p = GeoPoint(45.1885, 5.7245)
            repeat(200) { i ->
                b.add(i * 1000L, p, 0.015, 0.0012, 0.0010, 0.02)
                p = Geo.offset(p, 15.0, 45.0)
            }
            b.flush()
            return out
        }
        val first = drive()
        val second = drive()
        assertTrue(first.size > 5)
        assertEquals(first.map { it.key }, second.map { it.key })
        assertTrue(first.all { it.key.endsWith("-1") })
        assertEquals(1.2, first[1].excessRatio, 0.01)
    }
}
