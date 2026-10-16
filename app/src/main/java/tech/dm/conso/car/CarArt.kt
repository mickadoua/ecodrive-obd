package tech.dm.conso.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import tech.dm.conso.data.LiveState
import tech.dm.conso.data.Sample
import tech.dm.conso.ui.Palette

private const val TREND_WINDOW_S = 300
private const val BUCKET_S = 10

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

object CarArt {
    private fun canvas(size: Int): Pair<Bitmap, Canvas> =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).let { it to Canvas(it) }

    private fun stroke(width: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    fun consoGauge(l100: Double?, score: Double, size: Int = 256): Bitmap {
        val (b, c) = canvas(size)
        val w = size * 0.12f
        val oval = RectF(w, w, size - w, size - w)
        c.drawArc(oval, 135f, 270f, false, stroke(w, Palette.Outline.toArgb()))
        val fraction = ((l100 ?: 0.0) / 20.0).toFloat().coerceIn(0.02f, 1f)
        c.drawArc(oval, 135f, 270f * fraction, false, stroke(w, Palette.forScore(score).toArgb()))
        return b
    }

    fun scoreRing(score: Double, size: Int = 256): Bitmap {
        val (b, c) = canvas(size)
        val w = size * 0.12f
        val oval = RectF(w, w, size - w, size - w)
        c.drawArc(oval, 0f, 360f, false, stroke(w, Palette.Outline.toArgb()))
        c.drawArc(oval, -90f, 360f * (score / 100).toFloat(), false, stroke(w, Palette.forScore(score).toArgb()))
        return b
    }

    fun tipIcon(s: LiveState, size: Int = 256): Bitmap {
        val (b, c) = canvas(size)
        val m = size / 2f
        val g = s.gear
        val p = stroke(size * 0.1f, Palette.Text.toArgb())
        when {
            g != null && g.targetGear != g.currentGear -> {
                val dir = if (g.targetGear > g.currentGear) -1 else 1
                p.color = Palette.Blue.toArgb()
                c.drawLine(m, m - dir * size * 0.3f, m, m + dir * size * 0.3f, p)
                c.drawLine(m, m + dir * size * 0.3f, m - size * 0.2f, m + dir * size * 0.1f, p)
                c.drawLine(m, m + dir * size * 0.3f, m + size * 0.2f, m + dir * size * 0.1f, p)
            }
            s.speedLimitKmh?.let { s.speedKmh > it + 3 } == true -> {
                p.color = Palette.Red.toArgb()
                c.drawCircle(m, m, size * 0.36f, p)
            }
            (s.eco?.score ?: 100.0) >= 75 -> {
                p.color = Palette.Green.toArgb()
                c.drawPath(Path().apply { moveTo(size * 0.22f, m); lineTo(size * 0.42f, size * 0.7f); lineTo(size * 0.78f, size * 0.3f) }, p)
            }
            else -> {
                p.color = Palette.Yellow.toArgb()
                c.drawLine(m, size * 0.22f, m, size * 0.6f, p)
                c.drawPoint(m, size * 0.78f, p)
            }
        }
        return b
    }

    fun sparkline(s: LiveState, size: Int = 256): Bitmap {
        val (b, c) = canvas(size)
        val pts = trendPoints(s.samples)
        val top = maxOf(10.0, (pts.filterNotNull().maxOrNull() ?: 0.0) * 1.1)
        drawCurve(c, pts, top, size * 0.1f, size * 0.9f, size.toFloat(), stroke(size * 0.05f, Palette.Blue.toArgb()))
        return b
    }

    private fun drawCurve(c: Canvas, pts: List<Double?>, top: Double, high: Float, low: Float, width: Float, line: Paint) {
        val points = pts.mapIndexedNotNull { i, v ->
            v?.let { width * 0.94f * i / (pts.size - 1).coerceAtLeast(1).toFloat() to low - (low - high) * (it / top).toFloat().coerceIn(0f, 1f) }
        }
        if (points.size < 2) return
        val curve = Path().apply {
            moveTo(points[0].first, points[0].second)
            points.zipWithNext().forEach { (p, q) ->
                val mx = (p.first + q.first) / 2
                cubicTo(mx, p.second, mx, q.second, q.first, q.second)
            }
        }
        c.drawPath(curve, line)
    }
}
