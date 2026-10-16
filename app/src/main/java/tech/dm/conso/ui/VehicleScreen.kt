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
import tech.dm.conso.data.Vehicle
import tech.dm.conso.data.VehicleProfile
import tech.dm.conso.obd.FuelType
import tech.dm.conso.obd.Powertrain
import tech.dm.conso.obd.VinInfo
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(
    vehicles: List<Vehicle>,
    active: Vehicle,
    canSwitch: Boolean,
    onSelect: (Vehicle) -> Unit,
    onAdd: () -> Unit,
    onRename: (Vehicle) -> Unit,
    profile: VehicleProfile,
    detected: VinInfo?,
    onSave: (VehicleProfile) -> Unit,
    autoStart: @Composable () -> Unit,
) {
    val draft = profile
    var yearText by remember(active.id) { mutableStateOf(profile.year?.toString() ?: "") }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Véhicule", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text("Sert au calcul de la consommation attendue et à la comparaison entre modèles équivalents.", color = Palette.Muted, fontSize = 13.sp)

        Card {
            Text("Mes véhicules", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vehicles.forEach { v -> Chip(v.name, v.id == active.id) { if (canSwitch) onSelect(v) } }
                if (canSwitch) Chip("+ Ajouter", false, onAdd)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(active.name, { onRename(active.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("Nom du véhicule actif") }, singleLine = true)
            Spacer(Modifier.height(6.dp))
            Text(
                if (canSwitch) "Reconnu à la connexion par le VIN, sinon par le module OBD. Historique, carte et classement suivent le véhicule actif."
                else "Changement de véhicule possible une fois déconnecté.",
                color = Palette.Muted, fontSize = 11.sp,
            )
        }

        if (detected?.make != null) {
            Card {
                Text("Détecté via OBD", color = Palette.Muted, fontSize = 12.sp)
                Text(
                    listOfNotNull(detected.make, detected.modelYear?.toString()).joinToString(" · "),
                    color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Chip("Utiliser", true) {
                    onSave(draft.copy(make = detected.make, year = detected.modelYear ?: draft.year))
                    yearText = (detected.modelYear ?: draft.year)?.toString() ?: ""
                }
                Spacer(Modifier.height(6.dp))
                Text("Le numéro VIN n'est ni stocké ni envoyé : seuls la marque, l'année et une empreinte irréversible servant à reconnaître la voiture restent sur le téléphone.", color = Palette.Muted, fontSize = 11.sp)
            }
        }

        Card {
            OutlinedTextField(draft.make, { onSave(draft.copy(make = it)) }, Modifier.fillMaxWidth(), label = { Text("Marque") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(draft.model, { onSave(draft.copy(model = it)) }, Modifier.fillMaxWidth(), label = { Text("Modèle et motorisation") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                yearText,
                { yearText = it.filter(Char::isDigit).take(4); onSave(draft.copy(year = yearText.toIntOrNull())) },
                Modifier.fillMaxWidth(), label = { Text("Année") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Card {
            Text("Motorisation", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Powertrain.entries.forEach { p -> Chip(p.label, draft.powertrain == p) { onSave(draft.copy(powertrain = p)) } }
            }
            Text(
                when (draft.powertrain) {
                    Powertrain.ICE -> "Si le véhicule déclare une motorisation hybride ou électrique (PID 51), elle est retenue."
                    Powertrain.HYBRID -> "Consommation de carburant en L/100 km, moteur thermique coupé compté comme roulage électrique."
                    Powertrain.ELECTRIC -> "Énergie en kWh/100 km, estimée par le modèle physique : la plupart des électriques n'exposent leur puissance batterie que par des PIDs constructeur."
                },
                color = Palette.Muted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))
            if (draft.powertrain != Powertrain.ELECTRIC) {
                Text("Carburant", color = Palette.Text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Essence", draft.fuel == FuelType.GASOLINE) { onSave(draft.copy(fuel = FuelType.GASOLINE)) }
                    Chip("Diesel", draft.fuel == FuelType.DIESEL) { onSave(draft.copy(fuel = FuelType.DIESEL)) }
                }
                Spacer(Modifier.height(14.dp))
                Text("Cylindrée : %.1f L".format(draft.displacementL), color = Palette.Text, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = draft.displacementL.toFloat(),
                    onValueChange = { onSave(draft.copy(displacementL = (it * 10).roundToInt() / 10.0)) },
                    valueRange = 0.8f..3.0f,
                    steps = 21,
                    colors = SliderDefaults.colors(thumbColor = Palette.Green, activeTrackColor = Palette.Green),
                )
                Text("Utilisée si la voiture n'a pas de débitmètre d'air.", color = Palette.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))
            }
            Text("Catégorie", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CarSegment.entries.forEach { s -> Chip(s.label, draft.segment == s) { onSave(draft.copy(segment = s)) } }
            }
        }

        Text("Enregistré automatiquement", color = Palette.Muted, fontSize = 12.sp)
        autoStart()
        Spacer(Modifier.height(16.dp))
    }
}
