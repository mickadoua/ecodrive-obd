package tech.dm.conso

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import tech.dm.conso.live.LiveReading
import tech.dm.conso.live.LiveSession
import tech.dm.conso.obd.SimulatedElm327
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveSessionTest {
    @Test
    fun simulatorFeedsReadings() = runTest {
        var now = 0L
        val readings = mutableListOf<LiveReading>()
        val session = LiveSession(SimulatedElm327 { now }, scope = this)
        session.start({ now += 500; readings += it }, { error(it) })
        advanceTimeBy(30_000)
        session.stop()
        assertTrue(readings.size > 50)
        assertTrue(readings.last().speedKmh > 0)
        assertTrue(readings.last().litersPerHour!! > 0)
        assertTrue(readings.last().ecoScore in 0.0..100.0)
    }
}
