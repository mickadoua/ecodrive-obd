package tech.dm.conso

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.Test
import tech.dm.conso.data.TripStats
import tech.dm.conso.eco.EcoHint
import tech.dm.conso.eco.EcoScorer

class TripScoreTest {
    @Test
    fun tripScoreIsDistanceWeightedNotEma() {
        val stats = TripStats()
        val scorer = EcoScorer()
        var ema = 0.0
        repeat(400) {
            val r = scorer.evaluate(90.0, 0.0, 0.0, 2000.0, 5.0, dtS = 1.0)
            stats.add(1.0, 90.0, 0.025, 0.0, 0.0, EcoHint.GOOD, r.instantScore, null)
        }
        repeat(60) {
            val r = scorer.evaluate(50.0, 2.5, 0.0, 4000.0, 20.0, dtS = 1.0)
            stats.add(1.0, 50.0, 0.014, 0.0, 0.0, r.hint, r.instantScore, null)
            ema = r.score
        }
        val expected = (400 * 0.025 * 100.0 + 60 * 0.014 * scorer.evaluate(50.0, 2.5, 0.0, 4000.0, 20.0, dtS = 1.0).instantScore) / (400 * 0.025 + 60 * 0.014)
        assertEquals(expected, stats.score!!, 0.5)
        assertNotEquals(ema, stats.score!!, 10.0)
    }

    @Test
    fun weightsByDistanceNotTime() {
        val stats = TripStats()
        stats.add(600.0, 10.0, 1.0, 0.0, 0.0, EcoHint.GOOD, 40.0, null)
        stats.add(60.0, 120.0, 2.0, 0.0, 0.0, EcoHint.GOOD, 100.0, null)
        assertEquals(80.0, stats.score!!, 1e-9)
    }

    @Test
    fun noScoreBeforeMinimalDistance() {
        val stats = TripStats()
        stats.add(10.0, 50.0, 0.1, 0.0, 0.0, EcoHint.GOOD, 90.0, null)
        assertNull(stats.score)
    }
}
