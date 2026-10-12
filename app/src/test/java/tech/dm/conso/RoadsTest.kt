package tech.dm.conso

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.RoadName
import tech.dm.conso.data.Segment
import tech.dm.conso.data.groupByRoad

class RoadsTest {
    private fun seg(cell: String, km: Double, fuel: Double, expected: Double) =
        Segment("$cell-1", 1L, 0L, listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)), km, fuel, expected, 0.0, 50.0)

    @Test
    fun averagesPerRoadAndRanksByWastedFuel() {
        val d1090 = RoadName("D1090", "Grenoble")
        val avenue = RoadName("Avenue Jean Jaurès", "Grenoble")
        val names = mapOf("a" to d1090, "b" to d1090, "c" to avenue)
        val roads = groupByRoad(
            listOf(seg("a", 0.2, 0.020, 0.012), seg("b", 0.2, 0.012, 0.012), seg("c", 0.4, 0.024, 0.024), seg("z", 1.0, 1.0, 0.0)),
            names,
        )
        assertEquals(listOf(d1090, avenue), roads.map { it.road })
        assertEquals(8.0, roads[0].l100, 0.001)
        assertEquals(2.0, roads[0].surplusL100, 0.001)
        assertEquals(0.0, roads[1].surplusL100, 0.001)
    }
}
