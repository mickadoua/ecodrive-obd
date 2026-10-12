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
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.dm.conso.ConsoApp
import tech.dm.conso.auto.AutoStartSettings
import tech.dm.conso.auto.AutoStartStore
import tech.dm.conso.data.ConnectionStatus
import tech.dm.conso.data.DayStats
import tech.dm.conso.data.ObdSource
import tech.dm.conso.leaderboard.LeaderboardRepository
import tech.dm.conso.leaderboard.MockLeaderboard
import tech.dm.conso.service.ObdService

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Direct", Icons.Rounded.Speed),
    MAP("Carte", Icons.Rounded.Map),
    LEADERBOARD("Classement", Icons.Rounded.EmojiEvents),
    VEHICLE("Véhicule", Icons.Rounded.DirectionsCar),
}

class MainActivity : ComponentActivity() {
    private val leaderboard: LeaderboardRepository = MockLeaderboard()
    private val autoStartStore by lazy { AutoStartStore(this) }
    private var autoStart by mutableStateOf(AutoStartSettings())
    private var backgroundLocation by mutableStateOf(false)

    private val backgroundLocationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        backgroundLocation = it
    }
    private val telemetry get() = ConsoApp.instance.telemetry
    private var showPicker by mutableStateOf(false)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        showPicker = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        autoStart = autoStartStore.load()
        if (savedInstanceState == null) reconnectLastModule()
        setContent {
            ConsoTheme {
                val state by telemetry.state.collectAsStateWithLifecycle()
                val version by telemetry.historyVersion.collectAsStateWithLifecycle()
                val profile by telemetry.profile.collectAsStateWithLifecycle()
                val roadNames by telemetry.roads.names.collectAsStateWithLifecycle()
                val unresolvable by telemetry.roads.unresolvable.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
                var focusKey by remember { mutableStateOf<String?>(null) }
                var selectedDay by remember { mutableStateOf<DayStats?>(null) }
                val days = remember(version, state.tripKm.toInt()) { telemetry.history.lastDays(7) }
                val segments = remember(version) { telemetry.segments.all() }
                val myL100 = remember(version) {
                    telemetry.history.lastDays(30).let { d ->
                        val km = d.sumOf { it.distanceKm }
                        if (km > 1) d.sumOf { it.fuelL } / km * 100 else null
                    }
                }
                val portions = remember(segments) { leaderboard.portions(segments) }
                val models = remember(profile, myL100) { leaderboard.equivalentModels(profile, myL100) }

                val connected = state.status == ConnectionStatus.CONNECTED
                LaunchedEffect(connected) {
                    if (connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                Scaffold(
                    containerColor = Palette.Background,
                    bottomBar = {
                        NavigationBar(containerColor = Palette.Surface) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t; selectedDay = null },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Palette.Background,
                                        indicatorColor = Palette.Green,
                                        selectedTextColor = Palette.Green,
                                        unselectedIconColor = Palette.Muted,
                                        unselectedTextColor = Palette.Muted,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        val day = selectedDay
                        if (day != null) {
                            DayDetailScreen(day, segments, roadNames, unresolvable, telemetry.roads::resolve) { selectedDay = null }
                        } else when (tab) {
                            Tab.DASHBOARD -> Dashboard(
                                state = state,
                                days = days,
                                onConnectClick = ::requestPermissionsThenPick,
                                onDisconnect = telemetry::disconnect,
                                onDayClick = { selectedDay = it },
                            )
                            Tab.MAP -> MapScreen(segments, roadNames, unresolvable, state.location, focusKey, telemetry.roads::resolve)
                            Tab.LEADERBOARD -> LeaderboardScreen(
                                portions = portions,
                                models = models,
                                profile = profile,
                                simulated = leaderboard.isSimulated,
                                onPortionClick = { focusKey = it; tab = Tab.MAP },
                                onConfigureVehicle = { tab = Tab.VEHICLE },
                            )
                            Tab.VEHICLE -> VehicleScreen(profile, state.detectedVehicle, telemetry::saveProfile) {
                                AutoStartCard(
                                    settings = autoStart,
                                    bonded = bondedDevices(),
                                    backgroundLocationGranted = backgroundLocation,
                                    onChange = { autoStart = it; autoStartStore.save(it) },
                                    onRequestBackgroundLocation = {
                                        backgroundLocationRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    },
                                )
                            }
                        }
                    }
                }
                if (showPicker) {
                    SourcePicker(
                        sources = availableSources(),
                        onPick = {
                            showPicker = false
                            if (it is ObdSource.Bluetooth) {
                                autoStart = autoStart.copy(obdAddress = it.device.address, obdName = it.label)
                                autoStartStore.save(autoStart)
                            }
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

    private fun reconnectLastModule() {
        val canUseBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!canUseBt || telemetry.state.value.status != ConnectionStatus.DISCONNECTED) return
        val source = autoStartStore.savedObdSource(this) ?: return
        telemetry.connect(source, retry = true, maxAttempts = 3)
        ObdService.start(this)
    }

    override fun onResume() {
        super.onResume()
        backgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun bondedDevices(): List<BondedDevice> {
        val canUseBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!canUseBt) return emptyList()
        return getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .map { BondedDevice(it.address, it.name ?: it.address) }
            .sortedBy { it.name.lowercase() }
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
