package tech.dm.conso

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.Segment
import tech.dm.conso.data.TripSummary
import tech.dm.conso.eco.EcoHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistenceFormatTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun readsSegmentWrittenByOrgJson() {
        val s = Segment.fromJson(obj(LEGACY_SEGMENT))
        assertEquals("u0h1abc-3", s.key)
        assertEquals(listOf(GeoPoint(45.1, 5.7), GeoPoint(45.1005, 5.7008)), s.points)
        assertEquals(48.0, s.avgSpeedKmh)
        assertEquals(1.0E-4, s.avgGrade)
        assertEquals(s, Segment.fromJson(obj(s.toJson().toString())))
    }

    @Test
    fun readsTripSummariesWrittenByOrgJson() {
        val (full, empty) = LEGACY_TRIPS.lines().map { TripSummary.fromJson(obj(it)) }
        assertEquals("Grenoble", full.startCity)
        assertNull(full.endCity)
        assertEquals(GeoPoint(45.1, 5.7), full.stats.start)
        assertNull(full.stats.end)
        assertEquals(0.02, full.stats.wastedL[EcoHint.HARSH_ACCEL])
        assertEquals(12.5, full.stats.hintS[EcoHint.HIGH_RPM])
        assertNull(empty.startCity)
        assertNull(empty.stats.score)

        val again = TripSummary.fromJson(obj(full.toJson().toString()))
        assertEquals(full.copy(stats = again.stats), again)
        assertEquals(full.stats.toJson(), again.stats.toJson())
    }

    companion object {
        const val LEGACY_SEGMENT =
            """{"key":"u0h1abc-3","trip":1700000000000,"t":1700000001000,"d":0.08,"f":0.006,"e":0.005,"g":1.0E-4,"v":48,"p":[45.1,5.7,45.1005,5.7008]}"""
        const val LEGACY_STATS =
            """{"moving":120.5,"idle":30,"km":2.4,"fuel":0.18,"expected":0.15,"idleFuel":0.01,"scoreKm":180,"scoredKm":2.2,"wasted":{"HARSH_ACCEL":0.02},"hintS":{"GOOD":100,"HIGH_RPM":12.5},"start":{"lat":45.1,"lon":5.7},"end":null}"""
        const val LEGACY_TRIPS =
            """{"id":1700000000000,"start":1700000000000,"end":1700000300000,"stats":$LEGACY_STATS,"from":"Grenoble","to":null}
{"id":1700000400000,"start":1700000400000,"end":1700000500000,"stats":{"moving":0,"idle":0,"km":0,"fuel":0,"expected":0,"idleFuel":0,"scoreKm":0,"scoredKm":0,"wasted":{},"hintS":{},"start":null,"end":null},"from":null,"to":null}"""
        const val LEGACY_TRIP =
            """{"id":1700000000000,"engine":null,"t":1700000300000,"km":2.4,"l":0.18,"s":[[1700000001000,50,3.2,210.5,0.01,82],[1700000002000,0,null,null,-0.02,null]],"startedAt":1700000000000,"stats":$LEGACY_STATS,"finalized":true}"""
    }
}
