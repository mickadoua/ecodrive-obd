package tech.dm.conso.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import tech.dm.conso.BuildConfig
import tech.dm.conso.ConsoApp
import tech.dm.conso.auto.AutoStartStore
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
            val instant = s.litersPer100?.let { colored("%.1f L/100 km".format(it), 100 - (it - 4) * 10) }
                ?: SpannableString(s.litersPerHour?.let { "%.1f L/h".format(it) } ?: "–")
            val rows = listOf(
                row("Consommation", SpannableStringBuilder(instant).append(" · %.0f km/h".format(s.speedKmh))),
                row("Conseil", carTips(s).take(2).joinToString(" · ").ifEmpty { "En attente de données" }),
                row(
                    "Éco-score",
                    SpannableStringBuilder(s.eco?.let { colored("%.0f/100".format(it.score), it.score) } ?: "–")
                        .append(" · pente %+.1f %%".format(s.grade * 100)),
                ),
                row(
                    "Trajet",
                    "%.1f km · moy. ".format(s.tripKm) + (s.tripAvgL100?.let { "%.1f L/100".format(it) } ?: "–"),
                ),
            )
            rows.take(rowLimit()).forEach(pane::addRow)
            if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_4) {
                val points = trendPoints(s.samples)
                if (points.count { it != null } >= 2) {
                    pane.setImage(CarIcon.Builder(IconCompat.createWithBitmap(renderTrend(points, s.tripAvgL100))).build())
                }
            }

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

    private fun row(title: String, text: CharSequence) = Row.Builder().setTitle(title).addText(text).build()

    private fun colored(text: String, score: Double) = SpannableString(text).apply {
        val color = when {
            score >= 75 -> CarColor.GREEN
            score >= 50 -> CarColor.YELLOW
            else -> CarColor.RED
        }
        setSpan(ForegroundCarColorSpan.create(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun rowLimit(): Int =
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)
        } else 4

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
