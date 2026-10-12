package tech.dm.conso.obd

class ObdException(message: String) : Exception(message)

class Elm327(private val transport: ObdTransport) {
    private val supported = mutableSetOf<Int>()

    suspend fun initialize() {
        transport.open()
        transport.send("ATZ", 5_000)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) transport.send(cmd)
        discoverSupportedPids()
    }

    fun supports(pid: Int) = pid in supported

    suspend fun query(pid: Int): IntArray? {
        val raw = transport.send("01%02X".format(pid), 2_000)
        return parseResponse(raw, pid)
    }

    suspend fun readVin(): String? = runCatching { Vin.parse(transport.send("0902", 5_000)) }.getOrNull()

    fun close() = transport.close()

    private suspend fun discoverSupportedPids() {
        var base = 0x00
        while (base <= 0xA0) {
            val raw = transport.send("01%02X".format(base), if (base == 0) 15_000 else 2_000)
            val data = parseResponse(raw, base)
            if (data == null) {
                if (base == 0) throw ObdException(errorOf(raw) ?: "Pas de réponse du véhicule")
                break
            }
            supported += decodeSupportBitmap(base, data)
            if ((base + 0x20) !in supported) break
            base += 0x20
        }
    }

    companion object {
        fun parseResponse(raw: String, pid: Int): IntArray? {
            val prefix = "41%02X".format(pid)
            return raw.split('\r', '\n')
                .map { it.replace(" ", "").uppercase() }
                .firstOrNull { it.startsWith(prefix) }
                ?.removePrefix(prefix)
                ?.chunked(2)
                ?.mapNotNull { it.takeIf { h -> h.length == 2 }?.toIntOrNull(16) }
                ?.toIntArray()
        }

        fun decodeSupportBitmap(base: Int, data: IntArray): Set<Int> {
            val result = mutableSetOf<Int>()
            data.take(4).forEachIndexed { byteIndex, byte ->
                for (bit in 0 until 8) {
                    if (byte and (0x80 shr bit) != 0) result += base + byteIndex * 8 + bit + 1
                }
            }
            return result
        }

        fun encodeSupportBitmap(base: Int, pids: Set<Int>): String {
            val bytes = IntArray(4)
            pids.filter { it in (base + 1)..(base + 32) }.forEach {
                val offset = it - base - 1
                bytes[offset / 8] = bytes[offset / 8] or (0x80 shr (offset % 8))
            }
            return bytes.joinToString("") { "%02X".format(it) }
        }

        private fun errorOf(raw: String): String? = listOf(
            "UNABLE TO CONNECT", "NO DATA", "BUS INIT", "CAN ERROR", "STOPPED", "?"
        ).firstOrNull { raw.uppercase().contains(it) }
    }
}
