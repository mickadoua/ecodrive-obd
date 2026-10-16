package tech.dm.conso.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import tech.dm.conso.ConsoApp
import tech.dm.conso.R
import tech.dm.conso.auto.AutoStartStore
import tech.dm.conso.car.TipAlert
import tech.dm.conso.data.ConnectionStatus
import tech.dm.conso.data.LiveState
import tech.dm.conso.data.Vehicle
import tech.dm.conso.ui.MainActivity

class ObdService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trajet en cours", NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            ConsoApp.instance.telemetry.state.sample(2_000).collect { s ->
                if (s.status == ConnectionStatus.DISCONNECTED || (s.status == ConnectionStatus.ERROR && !s.retrying)) {
                    stopSelf()
                } else {
                    nm.notify(ID, build(s))
                    if (s.status == ConnectionStatus.CONNECTED) TipAlert.update(this@ObdService, s)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val auto = intent?.action == ACTION_AUTO
        goForeground(fromBackground = auto)
        if (auto) {
            val store = AutoStartStore(this)
            val source = store.obdSource(this, intent.getStringExtra(EXTRA_OBD), intent.getStringExtra(EXTRA_OBD_NAME))
                ?: store.savedObdSource(this)
            if (source == null) stopSelf() else ConsoApp.instance.telemetry.connect(source, retry = true)
        }
        return START_NOT_STICKY
    }

    private fun goForeground(fromBackground: Boolean) {
        val notification = build(ConsoApp.instance.telemetry.state.value)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(ID, notification)
            return
        }
        val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (!fromBackground || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            (if (location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        startForeground(ID, notification, types)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(s: LiveState): Notification {
        val text = when {
            s.unknownVehicle != null -> "Véhicule inconnu : ouvrez l'app pour l'identifier"
            s.status == ConnectionStatus.CONNECTING -> "Connexion à ${s.sourceLabel}…"
            s.retrying -> "Module OBD injoignable, nouvelle tentative…"
            s.litersPer100 != null -> "%.1f %s · moy. %s".format(s.litersPer100, s.powertrain.per100Unit, s.tripAvgL100?.let { "%.1f".format(it) } ?: "–")
            s.litersPerHour != null -> "%.1f %s à l'arrêt".format(s.litersPerHour, s.powertrain.rateUnit)
            else -> "Connecté"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Conso")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "trip"
        private const val ID = 1

        private const val ACTION_AUTO = "tech.dm.conso.AUTO_START"
        private const val EXTRA_OBD = "obd"
        private const val EXTRA_OBD_NAME = "obdName"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ObdService::class.java))
        }

        fun startAuto(context: Context, vehicle: Vehicle) {
            context.startForegroundService(
                Intent(context, ObdService::class.java).setAction(ACTION_AUTO)
                    .putExtra(EXTRA_OBD, vehicle.obdAddress)
                    .putExtra(EXTRA_OBD_NAME, vehicle.obdName)
            )
        }
    }
}
