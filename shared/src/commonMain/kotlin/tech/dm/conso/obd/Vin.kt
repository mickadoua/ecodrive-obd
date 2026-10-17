package tech.dm.conso.obd

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tech.dm.conso.hex
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class VinInfo(val make: String?, val modelYear: Int?)

object Vin {
    private val WMI = mapOf(
        "VF1" to "Renault", "VF2" to "Renault", "VF6" to "Renault", "UU1" to "Dacia", "VGA" to "Dacia",
        "VF3" to "Peugeot", "VR3" to "Peugeot", "VF7" to "Citroën", "VR7" to "Citroën", "VR1" to "DS",
        "WVW" to "Volkswagen", "WV1" to "Volkswagen", "WV2" to "Volkswagen", "WAU" to "Audi", "WUA" to "Audi",
        "TMB" to "Skoda", "VSS" to "Seat", "WBA" to "BMW", "WMW" to "Mini", "WDD" to "Mercedes-Benz",
        "W1K" to "Mercedes-Benz", "WF0" to "Ford", "W0L" to "Opel", "W0V" to "Opel", "ZFA" to "Fiat",
        "ZFF" to "Ferrari", "ZAR" to "Alfa Romeo", "SJN" to "Nissan", "VSK" to "Nissan", "JN1" to "Nissan",
        "JTD" to "Toyota", "SB1" to "Toyota", "VNK" to "Toyota", "JMZ" to "Mazda", "KMH" to "Hyundai",
        "TMA" to "Hyundai", "KNA" to "Kia", "U5Y" to "Kia", "YV1" to "Volvo", "JHM" to "Honda",
        "SHH" to "Honda", "JSA" to "Suzuki", "TSM" to "Suzuki", "LRW" to "Tesla", "5YJ" to "Tesla",
    )
    private const val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY123456789"

    fun parse(raw: String): String? {
        val lines = raw.uppercase().split('\r', '\n').map { it.trim().replace(" ", "") }.filter { it.isNotEmpty() }
        val payload = if (lines.any { ':' in it }) {
            val joined = lines.filter { ':' in it }.joinToString("") { it.substringAfter(':') }
            val start = joined.indexOf("4902")
            if (start < 0) return null
            joined.substring(start + 6)
        } else {
            lines.filter { it.startsWith("4902") }.joinToString("") { it.drop(6) }
        }
        val vin = payload.chunked(2)
            .mapNotNull { it.takeIf { h -> h.length == 2 }?.toIntOrNull(16) }
            .filter { it in 0x30..0x5A }
            .map { it.toChar() }
            .joinToString("")
            .takeLast(17)
        return vin.takeIf { it.length == 17 && it.none { c -> c in "IOQ" } }
    }

    @OptIn(ExperimentalTime::class)
    fun decode(vin: String): VinInfo {
        val make = WMI[vin.take(3)]
        val code = YEAR_CODES.indexOf(vin[9])
        val year = if (code < 0) null else {
            val base = 2010 + code
            if (base > Clock.System.todayIn(TimeZone.currentSystemDefault()).year + 1) base - 30 else base
        }
        return VinInfo(make, year)
    }

    fun encodeForSimulator(vin: String): String {
        val bytes = "01" + vin.map { it.code.hex(2) }.joinToString("")
        val payload = "4902$bytes"
        val lines = mutableListOf((payload.length / 2).hex(3))
        payload.chunked(14).forEachIndexed { i, chunk -> lines += "$i:$chunk" }
        return lines.joinToString("\r") + "\r\r"
    }
}
