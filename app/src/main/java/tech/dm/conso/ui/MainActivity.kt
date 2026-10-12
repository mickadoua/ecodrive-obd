package tech.dm.conso.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.dm.conso.ConsoApp
import tech.dm.conso.data.ObdSource
import tech.dm.conso.service.ObdService

class MainActivity : ComponentActivity() {
    private val telemetry get() = ConsoApp.instance.telemetry
    private var showPicker by mutableStateOf(false)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        showPicker = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConsoTheme {
                val state by telemetry.state.collectAsStateWithLifecycle()
                val version by telemetry.historyVersion.collectAsStateWithLifecycle()
                val days = remember(version, state.tripKm.toInt()) { telemetry.history.lastDays(7) }
                Dashboard(
                    state = state,
                    days = days,
                    onConnectClick = ::requestPermissionsThenPick,
                    onDisconnect = telemetry::disconnect,
                )
                if (showPicker) {
                    SourcePicker(
                        sources = availableSources(),
                        onPick = {
                            showPicker = false
                            telemetry.connect(it)
                            ObdService.start(this)
                        },
                        onDismiss = { showPicker = false },
                    )
                }
            }
        }
    }

    private fun requestPermissionsThenPick() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) showPicker = true else permissions.launch(wanted.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun availableSources(): List<ObdSource> {
        val canUseBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val bonded = if (canUseBt) {
            getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .sortedByDescending { d -> OBD_HINTS.any { d.name?.contains(it, ignoreCase = true) == true } }
                .map { ObdSource.Bluetooth(it, it.name ?: it.address) }
        } else emptyList()
        return bonded + ObdSource.Simulator
    }

    companion object {
        private val OBD_HINTS = listOf("OBD", "ELM", "VLINK", "VGATE", "KONNWEI", "V-LINK")
    }
}
