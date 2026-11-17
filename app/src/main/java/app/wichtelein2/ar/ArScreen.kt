package app.wichtelein2.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.wichtelein2.audio.AudioPlayer
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.ArSnapshot
import app.wichtelein2.core.MusicChoice
import app.wichtelein2.core.Mixdown
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Retreat
import app.wichtelein2.core.Simulation
import app.wichtelein2.core.Simulator
import app.wichtelein2.core.Step
import app.wichtelein2.data.AppSettings
import app.wichtelein2.render.SpriteLibrary
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class ArAction(val label: String) {
    ALL("Alles"),
    SWEEP("Fegen"),
    HIDE("Verstecken"),
    SCARE("Erschrecken"),
}

private fun stepsFor(a: ArAction): List<Step> = when (a) {
    ArAction.ALL -> listOf(
        Step(ActionType.SWEEP, seconds = 4f),
        Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 2f),
        Step(ActionType.SCARE, retreat = Retreat.DOOR),
    )
    ArAction.SWEEP -> listOf(Step(ActionType.SWEEP, seconds = 6f))
    ArAction.HIDE -> listOf(Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 3f))
    ArAction.SCARE -> listOf(Step(ActionType.SCARE, retreat = Retreat.DOOR))
}

/**
 * Echtes AR: Das Kamerabild kommt von ARCore, der Boden wird erkannt, ein Antippen stellt die Wichteltür auf den Boden.
 * Der Wichtel kommt heraus, fegt, versteckt sich oder erschrickt und geht wieder hinein, mit Geräuschen und Musik wie im Editor.
 * Ein Video aus dem AR-Modus gibt es nicht.
 */
@Composable
fun ArScreen(settings: AppSettings, sprites: SpriteLibrary, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var availability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var installRequested by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        var a = ArCoreApk.getInstance().checkAvailability(context)
        while (a.isTransient) {
            delay(250)
            a = ArCoreApk.getInstance().checkAvailability(context)
        }
        availability = a
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("ar_root")) {
        when {
            activity == null -> Message("AR ist hier nicht verfügbar.", onBack)
            !granted -> Message("Für AR braucht die App die Kamera.", onBack) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Kamera erlauben") }
            }
            availability == null -> Message("Prüfe, ob dein Handy AR kann …", onBack)
            availability == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArContent(activity, settings, sprites, onBack)
            availability!!.isSupported -> Message("Für AR muss „Google Play-Dienste für AR“ installiert oder aktualisiert werden.", onBack) {
                Button(onClick = {
                    try {
                        val r = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
                        installRequested = true
                        if (r == ArCoreApk.InstallStatus.INSTALLED) availability = ArCoreApk.Availability.SUPPORTED_INSTALLED
                    } catch (_: Exception) {
                    }
                }) { Text("Installieren") }
                OutlinedButton(onClick = { availability = ArCoreApk.getInstance().checkAvailability(context) }) { Text("Erneut prüfen", color = Color.White) }
            }
            else -> Message("Dieses Handy unterstützt kein AR (ARCore). Der normale Wichtel-Editor funktioniert trotzdem.", onBack)
        }
    }
}

@Composable
private fun Message(text: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AR-Wichtel", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp).testTag("ar_message"))
        extra()
        TextButton(onClick = onBack, modifier = Modifier.testTag("ar_back")) { Text("‹ Zurück", color = Color.White) }
    }
}

@Composable
private fun ArContent(activity: Activity, settings: AppSettings, sprites: SpriteLibrary, onBack: () -> Unit) {
    var snapshot by remember { mutableStateOf<ArSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val renderer = remember { ArRenderer(activity) { snapshot = it } }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }
    val drawer = remember(sprites) { ArDrawer(sprites) }

    // ARCore-Sitzung anlegen, steuern und wieder schließen
    var session by remember { mutableStateOf<Session?>(null) }
    DisposableEffect(Unit) {
        try {
            val s = Session(activity)
            s.configure(
                Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    focusMode = Config.FocusMode.AUTO
                },
            )
            session = s
            renderer.session = s
        } catch (t: Throwable) {
            error = "AR konnte nicht gestartet werden: ${t.message ?: t.javaClass.simpleName}"
        }
        onDispose {
            renderer.session = null
            session?.close()
            session = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        try {
            session?.resume()
            glView?.onResume()
        } catch (e: CameraNotAvailableException) {
            error = "Die Kamera wird gerade von einer anderen App benutzt."
        } catch (t: Throwable) {
            error = "AR konnte nicht fortgesetzt werden: ${t.message}"
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        glView?.onPause()
        session?.pause()
    }

    var action by remember { mutableStateOf(ArAction.ALL) }
    var doorMeters by remember { mutableFloatStateOf(0.30f) }
    var muted by remember { mutableStateOf(false) }
    val plan = remember(action) { drawer.planFor(stepsFor(action)) }
    var sim by remember { mutableStateOf<Simulation?>(null) }
    LaunchedEffect(plan) { sim = withContext(Dispatchers.Default) { Simulator(plan).run() } }

    // Ton: eine Schleife, die beim Platzieren von vorn beginnt
    val audio = remember { AudioPlayer() }
    DisposableEffect(Unit) { onDispose { audio.stop() } }
    var startNanos by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(-1L) }
    val placement = snapshot?.placement ?: 0
    LaunchedEffect(sim, placement, muted, settings.soundGroups, settings.music, settings.fxVolume, settings.musicVolume) {
        val s = sim ?: return@LaunchedEffect
        audio.stop()
        if (muted || placement == 0) return@LaunchedEffect
        val pcm = withContext(Dispatchers.Default) {
            Mixdown.mix(s, settings.soundGroups, MusicChoice.track(settings.music), settings.fxVolume, settings.musicVolume)
        } ?: return@LaunchedEffect
        startNanos = withFrameNanos { it }
        audio.play(pcm, 0f, loop = true)
    }
    LaunchedEffect(placement, sim) {
        startNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsedMs = if (placement > 0) (now - startNanos) / 1_000_000L else -1L
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("ar_back")) { Text("‹ Zurück", color = Color.White) }
            Text("AR-Wichtel", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        preserveEGLContextOnPause = true
                        setEGLContextClientVersion(2)
                        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        glView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val snap = snapshot
            val s = sim
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { o -> renderer.tap(o.x, o.y) }
                },
            ) {
                if (snap != null && s != null && snap.anchor != null && elapsedMs >= 0) {
                    val ms = elapsedMs % (s.duration * 1000f).toLong().coerceAtLeast(1000L)
                    val frame = s.at(ms / 1000f)
                    drawIntoCanvas { c -> drawer.draw(c.nativeCanvas, plan, snap, frame, doorMeters) }
                }
            }
            val hint = when {
                error != null -> error
                snapshot == null -> "Kamera startet …"
                snapshot?.tracking != true -> "Bewege das Handy langsam, damit die Umgebung erkannt wird."
                snapshot?.planeCount == 0 -> "Suche den Boden … richte die Kamera auf eine freie Fläche am Boden."
                snapshot?.anchor == null -> "Boden gefunden. Tippe auf den Boden, wo die Wichteltür stehen soll."
                else -> null
            }
            hint?.let {
                Text(
                    it,
                    color = Color(0xFFFFE08A),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("ar_hint"),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArAction.entries.forEach { a ->
                    FilterChip(selected = action == a, onClick = { action = a }, label = { Text(a.label) }, modifier = Modifier.testTag("ar_action_${a.name}"))
                }
            }
            Text("Größe", color = Color.White, fontSize = 12.sp)
            Slider(value = doorMeters, onValueChange = { doorMeters = it }, valueRange = 0.15f..0.6f, modifier = Modifier.testTag("ar_size"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { renderer.clearAnchor() }, modifier = Modifier.weight(1f).testTag("ar_new_place")) { Text("📍 Neuer Platz", color = Color.White) }
                OutlinedButton(onClick = { muted = !muted }, modifier = Modifier.weight(1f).testTag("ar_mute")) { Text(if (muted) "🔇 Ton aus" else "🔊 Ton an", color = Color.White) }
            }
        }
    }
}
