package tech.dm.conso.obd

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.discard
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout

class TcpTransport(private val host: String = DEFAULT_HOST, private val port: Int = DEFAULT_PORT) : ObdTransport {
    private var selector: SelectorManager? = null
    private var socket: Socket? = null
    private var input: ByteReadChannel? = null
    private var output: ByteWriteChannel? = null

    override suspend fun open() {
        val sel = SelectorManager(Dispatchers.Default)
        val s = try {
            withTimeout(CONNECT_TIMEOUT_MS) { aSocket(sel).tcp().connect(host, port) }
        } catch (e: Throwable) {
            sel.close()
            throw e
        }
        selector = sel
        socket = s
        input = s.openReadChannel()
        output = s.openWriteChannel(autoFlush = false)
    }

    override suspend fun send(command: String, timeoutMs: Long): String {
        val read = input ?: throw ObdException("Socket fermé")
        val write = output ?: throw ObdException("Socket fermé")
        if (read.availableForRead > 0) read.discard(read.availableForRead.toLong())
        write.writeStringUtf8("$command\r")
        write.flush()
        return withTimeout(timeoutMs) {
            val out = StringBuilder()
            while (true) {
                val c = read.readByte().toInt().toChar()
                if (c == '>') break
                out.append(c)
            }
            out.toString()
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        runCatching { selector?.close() }
        socket = null
        selector = null
        input = null
        output = null
    }

    companion object {
        const val DEFAULT_HOST = "192.168.0.10"
        const val DEFAULT_PORT = 35000
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
