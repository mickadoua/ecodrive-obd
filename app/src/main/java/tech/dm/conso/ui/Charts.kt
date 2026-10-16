package tech.dm.conso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.DayStats
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
fun DaysBarChart(days: List<DayStats>, modifier: Modifier = Modifier, onDayClick: (DayStats) -> Unit = {}) {
    val powertrain = LocalPowertrain.current
    val maxV = (days.mapNotNull { it.avgL100 }.maxOrNull() ?: 10.0).coerceAtLeast(8.0)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                Column(
                    Modifier.weight(1f).clickable(enabled = d.distanceKm > 0.1) { onDayClick(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.avgL100?.let { "%.1f".format(it) } ?: "",
                        color = Palette.Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val v = d.avgL100 ?: return@Canvas
                            val h = (v / maxV * size.height).toFloat()
                            val w = size.width * 0.5f
                            val color = d.ecoScore?.let(Palette::forScore) ?: Palette.forConsumption(v, powertrain)
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
