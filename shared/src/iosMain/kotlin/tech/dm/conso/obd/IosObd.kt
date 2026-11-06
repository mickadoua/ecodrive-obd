package tech.dm.conso.obd

import tech.dm.conso.live.LiveSession

object IosObd {
    fun simulator(): ObdTransport = SimulatedElm327()
    fun bluetoothLe(): ObdTransport = BleTransport()
    fun wifi(host: String, port: Int): ObdTransport = TcpTransport(host, port)
    fun session(transport: ObdTransport) = LiveSession(transport)
}
