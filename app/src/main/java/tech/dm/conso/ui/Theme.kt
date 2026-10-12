package tech.dm.conso.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

object Palette {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF141A22)
    val SurfaceHigh = Color(0xFF1C2430)
    val Outline = Color(0xFF263040)
    val Text = Color(0xFFE8EDF3)
    val Muted = Color(0xFF8A96A8)
    val Green = Color(0xFF3DDC97)
    val Yellow = Color(0xFFFFC857)
    val Red = Color(0xFFFF5D73)
    val Blue = Color(0xFF5AA9FF)

    fun forScore(score: Double): Color = when {
        score >= 75 -> lerp(Yellow, Green, ((score - 75) / 25).toFloat().coerceIn(0f, 1f))
        else -> lerp(Red, Yellow, (score / 75).toFloat().coerceIn(0f, 1f))
    }

    fun forConsumption(l100: Double): Color = forScore(100 - (l100 - 4) * 10)
}

@Composable
fun ConsoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Green,
            background = Palette.Background,
            surface = Palette.Surface,
            surfaceVariant = Palette.SurfaceHigh,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
            onSurfaceVariant = Palette.Muted,
            outline = Palette.Outline,
        ),
        content = content,
    )
}
