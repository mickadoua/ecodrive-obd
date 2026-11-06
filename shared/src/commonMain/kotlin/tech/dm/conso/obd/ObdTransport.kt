package tech.dm.conso.obd

interface ObdTransport {
    suspend fun open()
    suspend fun send(command: String, timeoutMs: Long = 2_000): String
    fun close()
}
