package tech.dm.conso.obd

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSASCIIStringEncoding
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class BleTransport(
    private val serviceUuids: List<String> = DEFAULT_SERVICES,
    private val namePrefixes: List<String> = DEFAULT_NAMES,
) : ObdTransport {
    private val incoming = Channel<String>(Channel.UNLIMITED)
    private var ready = CompletableDeferred<Unit>()
    private var central: CBCentralManager? = null
    private var peripheral: CBPeripheral? = null
    private var notify: CBCharacteristic? = null
    private var write: CBCharacteristic? = null
    private var pendingServices = 0

    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            if (central.state == CBManagerStatePoweredOn) central.scanForPeripheralsWithServices(null, null)
            else ready.completeExceptionally(ObdException("Bluetooth indisponible"))
        }

        override fun centralManager(central: CBCentralManager, didDiscoverPeripheral: CBPeripheral, advertisementData: Map<Any?, *>, RSSI: NSNumber) {
            if (peripheral != null || !matches(didDiscoverPeripheral, advertisementData)) return
            central.stopScan()
            peripheral = didDiscoverPeripheral
            didDiscoverPeripheral.delegate = this
            central.connectPeripheral(didDiscoverPeripheral, null)
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            didConnectPeripheral.discoverServices(null)
        }

        override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
            ready.completeExceptionally(ObdException(error?.localizedDescription ?: "Connexion BLE impossible"))
        }

        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            val services = peripheral.services.orEmpty().filterIsInstance<CBService>()
            pendingServices = services.size
            if (services.isEmpty()) ready.completeExceptionally(ObdException("Aucun service BLE"))
            services.forEach { peripheral.discoverCharacteristics(null, it) }
        }

        override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
            for (c in didDiscoverCharacteristicsForService.characteristics.orEmpty().filterIsInstance<CBCharacteristic>()) {
                if (notify == null && c.properties and CBCharacteristicPropertyNotify != 0UL) notify = c
                if (write == null && c.properties and (CBCharacteristicPropertyWrite or CBCharacteristicPropertyWriteWithoutResponse) != 0UL) write = c
            }
            pendingServices--
            val n = notify
            if (n != null && write != null) {
                peripheral.setNotifyValue(true, n)
                ready.complete(Unit)
            } else if (pendingServices == 0) {
                ready.completeExceptionally(ObdException("Caractéristiques ELM327 introuvables"))
            }
        }

        override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
            val data = didUpdateValueForCharacteristic.value ?: return
            NSString.create(data, NSASCIIStringEncoding)?.toString()?.let { incoming.trySend(it) }
        }
    }

    private fun matches(p: CBPeripheral, advertisement: Map<Any?, *>): Boolean {
        val wanted = serviceUuids.map { CBUUID.UUIDWithString(it) }
        val advertised = (advertisement["kCBAdvDataServiceUUIDs"] as? List<*>).orEmpty()
        val name = p.name.orEmpty()
        return advertised.any { it in wanted } || namePrefixes.any { name.startsWith(it, ignoreCase = true) }
    }

    override suspend fun open() {
        ready = CompletableDeferred()
        central = CBCentralManager(delegate, null)
        withTimeout(SCAN_TIMEOUT_MS) { ready.await() }
    }

    override suspend fun send(command: String, timeoutMs: Long): String {
        val p = peripheral ?: throw ObdException("BLE non connecté")
        val w = write ?: throw ObdException("BLE non connecté")
        while (incoming.tryReceive().isSuccess) Unit
        val data = NSString.create(string = "$command\r").dataUsingEncoding(NSASCIIStringEncoding) ?: throw ObdException("Commande invalide")
        val type = if (w.properties and CBCharacteristicPropertyWrite != 0UL) CBCharacteristicWriteWithResponse else CBCharacteristicWriteWithoutResponse
        p.writeValue(data, w, type)
        return withTimeout(timeoutMs) {
            val out = StringBuilder()
            while ('>' !in out) out.append(incoming.receive())
            out.substring(0, out.indexOf(">"))
        }
    }

    override fun close() {
        peripheral?.let { central?.cancelPeripheralConnection(it) }
        central?.stopScan()
        peripheral = null
        notify = null
        write = null
        central = null
    }

    companion object {
        val DEFAULT_SERVICES = listOf("FFF0", "FFE0", "18F0")
        val DEFAULT_NAMES = listOf("OBD", "OBDII", "V-LINK", "IOS-Vlink", "Vgate", "VEEPEAK", "Android-Vlink")
        private const val SCAN_TIMEOUT_MS = 20_000L
    }
}
