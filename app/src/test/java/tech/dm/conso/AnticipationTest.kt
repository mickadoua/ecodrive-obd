package tech.dm.conso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.dm.conso.data.Ahead
import tech.dm.conso.data.AheadKind
import tech.dm.conso.data.Geo
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.LimitWay
import tech.dm.conso.data.RoadArea
import tech.dm.conso.data.RoadFeature
import tech.dm.conso.eco.EcoHint
import tech.dm.conso.eco.EcoScorer
import tech.dm.conso.eco.VehicleModel
import tech.dm.conso.obd.FuelType

class AnticipationTest {
    private val model = VehicleModel()
    private fun accelLh(kmh: Double, accel: Double) = model.fuelLh(model.tractionPowerW(kmh / 3.6, 0.0, accel), FuelType.GASOLINE)

    @Test
    fun acceleratingTowardsHigherLimitIsNotPenalized() {
        val ahead = listOf(Ahead(AheadKind.LIMIT, 80, 100.0))
        val withRaise = EcoScorer().evaluate(55.0, 1.0, 0.0, 2000.0, accelLh(55.0, 1.0), 50, ahead = ahead)
        val blind = EcoScorer().evaluate(55.0, 1.0, 0.0, 2000.0, accelLh(55.0, 1.0), 50)
        assertEquals(100.0, withRaise.instantScore, 0.01)
        assertEquals(EcoHint.ACCELERATING, withRaise.hint)
        assertTrue(blind.instantScore < withRaise.instantScore)
    }

    @Test
    fun acceleratingTowardsLowerLimitIsPenalizedWithMessage() {
        val ahead = listOf(Ahead(AheadKind.LIMIT, 50, 120.0))
        val r = EcoScorer().evaluate(75.0, 1.0, 0.0, 2000.0, accelLh(75.0, 1.0), 80, ahead = ahead)
        assertEquals(EcoHint.ANTICIPATE, r.hint)
        assertEquals("Levez le pied, limitation à 50 dans 120 m", r.message)
        assertTrue(r.instantScore < 80)
    }

    @Test
    fun distantRoundaboutDoesNotPenalizeAcceleration() {
        val ahead = listOf(Ahead(AheadKind.ROUNDABOUT, 30, 390.0))
        val r = EcoScorer().evaluate(50.0, 1.0, 0.0, 2000.0, accelLh(50.0, 1.0), 50, ahead = ahead)
        assertNotEquals(EcoHint.ANTICIPATE, r.hint)
        assertEquals(100.0, r.instantScore, 0.01)
    }

    @Test
    fun accelerationThenBrakingIsPenalizedAndImputed() {
        val scorer = EcoScorer()
        var speed = 30.0
        repeat(24) {
            speed += 1.2 * 3.6 * 0.25
            scorer.evaluate(speed, 1.2, 0.0, 2000.0, accelLh(speed, 1.2), 70, dtS = 0.25)
        }
        repeat(8) { scorer.evaluate(speed, 0.0, 0.0, 2000.0, accelLh(speed, 0.0), 70, dtS = 0.25) }
        val results = (1..20).map {
            speed -= 2.0 * 3.6 * 0.25
            scorer.evaluate(speed, -2.0, 0.0, 2000.0, 0.0, 70, dtS = 0.25)
        }
        val flagged = results.filter { it.hint == EcoHint.WASTED_ACCEL }
        assertTrue(flagged.isNotEmpty())
        assertTrue(flagged.first().message.startsWith("Accélération perdue"))
        assertTrue(flagged.first().instantScore < 85)
        val lost = results.sumOf { it.lostL }
        assertTrue("lost=$lost", lost > 0.005 && lost < 0.1)
    }

    @Test
    fun brakingLongAfterAccelerationIsNotImputed() {
        val scorer = EcoScorer()
        var speed = 30.0
        repeat(24) {
            speed += 1.2 * 3.6 * 0.25
            scorer.evaluate(speed, 1.2, 0.0, 2000.0, accelLh(speed, 1.2), 70, dtS = 0.25)
        }
        repeat(120) { scorer.evaluate(speed, 0.0, 0.0, 2000.0, accelLh(speed, 0.0), 70, dtS = 0.25) }
        val results = (1..20).map {
            speed -= 2.0 * 3.6 * 0.25
            scorer.evaluate(speed, -2.0, 0.0, 2000.0, 0.0, 70, dtS = 0.25)
        }
        assertTrue(results.none { it.hint == EcoHint.WASTED_ACCEL })
        assertEquals(0.0, results.sumOf { it.lostL }, 0.0)
    }

    @Test
    fun roadAreaFindsLimitChangeAndRoundaboutAhead() {
        val start = GeoPoint(45.0, 5.0)
        val north = 0.0
        fun at(m: Double) = Geo.offset(start, m, north)
        val area = RoadArea(
            start,
            ways = listOf(LimitWay(80, listOf(at(-200.0), at(150.0))), LimitWay(50, listOf(at(150.0), at(600.0)))),
            features = listOf(
                RoadFeature(AheadKind.ROUNDABOUT, listOf(at(300.0))),
                RoadFeature(AheadKind.STOP, listOf(Geo.offset(at(100.0), 200.0, 90.0))),
                RoadFeature(AheadKind.SIGNAL, listOf(at(-50.0))),
            ),
        )
        assertEquals(80, area.limitAt(start))
        val ahead = area.ahead(start, north, 80, 400.0)
        assertEquals(listOf(AheadKind.LIMIT, AheadKind.ROUNDABOUT), ahead.map { it.kind })
        assertEquals(50, ahead[0].kmh)
        assertEquals(160.0, ahead[0].distanceM, 20.0)
        assertEquals(300.0, ahead[1].distanceM, 1.0)
    }
}
