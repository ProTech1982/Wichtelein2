package app.wichtelein2.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.core.Anim
import app.wichtelein2.core.MusicTrack
import app.wichtelein2.core.ViewMode
import app.wichtelein2.core.WichtelColors
import app.wichtelein2.data.AppSettings
import app.wichtelein2.render.SpriteLibrary

private val SWATCHES = listOf(
    0xFFD32F2F, 0xFFE64A19, 0xFFF9A825, 0xFF7CB342, 0xFF2E7D32, 0xFF00897B,
    0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41, 0xFF37474F, 0xFFF5F5F5, 0xFF212121,
).map { it.toInt() }

/** Einstellungen: Design (System/hell/dunkel), Farben des Wichtels, Update-Suche. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    sprites: SpriteLibrary,
    version: String,
    onChange: (AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) { Text("‹ Zurück") }
            Spacer(Modifier.size(12.dp))
            Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Text("Design", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wie das Handy", "Hell", "Dunkel").forEachIndexed { i, label ->
                FilterChip(
                    selected = settings.themeMode == i,
                    onClick = { onChange(settings.copy(themeMode = i)) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("theme_$i"),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("Farben des Wichtels", fontWeight = FontWeight.SemiBold)
        WichtelPreview(settings.colors, sprites)
        ColorRow("Mütze", "hat", settings.colors.hat) { onChange(settings.copy(colors = settings.colors.copy(hat = it))) }
        ColorRow("Jacke", "jacket", settings.colors.jacket) { onChange(settings.copy(colors = settings.colors.copy(jacket = it))) }
        ColorRow("Hose", "pants", settings.colors.pants) { onChange(settings.copy(colors = settings.colors.copy(pants = it))) }
        ColorRow("Schuhe", "shoes", settings.colors.shoes) { onChange(settings.copy(colors = settings.colors.copy(shoes = it))) }
        OutlinedButton(
            onClick = { onChange(settings.copy(colors = WichtelColors())) },
            modifier = Modifier.padding(top = 8.dp).testTag("colors_reset"),
        ) { Text("Alle Originalfarben") }
        Spacer(Modifier.height(20.dp))

        Text("Ton (Geräusche und Musik, der Wichtel spricht nicht)", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = settings.soundFx,
                onCheckedChange = { onChange(settings.copy(soundFx = it)) },
                modifier = Modifier.testTag("sound_fx"),
            )
            Spacer(Modifier.size(10.dp))
            Text("Geräusche (Schritte, Tür, Besen, Glöckchen)")
        }
        Text("Lautstärke Geräusche", fontSize = 12.sp)
        Slider(
            value = settings.fxVolume,
            onValueChange = { onChange(settings.copy(fxVolume = it)) },
            modifier = Modifier.testTag("fx_volume"),
        )
        Text("Musik", fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.music == 0,
                onClick = { onChange(settings.copy(music = 0)) },
                label = { Text("Aus") },
                modifier = Modifier.testTag("music_0"),
            )
            MusicTrack.entries.forEachIndexed { i, t ->
                FilterChip(
                    selected = settings.music == i + 1,
                    onClick = { onChange(settings.copy(music = i + 1)) },
                    label = { Text(t.label) },
                    modifier = Modifier.testTag("music_${i + 1}"),
                )
            }
        }
        Text("Lautstärke Musik", fontSize = 12.sp)
        Slider(
            value = settings.musicVolume,
            onValueChange = { onChange(settings.copy(musicVolume = it)) },
            modifier = Modifier.testTag("music_volume"),
        )
        Spacer(Modifier.height(20.dp))

        Text("App und Updates", fontWeight = FontWeight.SemiBold)
        Text("Version $version", modifier = Modifier.testTag("version_text"))
        Button(onClick = onCheckUpdate, modifier = Modifier.padding(top = 8.dp).testTag("btn_update")) { Text("Nach Updates suchen") }
    }
}

/** Vorschau: Wichtel von vorn in den gewählten Farben. */
@Composable
private fun WichtelPreview(colors: WichtelColors, sprites: SpriteLibrary) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(colors) {
        sprites.setColors(colors)
        val sheet = sprites.sheet(ViewMode.SIDE, Anim.IDLE)
        val c = sprites.meta.cell
        val out = Bitmap.createBitmap(c, c, Bitmap.Config.ARGB_8888)
        AndroidCanvas(out).drawBitmap(sheet, Rect(0, 0, c, c), RectF(0f, 0f, c.toFloat(), c.toFloat()), null)
        bmp = out
    }
    Box(Modifier.fillMaxWidth().height(180.dp).testTag("wichtel_preview"), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Canvas(Modifier.size(180.dp)) {
                drawIntoCanvas { it.nativeCanvas.drawBitmap(b, null, RectF(0f, 0f, size.width, size.height), null) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(label: String, tag: String, current: Int?, onPick: (Int?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = current == null,
                onClick = { onPick(null) },
                label = { Text("Original") },
                modifier = Modifier.testTag("color_${tag}_orig"),
            )
            SWATCHES.forEachIndexed { i, c ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                        .border(
                            BorderStroke(if (current == c) 3.dp else 1.dp, if (current == c) MaterialTheme.colorScheme.primary else Color.Gray),
                            CircleShape,
                        )
                        .clickable { onPick(c) }
                        .testTag("color_${tag}_$i"),
                )
            }
        }
        var hue by remember { mutableStateOf(0f) }
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                onPick(android.graphics.Color.HSVToColor(floatArrayOf(it * 360f, 0.85f, 0.85f)))
            },
            modifier = Modifier.testTag("color_${tag}_hue"),
        )
    }
}
