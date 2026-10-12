package tech.dm.conso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.DayStats
import tech.dm.conso.data.Sample
import tech.dm.conso.obd.FuelCalculator
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ArcGauge(fraction: Float, color: Color, modifier: Modifier = Modifier, strokeWidth: Float = 28f) {
    Canvas(modifier) {
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        drawArc(Palette.SurfaceHigh, 135f, 270f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        drawArc(
            Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
            135f, 270f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun LiveChart(samples: List<Sample>, modifier: Modifier = Modifier) {
    val points = samples.map { s ->
        Triple(s.timeMs, s.litersPerHour?.let { FuelCalculator.per100km(it, s.speedKmh) }, s.altitudeM)
    }
    val altitudes = points.mapNotNull { it.third }
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val t0 = points.first().first.toFloat()
        val span = (points.last().first - points.first().first).coerceAtLeast(1).toFloat()
        fun x(t: Long) = (t - t0) / span * size.width

        if (altitudes.size >= 2) {
            val minA = altitudes.min()
            val rangeA = (altitudes.max() - minA).coerceAtLeast(10.0)
            val area = Path()
            var started = false
            points.forEach { (t, _, a) ->
                if (a == null) return@forEach
                val y = size.height - ((a - minA) / rangeA * size.height * 0.45).toFloat()
                if (!started) { area.moveTo(x(t), size.height); started = true }
                area.lineTo(x(t), y)
            }
            area.lineTo(x(points.last().first), size.height)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(Palette.Blue.copy(alpha = 0.45f), Palette.Blue.copy(alpha = 0.05f))))
        }

        val maxC = 20.0
        for (level in listOf(5.0, 10.0, 15.0)) {
            val y = size.height - (level / maxC * size.height).toFloat()
            drawLine(Palette.Outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val path = Path()
        var started = false
        points.forEach { (t, c, _) ->
            if (c == null) { started = false; return@forEach }
            val y = size.height - (c.coerceAtMost(maxC) / maxC * size.height).toFloat()
            if (!started) { path.moveTo(x(t), y); started = true } else path.lineTo(x(t), y)
        }
        drawPath(
            path,
            Brush.verticalGradient(listOf(Palette.Red, Palette.Yellow, Palette.Green), 0f, size.height),
            style = Stroke(5f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun DaysBarChart(days: List<DayStats>, modifier: Modifier = Modifier) {
    val maxV = (days.mapNotNull { it.avgL100 }.maxOrNull() ?: 10.0).coerceAtLeast(8.0)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.avgL100?.let { "%.1f".format(it) } ?: "",
                        color = Palette.Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val v = d.avgL100 ?: return@Canvas
                            val h = (v / maxV * size.height).toFloat()
                            val w = size.width * 0.5f
                            val color = d.ecoScore?.let(Palette::forScore) ?: Palette.forConsumption(v)
                            drawRoundRect(
                                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)), size.height - h, size.height),
                                Offset((size.width - w) / 2, size.height - h), Size(w, h), CornerRadius(12f, 12f),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).replaceFirstChar(Char::uppercase),
                    Modifier.weight(1f), color = Palette.Muted, fontSize = 12.sp,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
