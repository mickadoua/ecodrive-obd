package tech.dm.conso

import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import tech.dm.conso.eco.EcoHint
import tech.dm.conso.eco.EcoScorer
import tech.dm.conso.obd.Elm327
import tech.dm.conso.obd.FuelCalculator
import tech.dm.conso.obd.FuelMethod
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.Pids
import tech.dm.conso.obd.SimulatedElm327

class ObdParsingTest {
    @Test
    fun parsesResponseWithSearchingAndSpaces() {
        val data = Elm327.parseResponse("SEARCHING...\r41 0C 1A F8\r\r", Pids.RPM)!!
        assertEquals(1726.0, Pids.rpm(data), 0.01)
    }

    @Test
    fun noDataReturnsNull() {
        assertNull(Elm327.parseResponse("NO DATA\r\r", Pids.SPEED))
    }

    @Test
    fun supportBitmapRoundTrip() {
        val pids = setOf(0x01, 0x0C, 0x0D, 0x10, 0x20)
        val hex = Elm327.encodeSupportBitmap(0, pids)
        val data = hex.chunked(2).map { it.toInt(16) }.toIntArray()
        assertEquals(pids, Elm327.decodeSupportBitmap(0, data))
    }

    @Test
    fun knownBitmapFromRealCar() {
        val data = Elm327.parseResponse("4100BE3FA813", 0)!!
        val pids = Elm327.decodeSupportBitmap(0, data)
        assertTrue(Pids.RPM in pids && Pids.SPEED in pids && Pids.MAF in pids && 0x20 in pids)
    }

    @Test
    fun mafToLitersPerHour() {
        assertEquals(1.64, FuelCalculator.fromMaf(5.0, FuelType.GASOLINE), 0.01)
        assertEquals(5.5, FuelCalculator.per100km(4.95, 90.0)!!, 0.01)
        assertNull(FuelCalculator.per100km(0.8, 0.0))
    }

    @Test
    fun dieselWithoutFuelRateIsNotEstimated() {
        assertEquals(FuelMethod.NONE, FuelCalculator.chooseMethod({ it == Pids.MAF }, FuelType.DIESEL))
        assertEquals(FuelMethod.MAF, FuelCalculator.chooseMethod({ it == Pids.MAF }, FuelType.GASOLINE))
    }

    @Test
    fun uphillConsumptionIsExcused() {
        val flat = EcoScorer().evaluate(90.0, 0.0, 0.0, 2200.0, 9.0)
        val uphill = EcoScorer().evaluate(90.0, 0.0, 0.06, 2200.0, 9.0)
        assertTrue(uphill.instantScore > flat.instantScore)
        assertEquals(EcoHint.OVER_CONSUMPTION, flat.hint)
    }

    @Test
    fun downhillWithThrottleIsFlagged() {
        val r = EcoScorer().evaluate(80.0, 0.0, -0.06, 2000.0, 3.0)
        assertEquals(EcoHint.DOWNHILL_COAST, r.hint)
    }

    @Test
    fun simulatorSpeaksElm327() = runTest {
        var now = 0L
        val sim = SimulatedElm327 { now }
        val elm = Elm327(sim)
        elm.initialize()
        assertTrue(elm.supports(Pids.MAF) && !elm.supports(Pids.FUEL_RATE))
        repeat(40) { now += 500; elm.query(Pids.SPEED) }
        val speed = Pids.speedKmh(elm.query(Pids.SPEED)!!)
        val maf = Pids.mafGs(elm.query(Pids.MAF)!!)
        assertTrue(speed > 0 && maf > 0)
    }
}
