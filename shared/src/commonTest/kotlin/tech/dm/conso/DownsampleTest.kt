package tech.dm.conso

import kotlin.test.assertEquals
import kotlin.test.Test
import tech.dm.conso.data.Sample
import tech.dm.conso.data.bucketFor
import tech.dm.conso.data.downsample
import tech.dm.conso.obd.FuelCalculator

class DownsampleTest {
    @Test
    fun bucketGrowsWithWindow() {
        assertEquals(1_000L, bucketFor(60_000))
        assertEquals(5_000L, bucketFor(300_000))
        assertEquals(15_000L, bucketFor(900_000))
    }

    @Test
    fun bucketL100IsTotalFuelOverTotalDistance() {
        val samples = listOf(
            Sample(0, 50.0, 2.0, 100.0, 0.0, 80.0),
            Sample(1_000, 100.0, 10.0, 102.0, 0.02, 60.0),
        )
        val p = downsample(samples, 5_000).single()
        assertEquals(12.0 / 150.0 * 100, FuelCalculator.per100km(p.litersPerHour!!, p.speedKmh)!!, 1e-9)
        assertEquals(70.0, p.ecoScore!!, 1e-9)
        assertEquals(101.0, p.altitudeM!!, 1e-9)
    }
}
