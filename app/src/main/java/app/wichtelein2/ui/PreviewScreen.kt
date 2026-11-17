package app.wichtelein2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.audio.AudioPlayer
import app.wichtelein2.core.MusicChoice
import app.wichtelein2.core.Mixdown
import app.wichtelein2.core.Plan
import app.wichtelein2.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.wichtelein2.core.Simulation
import app.wichtelein2.render.Renderer
import app.wichtelein2.render.SpriteLibrary

@OptIn(ExperimentalLayoutApi::class)
/** Spielt den berechneten Ablauf live ab (genau die Bilder, die später im Video landen). */
@Composable
fun PreviewScreen(
    plan: Plan,
    photo: Bitmap,
    sprites: SpriteLibrary,
    customInterior: Bitmap?,
    sim: Simulation,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val renderer = remember(plan, photo, customInterior) { Renderer(plan, photo, sprites, customInterior) }
    var t by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }

    // Ton: einmal mischen, dann ab der aktuellen Stelle abspielen (Pause, Springen und Neustart starten ihn neu)
    val audio = remember { AudioPlayer() }
    DisposableEffect(Unit) { onDispose { audio.stop() } }
    val mix by produceState<FloatArray?>(null, sim, settings.soundFx, settings.music, settings.fxVolume, settings.musicVolume) {
        value = withContext(Dispatchers.Default) {
            Mixdown.mix(sim, settings.soundFx, MusicChoice.track(settings.music), settings.fxVolume, settings.musicVolume)
        }
    }
    var restartCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing, muted, mix, restartCount) {
        val m = mix
        if (playing && !muted && m != null) audio.play(m, t) else audio.stop()
    }

    LaunchedEffect(playing, sim) {
        if (!playing) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            t += (now - last) / 1_000_000_000f
            last = now
            if (t >= sim.duration) {
                t = sim.duration - 0.001f
                playing = false
                break
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.aspectRatio(plan.width.toFloat() / plan.height.toFloat()).clipToBounds().testTag("preview_canvas")) {
                val k = size.width / plan.width.toFloat()
                val frame = sim.at(t)
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(k, k)
                    renderer.draw(nc, frame)
                    nc.restore()
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Slider(
                value = (t / sim.duration).coerceIn(0f, 1f),
                onValueChange = { t = it * sim.duration; playing = false },
                modifier = Modifier.testTag("preview_slider"),
            )
            Text(
                "%.1f / %.1f s".format(t, sim.duration),
                fontSize = 12.sp,
                modifier = Modifier.testTag("preview_time"),
            )
            if (sim.warnings.isNotEmpty()) {
                Text(sim.warnings.distinct().joinToString("\n"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!playing && t >= sim.duration - 0.01f) t = 0f
                        playing = !playing
                    },
                    modifier = Modifier.testTag("preview_play"),
                ) { Text(if (playing) "❚❚ Pause" else "▶ Abspielen") }
                OutlinedButton(onClick = { muted = !muted }, modifier = Modifier.testTag("preview_mute")) { Text(if (muted) "🔇 Ton aus" else "🔊 Ton an") }
                OutlinedButton(onClick = { t = 0f; playing = true; restartCount++ }, modifier = Modifier.testTag("preview_restart")) { Text("Von vorn") }
                OutlinedButton(onClick = onClose, modifier = Modifier.testTag("preview_close")) { Text("Schließen") }
            }
        }
    }
}
