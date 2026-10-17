package tech.dm.conso

import kotlinx.coroutines.runBlocking
import tech.dm.conso.obd.Elm327
import tech.dm.conso.obd.Pids
import tech.dm.conso.obd.SimulatedElm327
import tech.dm.conso.obd.TcpTransport
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TcpTransportTest {
    private fun fakeWifiDongle(): ServerSocket {
        val server = ServerSocket(0)
        val sim = SimulatedElm327()
        thread(isDaemon = true) {
            server.accept().use { client ->
                val input = client.getInputStream().bufferedReader(Charsets.US_ASCII)
                val output = client.getOutputStream()
                val line = StringBuilder()
                while (true) {
                    val c = input.read()
                    if (c < 0) break
                    if (c.toChar() != '\r') { line.append(c.toChar()); continue }
                    val reply = runBlocking { sim.send(line.toString()) }
                    line.clear()
                    output.write("$reply>".toByteArray(Charsets.US_ASCII))
                    output.flush()
                }
            }
        }
        return server
    }

    @Test
    fun elm327OverWifi() = runBlocking {
        val server = fakeWifiDongle()
        val elm = Elm327(TcpTransport("127.0.0.1", server.localPort))
        elm.initialize()
        assertTrue(elm.supports(Pids.SPEED) && elm.supports(Pids.MAF))
        assertEquals(0, Pids.speedKmh(elm.query(Pids.SPEED)!!).toInt())
        assertEquals(SimulatedElm327.SIM_VIN, elm.readVin())
        elm.close()
        server.close()
    }
}
