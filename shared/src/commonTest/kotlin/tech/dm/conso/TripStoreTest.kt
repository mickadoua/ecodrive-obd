package tech.dm.conso

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import tech.dm.conso.data.TripSnapshot

class TripStoreTest {
    private fun snapshot(engine: Long?, last: Long) = TripSnapshot(1L, engine, last, 12.0, 0.9, emptyList())

    @Test
    fun sameEngineStartResumesTrip() {
        assertTrue(snapshot(1_000_000, 0).isSameTrip(1_030_000, 99_999_999))
        assertFalse(snapshot(1_000_000, 0).isSameTrip(1_000_000 + 600_000, 0))
    }

    @Test
    fun withoutEngineRuntimeFallsBackToFiveMinutes() {
        assertTrue(snapshot(null, 0).isSameTrip(null, 4 * 60_000))
        assertFalse(snapshot(null, 0).isSameTrip(null, 6 * 60_000))
    }
}
