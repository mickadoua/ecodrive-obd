package tech.dm.conso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.dm.conso.data.Geo
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.TerrainElevation
import tech.dm.conso.data.TerrainGrade

class TerrainTest {
    private val start = GeoPoint(45.0, 5.0)

    private fun slopeTerrain(grade: Double) = TerrainElevation { pts ->
        pts.map { 200.0 + Geo.distanceM(start, it) * grade }
    }

    @Test
    fun gradeFromTerrainModel() {
        val terrain = slopeTerrain(0.05)
        val estimator = TerrainGrade(terrain)
        var result: Pair<Double?, Double> = null to 0.0
        for (i in 0..40) {
            result = estimator.update(i * 10.0, Geo.offset(start, i * 10.0, 90.0))
            terrain.resolvePending()
        }
        result = estimator.update(410.0, Geo.offset(start, 410.0, 90.0))
        assertEquals(0.05, result.second, 0.01)
    }

    @Test
    fun noAltitudeUntilResolved() {
        val estimator = TerrainGrade(slopeTerrain(0.0))
        val (alt, grade) = estimator.update(0.0, start)
        assertNull(alt)
        assertEquals(0.0, grade, 0.0)
    }

    @Test
    fun failedFetchKeepsPointsPending() {
        var fail = true
        val terrain = TerrainElevation { pts -> if (fail) null else pts.map { 100.0 } }
        terrain.request(start)
        assertEquals(false, terrain.resolvePending())
        fail = false
        assertEquals(true, terrain.resolvePending())
        assertEquals(100.0, terrain.elevation(start)!!, 0.0)
    }
}
