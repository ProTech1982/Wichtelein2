package app.wichtelein2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.wichtelein2.audio.BackgroundMusic
import app.wichtelein2.core.Backgrounds
import app.wichtelein2.core.BgDesign
import app.wichtelein2.data.AppSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TITLE_SHADOW = Shadow(Color.Black, Offset(2f, 3f), 8f)

@Composable
private fun homeOutlinedColors() = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.38f), contentColor = Color.White)

private val HOME_BORDER = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f))

/**
 * Startseite: weihnachtliches Hintergrundbild mit sanfter Bewegung und Musik (Bild und Musik wählbar), Foto wählen oder
 * aufnehmen, letztes Projekt fortsetzen, AR, Einstellungen.
 */
@Composable
fun HomeScreen(
    version: String,
    hasProject: Boolean,
    busy: Boolean,
    message: String?,
    updateHint: String?,
    settings: AppSettings,
    designs: List<BgDesign>,
    loadBackground: (String, Int) -> Bitmap?,
    musicFile: (String) -> File?,
    musicVersion: Int,
    onChange: (AppSettings) -> Unit,
    onAddBackground: () -> Unit,
    onPickBackgroundMusic: (String) -> Unit,
    onDeleteBackground: (String) -> Unit,
    onPick: () -> Unit,
    onTake: () -> Unit,
    onContinue: () -> Unit,
    onAr: () -> Unit,
    onSettings: () -> Unit,
    onUpdateHint: () -> Unit,
) {
    // Hintergrund (bleibt beim Wechsel stehen, bis das neue Bild geladen ist)
    val bg by produceState<Bitmap?>(null, settings.bgId) { value = withContext(Dispatchers.IO) { loadBackground(settings.bgId, 1920) } ?: value }
    val design = designs.firstOrNull { it.id == settings.bgId }
    var picker by remember { mutableStateOf(false) }

    // Musik zum Bild: läuft nur auf der Startseite und solange die App im Vordergrund ist
    val music = remember { BackgroundMusic() }
    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    DisposableEffect(Unit) { onDispose { music.stop() } }
    val code = Backgrounds.musicFor(settings.bgMusic, settings.bgId, designs)
    LaunchedEffect(settings.bgId, code, settings.homeMusic, resumed, musicVersion) {
        if (settings.homeMusic && resumed && HomeFx.music && code != Backgrounds.MUSIC_OFF) {
            music.play(code, musicFile(settings.bgId), settings.bgVolume)
        } else {
            music.stop()
        }
    }
    LaunchedEffect(settings.bgVolume) { music.setVolume(settings.bgVolume) }

    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(bg, design)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Wichtelein2", style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = TITLE_SHADOW))
            Text("Der Wichtel in deinem Foto", style = TextStyle(fontSize = 16.sp, color = Color.White, shadow = TITLE_SHADOW))
            Spacer(Modifier.height(8.dp))
            Text("Version $version", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(28.dp))
            if (updateHint != null) {
                OutlinedButton(
                    onClick = onUpdateHint, colors = homeOutlinedColors(), border = HOME_BORDER,
                    modifier = Modifier.fillMaxWidth().testTag("update_hint"),
                ) { Text(updateHint) }
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = onPick, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_pick_photo")) { Text("Foto aus der Galerie") }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onTake, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_take_photo")) { Text("Foto aufnehmen") }
            if (hasProject) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onContinue, enabled = !busy, colors = homeOutlinedColors(), border = HOME_BORDER,
                    modifier = Modifier.fillMaxWidth().testTag("btn_continue"),
                ) { Text("Mit dem letzten Projekt weitermachen") }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAr, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("btn_ar")) { Text("AR-Wichtel (Kamera, echter Raum)") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSettings, colors = homeOutlinedColors(), border = HOME_BORDER,
                modifier = Modifier.fillMaxWidth().testTag("btn_settings"),
            ) { Text("Einstellungen") }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker = true }, colors = homeOutlinedColors(), border = HOME_BORDER,
                    modifier = Modifier.weight(1f).testTag("btn_backgrounds"),
                ) { Text("🎨 Hintergrund & Musik", maxLines = 1, fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { onChange(settings.copy(homeMusic = !settings.homeMusic)) }, colors = homeOutlinedColors(), border = HOME_BORDER,
                    modifier = Modifier.testTag("btn_home_music"),
                ) { Text(if (settings.homeMusic) "♪ An" else "♪ Aus", fontSize = 13.sp) }
            }
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        message, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("home_message"),
                    )
                }
            }
        }
    }
    if (picker) {
        BackgroundPickerDialog(
            designs = designs,
            settings = settings,
            loadThumb = { id -> loadBackground(id, 360) },
            hasMusicFile = { id -> musicFile(id) != null },
            onChange = onChange,
            onAddImage = onAddBackground,
            onPickMusic = onPickBackgroundMusic,
            onDelete = onDeleteBackground,
            onClose = { picker = false },
        )
    }
}
