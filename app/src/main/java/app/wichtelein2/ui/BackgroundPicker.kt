package app.wichtelein2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.wichtelein2.core.Backgrounds
import app.wichtelein2.core.BgDesign
import app.wichtelein2.core.MusicTrack
import app.wichtelein2.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Anzeigename eines Startbilds (eingebaut: Name aus den Daten, eigene: „Eigenes Bild n“). */
fun backgroundLabel(id: String, designs: List<BgDesign>, settings: AppSettings): String =
    designs.firstOrNull { it.id == id }?.label ?: ("Eigenes Bild " + (settings.customBgs.indexOf(id) + 1).coerceAtLeast(1))

/**
 * Auswahl des Startbilds mit Vorschaubildern und die Musik dazu: aus den eingebauten Weihnachtsmelodien, aus einer eigenen
 * Musikdatei oder aus. Eigene Bilder lassen sich hinzufügen und wieder löschen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundPickerDialog(
    designs: List<BgDesign>,
    settings: AppSettings,
    loadThumb: (String) -> Bitmap?,
    hasMusicFile: (String) -> Boolean,
    onChange: (AppSettings) -> Unit,
    onAddImage: () -> Unit,
    onPickMusic: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val id = settings.bgId
    val code = Backgrounds.musicFor(settings.bgMusic, id, designs)
    fun setMusic(c: String) = onChange(settings.copy(bgMusic = settings.bgMusic + (id to c)))
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.95f).testTag("bg_picker"),
        ) {
            Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Hintergrund und Musik", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Wähle das Bild der Startseite. Zu jedem Bild gehört eine eigene Musik.", fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    designs.forEach { d ->
                        Thumb(d.id, d.label, id == d.id, loadThumb) { onChange(settings.copy(bgId = d.id)) }
                    }
                    settings.customBgs.forEach { c ->
                        Thumb(c, backgroundLabel(c, designs, settings), id == c, loadThumb) { onChange(settings.copy(bgId = c)) }
                    }
                    Box(
                        Modifier.size(width = 100.dp, height = 178.dp).clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
                            .clickable { onAddImage() }.testTag("bg_add_image"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ Eigenes Bild", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Musik zu „" + backgroundLabel(id, designs, settings) + "“", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(code == Backgrounds.MUSIC_OFF, { setMusic(Backgrounds.MUSIC_OFF) }, { Text("Aus") }, modifier = Modifier.testTag("bg_music_off"))
                    MusicTrack.entries.forEach { t ->
                        val c = Backgrounds.trackCode(t)
                        FilterChip(code == c, { setMusic(c) }, { Text(t.label) }, modifier = Modifier.testTag("bg_music_$c"))
                    }
                    FilterChip(
                        code == Backgrounds.MUSIC_FILE,
                        { if (hasMusicFile(id)) setMusic(Backgrounds.MUSIC_FILE) else onPickMusic(id) },
                        { Text("Eigene Datei") },
                        modifier = Modifier.testTag("bg_music_file"),
                    )
                }
                OutlinedButton(onClick = { onPickMusic(id) }, modifier = Modifier.testTag("bg_pick_music")) {
                    Text(if (hasMusicFile(id)) "Eigene Musikdatei ändern" else "Eigene Musikdatei wählen")
                }
                Text("Lautstärke", fontSize = 12.sp)
                Slider(value = settings.bgVolume, onValueChange = { onChange(settings.copy(bgVolume = it)) }, modifier = Modifier.testTag("bg_volume"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = settings.homeMusic, onCheckedChange = { onChange(settings.copy(homeMusic = it)) }, modifier = Modifier.testTag("bg_home_music"))
                    Spacer(Modifier.size(10.dp))
                    Text("Musik auf der Startseite spielen", fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (Backgrounds.isCustom(id)) {
                        OutlinedButton(onClick = { onDelete(id) }, modifier = Modifier.testTag("bg_delete")) { Text("Dieses Bild löschen") }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                    TextButton(onClick = onClose, modifier = Modifier.testTag("bg_close")) { Text("Fertig") }
                }
            }
        }
    }
}

/** Vorschaubild eines Startbilds mit Namen; das gewählte hat einen kräftigen Rahmen. */
@Composable
private fun Thumb(id: String, label: String, selected: Boolean, loadThumb: (String) -> Bitmap?, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, id) { value = withContext(Dispatchers.IO) { loadThumb(id) } }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(width = 100.dp, height = 178.dp).clip(shape).background(Color(0xFF1B2740))
            .border(BorderStroke(if (selected) 4.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray), shape)
            .clickable { onClick() }.testTag("bg_thumb_$id"),
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}
