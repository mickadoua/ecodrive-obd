package tech.dm.conso.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import tech.dm.conso.BuildConfig
import tech.dm.conso.auto.AutoStartStore
import tech.dm.conso.ConsoApp
import tech.dm.conso.data.ConnectionStatus
import tech.dm.conso.data.ObdSource
import tech.dm.conso.service.ObdService

class ConsoCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = DashboardScreen(carContext)
    }
}

class DashboardScreen(carContext: CarContext) : Screen(carContext) {
    private val telemetry = ConsoApp.instance.telemetry

    init {
        if (telemetry.state.value.status == ConnectionStatus.DISCONNECTED) {
            AutoStartStore(carContext).savedObdSource(carContext)?.let(::start)
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var job: Job? = null
            override fun onStart(owner: LifecycleOwner) {
                job = lifecycleScope.launch { telemetry.state.sample(2_000).collect { invalidate() } }
            }
            override fun onStop(owner: LifecycleOwner) {
                job?.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val s = telemetry.state.value
        val pane = Pane.Builder()
        if (s.status == ConnectionStatus.CONNECTING) {
            pane.setLoading(true)
        } else {
            val instant = s.litersPer100?.let { "%.1f L/100 km".format(it) }
                ?: s.litersPerHour?.let { "%.1f L/h (arrêt)".format(it) } ?: "–"
            pane.addRow(row("Instantané", "$instant · %.0f km/h".format(s.speedKmh)))
            pane.addRow(
                row(
                    "Moyenne trajet",
                    (s.tripAvgL100?.let { "%.1f L/100 km".format(it) } ?: "–") + " · %.1f km".format(s.tripKm),
                )
            )
            pane.addRow(
                row(
                    "Éco-score " + (s.eco?.let { "%.0f/100".format(it.score) } ?: "–"),
                    s.eco?.hint?.text ?: "En attente",
                )
            )
            pane.addRow(row("Pente", "%+.1f %%".format(s.grade * 100) + (s.altitudeM?.let { " · %.0f m".format(it) } ?: "")))

            val active = s.status == ConnectionStatus.CONNECTED
            if (active) {
                pane.addAction(
                    Action.Builder().setTitle("Arrêter").setOnClickListener { telemetry.disconnect() }.build()
                )
            } else {
                pane.addAction(
                    Action.Builder().setTitle("OBD").setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener {
                            (AutoStartStore(carContext).savedObdSource(carContext) ?: firstObdDevice())?.let(::start) ?: invalidate()
                        }.build()
                )
                pane.addAction(Action.Builder().setTitle("Démo").setOnClickListener { start(ObdSource.Simulator) }.build())
            }
        }
        return PaneTemplate.Builder(pane.build())
            .setTitle(s.error?.takeIf { s.status == ConnectionStatus.ERROR }?.let { "Conso · $it" } ?: "Conso")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun row(title: String, text: String) = Row.Builder().setTitle(title).addText(text).build()

    private fun start(source: ObdSource) {
        telemetry.connect(source)
        runCatching { ObdService.start(carContext) }
    }

    @SuppressLint("MissingPermission")
    private fun firstObdDevice(): ObdSource? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(carContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        val devices = carContext.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
        val device = devices.firstOrNull { d -> listOf("OBD", "ELM", "VLINK", "VGATE").any { d.name?.contains(it, true) == true } }
            ?: return null
        return ObdSource.Bluetooth(device, device.name ?: device.address)
    }
}
