package tech.dm.conso.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

class BluetoothTransport(private val device: BluetoothDevice) : ObdTransport {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    override suspend fun open() = withContext(Dispatchers.IO) {
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            s.connect()
        } catch (e: IOException) {
            s.close()
            throw e
        }
        socket = s
    }

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("Socket fermé")
        val input = s.inputStream
        while (input.available() > 0) input.read()
        s.outputStream.write("$command\r".toByteArray(Charsets.US_ASCII))
        s.outputStream.flush()
        withTimeout(timeoutMs) {
            val out = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) throw IOException("Flux terminé")
                val c = b.toChar()
                if (c == '>') break
                out.append(c)
            }
            out.toString()
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
