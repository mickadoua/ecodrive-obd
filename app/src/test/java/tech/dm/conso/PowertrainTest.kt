package tech.dm.conso

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.dm.conso.eco.VehicleModel
import tech.dm.conso.obd.Elm327
import tech.dm.conso.obd.FuelCalculator
import tech.dm.conso.obd.FuelMethod
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.Pids
import tech.dm.conso.obd.Powertrain
import tech.dm.conso.obd.SimulatedElm327

class PowertrainTest {
    @Test
    fun fuelTypeFollowsSaeCoding() {
        assertEquals(FuelType.DIESEL, FuelType.fromPid51(0x04))
        assertEquals(FuelType.DIESEL, FuelType.fromPid51(0x13))
        assertEquals(FuelType.DIESEL, FuelType.fromPid51(0x17))
        assertEquals(FuelType.GASOLINE, FuelType.fromPid51(0x0B))
        assertEquals(FuelType.GASOLINE, FuelType.fromPid51(0x11))
    }

    @Test
    fun powertrainFromPid51() {
        assertEquals(Powertrain.ELECTRIC, Powertrain.fromPid51(0x08))
        for (code in 0x0F..0x16) assertEquals(Powertrain.HYBRID, Powertrain.fromPid51(code))
        assertEquals(Powertrain.ICE, Powertrain.fromPid51(0x01))
        assertEquals(Powertrain.ICE, Powertrain.fromPid51(0x17))
    }

    @Test
    fun detectionNeverDowngradesManualChoice() {
        assertEquals(Powertrain.HYBRID, Powertrain.resolve(Powertrain.HYBRID, 0x01))
        assertEquals(Powertrain.HYBRID, Powertrain.resolve(Powertrain.HYBRID, null))
        assertEquals(Powertrain.ELECTRIC, Powertrain.resolve(Powertrain.ICE, 0x08))
        assertEquals(Powertrain.HYBRID, Powertrain.resolve(Powertrain.ICE, 0x11))
    }

    @Test
    fun hybridBatteryScaling() {
        assertEquals(100.0, Pids.hybridBatteryPct(intArrayOf(0xFF)), 0.01)
        assertEquals(50.2, Pids.hybridBatteryPct(intArrayOf(0x80)), 0.01)
    }

    @Test
    fun electricUsesPhysicalModelEvenWithMaf() {
        assertEquals(FuelMethod.ELECTRIC_MODEL, FuelCalculator.chooseMethod({ true }, FuelType.GASOLINE, Powertrain.ELECTRIC))
        assertEquals(FuelMethod.MAF, FuelCalculator.chooseMethod({ it == Pids.MAF }, FuelType.GASOLINE, Powertrain.HYBRID))
    }

    @Test
    fun electricPowerWithRegen() {
        val m = VehicleModel(powertrain = Powertrain.ELECTRIC)
        assertEquals(0.4, m.electricKw(0.0), 1e-9)
        assertEquals(20_000 / 0.88 / 1000 + 0.4, m.electricKw(20_000.0), 1e-9)
        assertEquals(0.4 - 6.0, m.electricKw(-10_000.0), 1e-9)
        assertEquals(0.4 - 30.0, m.electricKw(-200_000.0), 1e-9)
        assertEquals(m.electricKw(15_000.0), m.fuelLh(15_000.0, FuelType.GASOLINE), 1e-9)
    }

    @Test
    fun electricAt90KmhIsAroundFifteenKwhPer100() {
        val m = VehicleModel(massKg = 1600.0, powertrain = Powertrain.ELECTRIC)
        val kwh100 = FuelCalculator.per100km(m.electricKw(m.tractionPowerW(25.0, 0.0, 0.0)), 90.0)!!
        assertTrue(kwh100 in 10.0..20.0)
    }

    @Test
    fun hybridIsMoreFrugalThanIce() {
        val p = VehicleModel().tractionPowerW(25.0, 0.0, 0.0)
        assertTrue(VehicleModel(powertrain = Powertrain.HYBRID).fuelLh(p, FuelType.GASOLINE) < VehicleModel().fuelLh(p, FuelType.GASOLINE))
    }

    @Test
    fun electricSimulator() = runBlocking {
        var now = 0L
        val elm = Elm327(SimulatedElm327(Powertrain.ELECTRIC) { now })
        elm.initialize()
        assertEquals(0x08, elm.query(Pids.FUEL_TYPE)!![0])
        assertTrue(elm.supports(Pids.HYBRID_BATTERY) && elm.supports(Pids.SPEED))
        assertFalse(elm.supports(Pids.MAF) || elm.supports(Pids.RPM))
    }

    @Test
    fun hybridSimulatorRollsWithEngineOff() = runBlocking {
        var now = 0L
        val elm = Elm327(SimulatedElm327(Powertrain.HYBRID) { now })
        elm.initialize()
        assertEquals(0x11, elm.query(Pids.FUEL_TYPE)!![0])
        var electricRolling = false
        repeat(80) {
            now += 500
            val speed = Pids.speedKmh(elm.query(Pids.SPEED)!!)
            val rpm = Pids.rpm(elm.query(Pids.RPM)!!)
            if (speed > 10 && rpm == 0.0) electricRolling = true
        }
        assertTrue(electricRolling)
        assertTrue(Pids.hybridBatteryPct(elm.query(Pids.HYBRID_BATTERY)!!) in 15.0..95.0)
    }
}
