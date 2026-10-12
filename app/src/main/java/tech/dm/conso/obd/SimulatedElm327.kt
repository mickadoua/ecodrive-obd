package tech.dm.conso.obd

import android.os.SystemClock
import kotlinx.coroutines.delay
import tech.dm.conso.eco.VehicleModel
import tech.dm.conso.data.AltitudeSource
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class SimulatedElm327(private val clock: () -> Long = SystemClock::elapsedRealtime) : ObdTransport, AltitudeSource {
    private data class Leg(val targetKmh: Double, val seconds: Double, val accel: Double)

    private val legs = listOf(
        Leg(0.0, 8.0, 1.0), Leg(50.0, 35.0, 1.2), Leg(90.0, 60.0, 0.9),
        Leg(130.0, 45.0, 2.4), Leg(70.0, 40.0, 0.8), Leg(30.0, 20.0, 1.0),
    )
    private val model = VehicleModel()
    private val supportedPids = setOf(0x0B, 0x0C, 0x0D, 0x0F, 0x10, 0x20, 0x51)

    private var lastTick = 0L
    private var legIndex = 0
    private var legElapsed = 0.0
    private var speedMs = 0.0
    private var accel = 0.0
    private var distanceM = 0.0
    private var fuelLh = model.idleLh
    private var rpm = 800.0

    override val altitudeM: Double? get() = terrain(distanceM)

    override suspend fun open() {
        lastTick = clock()
    }

    override suspend fun send(command: String, timeoutMs: Long): String {
        delay(25)
        step()
        val cmd = command.uppercase().replace(" ", "")
        if (cmd.startsWith("AT")) return if (cmd == "ATZ") "ELM327 v1.5\r\r" else "OK\r\r"
        if (!cmd.startsWith("01") || cmd.length != 4) return "?\r\r"
        val pid = cmd.substring(2).toInt(16)
        val payload = when {
            pid % 0x20 == 0 && pid <= 0x40 -> Elm327.encodeSupportBitmap(pid, supportedPids)
            pid !in supportedPids -> return "NO DATA\r\r"
            pid == Pids.SPEED -> hex1(speedMs * 3.6)
            pid == Pids.RPM -> hex2(rpm * 4)
            pid == Pids.MAF -> hex2(fuelLh * FuelType.GASOLINE.stoichAfr * FuelType.GASOLINE.densityGl / 3600.0 * 100)
            pid == Pids.MAP -> hex1(30 + min(70.0, fuelLh * 6))
            pid == Pids.INTAKE_TEMP -> hex1(25.0 + 40)
            pid == Pids.FUEL_TYPE -> "01"
            else -> return "NO DATA\r\r"
        }
        return "41%02X%s\r\r".format(pid, payload)
    }

    override fun close() = Unit

    private fun step() {
        val now = clock()
        val dt = ((now - lastTick) / 1000.0).coerceIn(0.0, 1.0)
        lastTick = now
        if (dt == 0.0) return

        val leg = legs[legIndex]
        legElapsed += dt
        if (legElapsed > leg.seconds) {
            legElapsed = 0.0
            legIndex = (legIndex + 1) % legs.size
        }
        val target = leg.targetKmh / 3.6
        val diff = target - speedMs
        accel = when {
            diff > 0.3 -> min(leg.accel, diff)
            diff < -0.3 -> max(-2.0, diff)
            else -> Random.nextDouble(-0.15, 0.15)
        }
        speedMs = max(0.0, speedMs + accel * dt)
        val before = terrain(distanceM)
        distanceM += speedMs * dt
        val grade = if (speedMs * dt > 0.01) (terrain(distanceM) - before) / (speedMs * dt) else 0.0

        val power = model.tractionPowerW(speedMs, grade, accel)
        fuelLh = if (power < 0 && speedMs > 6) 0.0
        else model.fuelLh(power, FuelType.GASOLINE) * Random.nextDouble(1.0, 1.12)

        val kmh = speedMs * 3.6
        val gearRatio = when {
            kmh < 20 -> 110.0; kmh < 35 -> 70.0; kmh < 55 -> 48.0; kmh < 80 -> 36.0; else -> 24.0
        }
        val boost = if (accel > 2.0) 1.5 else 1.0
        rpm = max(800.0, kmh * gearRatio * boost)
    }

    private fun terrain(d: Double) = 180.0 + 30.0 * sin(d / 2000.0 * PI) + 5.0 * sin(d / 500.0 * PI)

    private fun hex1(v: Double) = "%02X".format(v.roundToInt().coerceIn(0, 255))
    private fun hex2(v: Double) = "%04X".format(v.roundToInt().coerceIn(0, 65535))
}
