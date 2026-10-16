package tech.dm.conso.auto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import tech.dm.conso.ConsoApp
import tech.dm.conso.data.ObdSource
import tech.dm.conso.service.ObdService

data class AutoStartSettings(
    val enabled: Boolean = false,
    val carAddress: String? = null,
    val carName: String? = null,
    val obdAddress: String? = null,
    val obdName: String? = null,
)

class AutoStartStore(context: Context) {
    private val prefs = context.getSharedPreferences("autostart", Context.MODE_PRIVATE)

    fun load() = AutoStartSettings(
        enabled = prefs.getBoolean("enabled", false),
        carAddress = prefs.getString("car", null),
        carName = prefs.getString("carName", null),
        obdAddress = prefs.getString("obd", null),
        obdName = prefs.getString("obdName", null),
    )

    fun save(s: AutoStartSettings) {
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("car", s.carAddress)
            .putString("carName", s.carName)
            .putString("obd", s.obdAddress)
            .putString("obdName", s.obdName)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun savedObdSource(context: Context): ObdSource.Bluetooth? {
        val s = load()
        val address = s.obdAddress ?: return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return runCatching { ObdSource.Bluetooth(adapter.getRemoteDevice(address), s.obdName ?: address) }.getOrNull()
    }
}

class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val settings = AutoStartStore(context).load()
        if (!settings.enabled || device.address != settings.carAddress) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> ObdService.startAuto(context)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> ConsoApp.instance.telemetry.endTrip()
        }
    }
}
