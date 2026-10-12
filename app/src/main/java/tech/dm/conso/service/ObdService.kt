package tech.dm.conso.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import tech.dm.conso.data.ConnectionStatus
import tech.dm.conso.data.LiveState
import tech.dm.conso.ui.MainActivity

class ObdService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trajet en cours", NotificationManager.IMPORTANCE_LOW))
        val first = build(ConsoApp.instance.telemetry.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, first)
        }
        scope.launch {
            ConsoApp.instance.telemetry.state.sample(2_000).collect { s ->
                if (s.status == ConnectionStatus.DISCONNECTED || s.status == ConnectionStatus.ERROR) {
                    stopSelf()
                } else {
                    nm.notify(ID, build(s))
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(s: LiveState): Notification {
        val text = when {
            s.status == ConnectionStatus.CONNECTING -> "Connexion à ${s.sourceLabel}…"
            s.litersPer100 != null -> "%.1f L/100 km · moy. %s".format(s.litersPer100, s.tripAvgL100?.let { "%.1f".format(it) } ?: "–")
            s.litersPerHour != null -> "%.1f L/h à l'arrêt".format(s.litersPerHour)
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ObdService::class.java))
        }
    }
}
