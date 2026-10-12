package tech.dm.conso.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.CarSegment
import tech.dm.conso.data.VehicleProfile
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.VinInfo
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(
    profile: VehicleProfile,
    detected: VinInfo?,
    onSave: (VehicleProfile) -> Unit,
    autoStart: @Composable () -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(profile) }
    var yearText by remember(profile) { mutableStateOf(profile.year?.toString() ?: "") }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Véhicule", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text("Sert au calcul de la consommation attendue et à la comparaison entre modèles équivalents.", color = Palette.Muted, fontSize = 13.sp)

        if (detected?.make != null) {
            Card {
                Text("Détecté via OBD", color = Palette.Muted, fontSize = 12.sp)
                Text(
                    listOfNotNull(detected.make, detected.modelYear?.toString()).joinToString(" · "),
                    color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Chip("Utiliser", true) {
                    draft = draft.copy(make = detected.make, year = detected.modelYear ?: draft.year)
                    yearText = draft.year?.toString() ?: ""
                }
                Spacer(Modifier.height(6.dp))
                Text("Le numéro VIN n'est ni stocké ni envoyé : seuls la marque et l'année en sont déduites.", color = Palette.Muted, fontSize = 11.sp)
            }
        }

        Card {
            OutlinedTextField(draft.make, { draft = draft.copy(make = it) }, Modifier.fillMaxWidth(), label = { Text("Marque") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(draft.model, { draft = draft.copy(model = it) }, Modifier.fillMaxWidth(), label = { Text("Modèle et motorisation") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                yearText,
                { yearText = it.filter(Char::isDigit).take(4); draft = draft.copy(year = yearText.toIntOrNull()) },
                Modifier.fillMaxWidth(), label = { Text("Année") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Card {
            Text("Carburant", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Essence", draft.fuel == FuelType.GASOLINE) { draft = draft.copy(fuel = FuelType.GASOLINE) }
                Chip("Diesel", draft.fuel == FuelType.DIESEL) { draft = draft.copy(fuel = FuelType.DIESEL) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Cylindrée : %.1f L".format(draft.displacementL), color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Slider(
                value = draft.displacementL.toFloat(),
                onValueChange = { draft = draft.copy(displacementL = (it * 10).roundToInt() / 10.0) },
                valueRange = 0.8f..3.0f,
                steps = 21,
                colors = SliderDefaults.colors(thumbColor = Palette.Green, activeTrackColor = Palette.Green),
            )
            Text("Utilisée si la voiture n'a pas de débitmètre d'air.", color = Palette.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Text("Catégorie", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CarSegment.entries.forEach { s -> Chip(s.label, draft.segment == s) { draft = draft.copy(segment = s) } }
            }
        }

        Chip(if (draft == profile) "Enregistré" else "Enregistrer", draft != profile) { onSave(draft) }
        autoStart()
        Spacer(Modifier.height(16.dp))
    }
}
