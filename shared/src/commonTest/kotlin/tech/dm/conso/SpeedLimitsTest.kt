package tech.dm.conso

import tech.dm.conso.data.AheadKind
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.SpeedLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeedLimitsTest {
    @Test
    fun parsesMaxspeedTags() {
        assertEquals(50, SpeedLimits.parseMaxspeed("50"))
        assertEquals(48, SpeedLimits.parseMaxspeed("30 mph"))
        assertEquals(80, SpeedLimits.parseMaxspeed("FR:rural"))
        assertEquals(130, SpeedLimits.parseMaxspeed("FR:motorway"))
        assertNull(SpeedLimits.parseMaxspeed("signals"))
    }

    @Test
    fun parsesOverpassArea() {
        val json = """
            {"version":0.6,"elements":[
              {"type":"way","id":1,"tags":{"highway":"primary","maxspeed":"70"},
               "geometry":[{"lat":45.0,"lon":5.0},{"lat":45.001,"lon":5.0}]},
              {"type":"way","id":2,"tags":{"junction":"roundabout"},"geometry":[{"lat":45.002,"lon":5.0}]},
              {"type":"node","id":3,"lat":45.0005,"lon":5.0,"tags":{"highway":"stop"}},
              {"type":"node","id":4,"lat":45.0,"lon":5.0}
            ]}
        """.trimIndent()
        val area = SpeedLimits.parseArea(GeoPoint(45.0, 5.0), json)
        assertEquals(70, area.limitAt(GeoPoint(45.0005, 5.00001)))
        assertEquals(listOf(AheadKind.ROUNDABOUT, AheadKind.STOP), area.features.map { it.kind })
    }
}
