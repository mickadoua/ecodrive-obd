package tech.dm.conso.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.validation.HostValidator
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import tech.dm.conso.BuildConfig
import tech.dm.conso.ConsoApp
import tech.dm.conso.auto.AutoStartStore
import tech.dm.conso.data.ConnectionStatus
import tech.dm.conso.data.LiveState
import tech.dm.conso.data.ObdSource
import tech.dm.conso.service.ObdService

class ConsoCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session = ConsoSession()
}

class ConsoSession : Session(), SavedStateRegistryOwner {
    private val savedState = SavedStateRegistryController.create(this).apply {
        performAttach()
        performRestore(null)
    }
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreateScreen(intent: Intent): Screen {
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_7) {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(DashboardSurface(carContext, this))
        }
        return DashboardScreen(carContext)
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
        if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_7) return tilesTemplate(s)
        val content = if (s.status == ConnectionStatus.CONNECTED) {
            ListTemplate.Builder()
                .setTitle("Conso")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(
                    ItemList.Builder()
                        .addItem(Row.Builder().setTitle(carTips(s).firstOrNull() ?: "En attente de données").build())
                        .build()
                )
                .build()
        } else tilesTemplate(s)
        val map = MapWithContentTemplate.Builder().setContentTemplate(content)
        if (s.status == ConnectionStatus.CONNECTED) {
            val stop = Action.Builder().setTitle("Arrêter").setOnClickListener { telemetry.disconnect() }.build()
            map.setActionStrip(ActionStrip.Builder().addAction(stop).build())
        }
        return map.build()
    }

    private fun tilesTemplate(s: LiveState): Template {
        val grid = GridTemplate.Builder()
            .setTitle(s.error?.takeIf { s.status == ConnectionStatus.ERROR }?.let { "Conso · $it" } ?: "Conso")
            .setHeaderAction(Action.APP_ICON)
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_7) grid.setItemSize(GridTemplate.ITEM_SIZE_LARGE)
        if (s.status == ConnectionStatus.CONNECTING) return grid.setLoading(true).build()

        if (s.status != ConnectionStatus.CONNECTED) {
            val start = ItemList.Builder()
                .addItem(
                    tile("OBD", "Module Bluetooth", CarArt.consoGauge(null, 80.0)) {
                        (AutoStartStore(carContext).savedObdSource(carContext) ?: firstObdDevice())?.let(::start) ?: invalidate()
                    }
                )
                .addItem(tile("Démo", "Simulateur", CarArt.scoreRing(80.0)) { start(ObdSource.Simulator) })
                .build()
            return grid.setSingleList(start).build()
        }

        val score = s.eco?.score
        val tiles = ItemList.Builder()
            .addItem(
                tile(
                    s.litersPer100?.let { "%.1f".format(it) } ?: s.litersPerHour?.let { "%.1f".format(it) } ?: "–",
                    if (s.litersPer100 == null && s.litersPerHour != null) "L/h à l'arrêt" else "L/100 km",
                    CarArt.consoGauge(s.litersPer100, score ?: 80.0),
                )
            )
            .addItem(tile(score?.let { "%.0f".format(it) } ?: "–", "Éco-score", CarArt.scoreRing(score ?: 0.0)))
            .addItem(tile(carTips(s).firstOrNull() ?: "–", "Conseil", CarArt.tipIcon(s)))
            .addItem(
                tile(
                    s.tripAvgL100?.let { "%.1f".format(it) } ?: "–",
                    "Moyenne · %.0f km".format(s.tripKm),
                    CarArt.sparkline(s),
                )
            )
            .build()
        val stop = Action.Builder().setTitle("Arrêter").setOnClickListener { telemetry.disconnect() }.build()
        return grid.setSingleList(tiles).setActionStrip(ActionStrip.Builder().addAction(stop).build()).build()
    }

    private fun tile(title: String, text: String, image: Bitmap, onClick: (() -> Unit)? = null) = GridItem.Builder()
        .setTitle(title)
        .setText(text)
        .setImage(CarIcon.Builder(IconCompat.createWithBitmap(image)).build(), GridItem.IMAGE_TYPE_LARGE)
        .apply { onClick?.let { setOnClickListener(it) } }
        .build()

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
