package app.wichtelein2.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.core.UpdateCheck

/** Ergebnis der Update-Suche, wie es das Fenster zeigt. */
sealed interface UpdateDialogState {
    object Checking : UpdateDialogState
    class Error(val message: String) : UpdateDialogState
    class UpToDate(val version: String?, val notes: String?) : UpdateDialogState
    class Available(val version: String?, val notes: String?, val url: String) : UpdateDialogState
}

fun updateDialogFrom(r: UpdateCheck.Result): UpdateDialogState = when {
    r.error != null -> UpdateDialogState.Error(r.error)
    r.available && r.downloadUrl != null -> UpdateDialogState.Available(r.version, r.notes, r.downloadUrl)
    else -> UpdateDialogState.UpToDate(r.version, r.notes)
}

/**
 * Update-Fenster: bei neuer Version mit Änderungsprotokoll und „Jetzt updaten“ / „Abbrechen“ (der Download startet erst
 * nach dem Tippen). Ist die App aktuell, zeigt es das Änderungsprotokoll der aktuellen Version.
 */
@Composable
fun UpdateDialog(state: UpdateDialogState?, onUpdate: (String) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        null -> Unit
        UpdateDialogState.Checking -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Suche nach Updates …") },
            text = { Text("Einen Moment bitte.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
            modifier = Modifier.testTag("update_dialog"),
        )
        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update-Suche fehlgeschlagen") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            modifier = Modifier.testTag("update_dialog"),
        )
        is UpdateDialogState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Du hast die neueste Version") },
            text = { Notes(state.version, state.notes, "Änderungen dieser Version") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            modifier = Modifier.testTag("update_dialog"),
        )
        is UpdateDialogState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Neues Update verfügbar") },
            text = { Notes(state.version, state.notes, "Änderungen") },
            confirmButton = {
                TextButton(onClick = { onUpdate(state.url) }, modifier = Modifier.testTag("update_now")) { Text("Jetzt updaten") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
            modifier = Modifier.testTag("update_dialog"),
        )
    }
}

@Composable
private fun Notes(version: String?, notes: String?, heading: String) {
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        if (!version.isNullOrBlank()) Text(version, fontWeight = FontWeight.SemiBold)
        Text(heading, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(UpdateCheck.cleanNotes(notes), fontSize = 13.sp)
    }
}
