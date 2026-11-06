package tech.dm.conso.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import tech.dm.conso.R
import tech.dm.conso.data.LiveState

fun drivingTips(s: LiveState): List<String> = buildList {
    s.gear?.let { g ->
        when {
            g.targetGear > g.currentGear -> add("Passez la ${g.targetGear}e")
            g.targetGear < g.currentGear -> add("Rétrogradez en ${g.targetGear}e")
        }
    }
    s.speedLimitKmh?.let { limit -> if (s.speedKmh > limit + 3) add("Limité à $limit km/h") }
    s.eco?.message?.let(::add)
}

object TipAlert {
    private const val TIP_CHANNEL = "tips"
    private const val TIP_ID = 2
    private const val TIP_INTERVAL_MS = 60_000L

    private var lastTip: String? = null
    private var lastTipAt = 0L

    fun update(context: Context, s: LiveState) {
        val tip = drivingTips(s).firstOrNull() ?: return
        if (tip == lastTip) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTipAt < TIP_INTERVAL_MS) return
        lastTip = tip
        lastTipAt = now
        val title = (s.litersPer100?.let { "%.1f L/100".format(it) } ?: s.litersPerHour?.let { "%.1f L/h".format(it) } ?: "–") +
            (s.eco?.let { " · Éco %.0f".format(it.score) } ?: "")
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(TIP_CHANNEL, "Conseils en voiture", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(context, TIP_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tip)
            .setContentText(title)
            .setTimeoutAfter(TIP_INTERVAL_MS)
            .build()
        runCatching { nm.notify(TIP_ID, notification) }
    }
}
