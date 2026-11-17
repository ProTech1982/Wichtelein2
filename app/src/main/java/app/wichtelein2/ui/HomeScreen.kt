package app.wichtelein2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Startseite: Foto wählen oder aufnehmen, letztes Projekt fortsetzen, Einstellungen. */
@Composable
fun HomeScreen(
    version: String,
    hasProject: Boolean,
    busy: Boolean,
    message: String?,
    updateHint: String?,
    onPick: () -> Unit,
    onTake: () -> Unit,
    onContinue: () -> Unit,
    onAr: () -> Unit,
    onSettings: () -> Unit,
    onUpdateHint: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Wichtelein2", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Der Wichtel in deinem Foto", fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("Version $version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        if (updateHint != null) {
            OutlinedButton(onClick = onUpdateHint, modifier = Modifier.fillMaxWidth().testTag("update_hint")) { Text(updateHint) }
            Spacer(Modifier.height(12.dp))
        }
        Button(onClick = onPick, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_pick_photo")) { Text("Foto aus der Galerie") }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onTake, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_take_photo")) { Text("Foto aufnehmen") }
        if (hasProject) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onContinue, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_continue")) {
                Text("Mit dem letzten Projekt weitermachen")
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onAr, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_ar")) { Text("AR-Wichtel (Kamera, echter Raum)") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().testTag("btn_settings")) { Text("Einstellungen") }
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("home_message"))
        }
    }
}
