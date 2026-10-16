package tech.dm.conso.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import tech.dm.conso.data.LiveState
import tech.dm.conso.data.Sample
import tech.dm.conso.ui.Palette

private const val TREND_WINDOW_S = 300
private const val BUCKET_S = 10
private const val BUCKETS = TREND_WINDOW_S / BUCKET_S

fun trendPoints(samples: List<Sample>): List<Double?> =
    samples.takeLast(TREND_WINDOW_S).chunked(BUCKET_S).map { bucket ->
        val moving = bucket.filter { it.speedKmh >= 5 && it.litersPerHour != null }
        if (moving.isEmpty()) null
        else (moving.sumOf { it.litersPerHour!! } / moving.sumOf { it.speedKmh } * 100).coerceAtMost(30.0)
    }

fun carTips(s: LiveState): List<String> = buildList {
    s.gear?.let { g ->
        when {
            g.targetGear > g.currentGear -> add("Passez la ${g.targetGear}e")
            g.targetGear < g.currentGear -> add("Rétrogradez en ${g.targetGear}e")
        }
    }
    s.speedLimitKmh?.let { limit -> if (s.speedKmh > limit + 3) add("Limité à $limit km/h") }
    s.eco?.message?.let(::add)
}

fun renderTrend(points: List<Double?>, average: Double?, width: Int = 400, height: Int = 225): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pad = height * 0.08f
    val values = points.filterNotNull()
    val top = maxOf(10.0, (values.maxOrNull() ?: 0.0) * 1.15, (average ?: 0.0) * 1.3)
    fun x(i: Int) = pad + (width - 2 * pad) * i / (BUCKETS - 1).toFloat()
    fun y(v: Double) = (height - pad) - (height - 2 * pad) * (v / top).toFloat()

    val grid = Paint().apply { color = Palette.Outline.toArgb(); strokeWidth = 2f }
    for (v in 5..top.toInt() step 5) canvas.drawLine(pad, y(v.toDouble()), width - pad, y(v.toDouble()), grid)

    val offset = BUCKETS - points.size
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    var previous: Pair<Float, Float>? = null
    points.forEachIndexed { i, v ->
        if (v == null) { previous = null; return@forEachIndexed }
        val p = x(i + offset) to y(v)
        previous?.let {
            line.color = Palette.forConsumption(v).toArgb()
            canvas.drawLine(it.first, it.second, p.first, p.second, line)
        }
        previous = p
    }

    average?.let {
        val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Text.toArgb(); style = Paint.Style.STROKE; strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        canvas.drawPath(Path().apply { moveTo(pad, y(it)); lineTo(width - pad, y(it)) }, dashed)
    }
    return bitmap
}
