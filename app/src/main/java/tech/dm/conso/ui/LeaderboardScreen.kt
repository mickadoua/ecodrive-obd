package tech.dm.conso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.VehicleProfile
import tech.dm.conso.leaderboard.ModelEntry
import tech.dm.conso.leaderboard.PortionStats

private val HEADINGS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

@Composable
fun LeaderboardScreen(
    portions: List<PortionStats>,
    models: List<ModelEntry>,
    profile: VehicleProfile,
    simulated: Boolean,
    onPortionClick: (String) -> Unit,
    onConfigureVehicle: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Classement", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text("Comparaison anonyme avec les autres conducteurs", color = Palette.Muted, fontSize = 13.sp)
            if (simulated) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Données de la communauté simulées en attendant le serveur",
                    Modifier.clip(RoundedCornerShape(50)).background(Palette.Yellow.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Palette.Yellow, fontSize = 12.sp,
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)); SectionTitle("Mes portions", "même tronçon, même sens") }
        if (portions.isEmpty()) {
            item { Text("Roulez un peu : chaque portion de ~150 m parcourue apparaîtra ici.", color = Palette.Muted, fontSize = 13.sp) }
        }
        itemsIndexed(portions.take(15)) { _, p -> PortionRow(p) { onPortionClick(p.key) } }

        item {
            Spacer(Modifier.height(8.dp))
            SectionTitle("Véhicules équivalents", "${profile.segment.label} · ${profile.energyLabel}")
        }
        if (!profile.isConfigured) {
            item {
                Card {
                    Text("Renseignez votre véhicule pour vous comparer aux modèles équivalents.", color = Palette.Text)
                    Spacer(Modifier.height(8.dp))
                    Chip("Configurer le véhicule", true, onConfigureVehicle)
                }
            }
        }
        itemsIndexed(models) { i, m -> ModelRow(i + 1, m) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PortionRow(p: PortionStats, onClick: () -> Unit) {
    val top = (100 - p.betterThanPct).coerceIn(1, 100)
    val color = when {
        top <= 25 -> Palette.Green
        top <= 60 -> Palette.Yellow
        else -> Palette.Red
    }
    val heading = HEADINGS.getOrElse(p.key.substringAfterLast('-').toIntOrNull() ?: 0) { "?" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Portion ${p.key.substringBefore('-').takeLast(4).uppercase()} · direction $heading",
                    color = Palette.Text, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "pente %+.1f %% · parcourue %d× · %d conducteurs".format(p.avgGrade * 100, p.segments.size, p.drivers),
                    color = Palette.Muted, fontSize = 12.sp,
                )
            }
            Text(
                "Top $top %",
                Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        val max = maxOf(p.myL100, p.communityMedianL100) * 1.15
        CompareBar("Vous", p.myL100, max, color)
        Spacer(Modifier.height(4.dp))
        CompareBar("Médiane", p.communityMedianL100, max, Palette.Muted)
    }
}

@Composable
private fun CompareBar(label: String, value: Double, max: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Palette.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth((value / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
        Text("%.1f".format(value), color = Palette.Text, fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 8.dp))
    }
}

@Composable
private fun ModelRow(rank: Int, m: ModelEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (m.isMe) Palette.Green.copy(alpha = 0.15f) else Palette.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", color = if (rank <= 3) Palette.Yellow else Palette.Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(m.label, color = if (m.isMe) Palette.Green else Palette.Text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!m.isMe) Text("${m.drivers} conducteurs", color = Palette.Muted, fontSize = 11.sp)
        }
        Text("%.1f".format(m.medianL100), color = Palette.Text, fontWeight = FontWeight.SemiBold)
        Text(" ${LocalPowertrain.current.per100Short}", color = Palette.Muted, fontSize = 11.sp)
    }
}
