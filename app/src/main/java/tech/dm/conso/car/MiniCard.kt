package tech.dm.conso.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService
import androidx.car.app.notification.CarAppExtender
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import tech.dm.conso.R
import tech.dm.conso.data.LiveState
import tech.dm.conso.ui.Palette

class ConsoMediaService : MediaBrowserService() {
    override fun onCreate() {
        super.onCreate()
        sessionToken = MiniCard.session(this).sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(mutableListOf())
    }
}

object MiniCard {
    private const val TIP_CHANNEL = "tips"
    private const val TIP_ID = 2
    private const val TIP_INTERVAL_MS = 60_000L

    private var session: MediaSession? = null
    private var lastTip: String? = null
    private var lastTipAt = 0L

    fun session(context: Context): MediaSession = session ?: MediaSession(context.applicationContext, "conso").also {
        it.setCallback(object : MediaSession.Callback() {
            override fun onStop() = stop()
        })
        session = it
    }

    fun update(context: Context, s: LiveState) {
        val tips = carTips(s)
        val title = (s.litersPer100?.let { "%.1f L/100".format(it) } ?: s.litersPerHour?.let { "%.1f L/h".format(it) } ?: "–") +
            (s.eco?.let { " · Éco %.0f".format(it.score) } ?: "")
        session(context).apply {
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, tips.firstOrNull() ?: "Conso")
                    .putBitmap(MediaMetadata.METADATA_KEY_ART, renderGauge(s.eco?.score))
                    .build()
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE)
                    .build()
            )
            isActive = true
        }
        tips.firstOrNull()?.let { tip -> if (tip != lastTip) alert(context, title, tip) }
    }

    fun stop() {
        session?.run {
            setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
            isActive = false
        }
    }

    private fun alert(context: Context, title: String, tip: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTipAt < TIP_INTERVAL_MS) return
        lastTip = tip
        lastTipAt = now
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(TIP_CHANNEL, "Conseils en voiture", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(context, TIP_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tip)
            .setContentText(title)
            .setTimeoutAfter(TIP_INTERVAL_MS)
            .extend(
                CarAppExtender.Builder()
                    .setContentTitle(tip)
                    .setContentText(title)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .build()
            )
            .build()
        runCatching { nm.notify(TIP_ID, notification) }
    }

    private fun renderGauge(score: Double?, size: Int = 320): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Palette.Background.toArgb())
        val stroke = size * 0.11f
        val oval = RectF(stroke, stroke, size - stroke, size - stroke)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        paint.color = Palette.Outline.toArgb()
        canvas.drawArc(oval, 135f, 270f, false, paint)
        score?.let {
            paint.color = Palette.forScore(it).toArgb()
            canvas.drawArc(oval, 135f, 270f * (it / 100).toFloat().coerceIn(0.02f, 1f), false, paint)
        }
        return bitmap
    }
}
