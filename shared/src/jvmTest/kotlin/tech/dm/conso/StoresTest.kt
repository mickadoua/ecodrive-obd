package tech.dm.conso

import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.Sample
import tech.dm.conso.data.Segment
import tech.dm.conso.data.SegmentStore
import tech.dm.conso.data.TextFile
import tech.dm.conso.data.TripStore
import tech.dm.conso.data.TripSummary
import tech.dm.conso.data.TripSummaryStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoresTest {
    private val dir = createTempDirectory().toFile()
    private fun text(name: String) = TextFile(File(dir, name).path)

    @Test
    fun loadsLegacyTripSnapshotAndSavesItBack() {
        File(dir, "trip.json").writeText(PersistenceFormatTest.LEGACY_TRIP)
        val snap = TripStore(text("trip.json")).load()!!
        assertNull(snap.engineStartMs)
        assertEquals(Sample(1700000002000, 0.0, null, null, -0.02, null), snap.samples[1])
        assertEquals(82.0, snap.samples[0].ecoScore)
        assertTrue(snap.finalized)

        TripStore(text("trip.json")).save(snap)
        val again = TripStore(text("trip.json")).load()!!
        assertEquals(snap.copy(stats = again.stats), again)
        assertTrue(!File(dir, "trip.json.tmp").exists())
    }

    @Test
    fun segmentAndSummaryStoresAppendAndReload() {
        val seg = Segment("abc-1", 1, 2, listOf(GeoPoint(1.0, 2.0), GeoPoint(1.001, 2.0)), 0.1, 0.01, 0.008, 0.0, 40.0)
        SegmentStore(text("segments.jsonl")).apply { append(seg); append(seg.copy(key = "abc-2")) }
        assertEquals(listOf("abc-1", "abc-2"), SegmentStore(text("segments.jsonl")).all().map { it.key })

        File(dir, "trips.jsonl").writeText(PersistenceFormatTest.LEGACY_TRIPS + "\n")
        val store = TripSummaryStore(text("trips.jsonl"))
        val first = store.all().first()
        store.add(first.copy(endCity = "Lyon"))
        val reloaded = TripSummaryStore(text("trips.jsonl")).all()
        assertEquals(2, reloaded.size)
        assertEquals("Grenoble → Lyon", reloaded.first { it.id == first.id }.route)
    }

    @Test
    fun missingFilesGiveEmptyStores() {
        assertNull(TripStore(text("none.json")).load())
        assertEquals(emptyList<TripSummary>(), TripSummaryStore(text("none.jsonl")).all())
    }
}
