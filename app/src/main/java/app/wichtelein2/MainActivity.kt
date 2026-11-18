package app.wichtelein2

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import app.wichtelein2.ar.ArScreen
import app.wichtelein2.core.Backgrounds
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.Mixdown
import app.wichtelein2.core.MusicChoice
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Simulation
import app.wichtelein2.core.Simulator
import app.wichtelein2.core.UpdateCheck
import app.wichtelein2.data.AppSettings
import app.wichtelein2.data.BackgroundStore
import app.wichtelein2.data.ProjectStore
import app.wichtelein2.data.SettingsStore
import app.wichtelein2.data.Updater
import app.wichtelein2.render.Exporter
import app.wichtelein2.render.SpriteLibrary
import app.wichtelein2.ui.EditorScreen
import app.wichtelein2.ui.HomeScreen
import app.wichtelein2.ui.PreviewScreen
import app.wichtelein2.ui.SettingsScreen
import app.wichtelein2.ui.UpdateDialog
import app.wichtelein2.ui.UpdateDialogState
import app.wichtelein2.ui.WichtelTheme
import app.wichtelein2.ui.isDark
import app.wichtelein2.ui.updateDialogFrom
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Screen { HOME, EDITOR, PREVIEW, SETTINGS, AR }

private sealed interface ExportState {
    class Running(val progress: Float) : ExportState
    class Done(val file: File, val uri: Uri?) : ExportState
    class Failed(val message: String) : ExportState
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(ctx) }
    val project = remember { ProjectStore(ctx) }
    val sprites = remember { SpriteLibrary(ctx) }
    val bgStore = remember { BackgroundStore(ctx) }
    val designs = remember { Backgrounds.parse(ctx.assets.open("backgrounds/backgrounds.json").bufferedReader(Charsets.UTF_8).use { it.readText() }) }

    var settings by remember { mutableStateOf(settingsStore.load()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var plan by remember { mutableStateOf<Plan?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var interior by remember { mutableStateOf<Bitmap?>(null) }
    var hasProject by remember { mutableStateOf(project.hasProject()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sim by remember { mutableStateOf<Simulation?>(null) }
    var export by remember { mutableStateOf<ExportState?>(null) }
    val exportCancelled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var updateResult by remember { mutableStateOf<UpdateCheck.Result?>(null) }
    var updateDialog by remember { mutableStateOf<UpdateDialogState?>(null) }
    var musicVersion by remember { mutableIntStateOf(0) }
    var pendingMusicBg by remember { mutableStateOf<String?>(null) }

    fun changeSettings(s: AppSettings) {
        settings = s
        settingsStore.save(s)
    }

    sprites.setColors(settings.colors)

    val dark = isDark(settings.themeMode, isSystemInDarkTheme())
    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        )
        onDispose { }
    }

    // Beim Start einmal leise nach einem Update schauen
    LaunchedEffect(Unit) {
        val r = Updater.check(ctx)
        if (r.available) updateResult = r
    }

    // Änderungen am Plan zwischenspeichern
    LaunchedEffect(plan) {
        val p = plan ?: return@LaunchedEffect
        delay(250)
        withContext(Dispatchers.IO) { project.savePlan(p) }
    }

    fun openProject(p: Plan, ph: Bitmap) {
        plan = p
        photo = ph
        interior = project.loadInterior()
        hasProject = true
        screen = Screen.EDITOR
    }

    fun importFrom(uri: Uri) {
        scope.launch {
            busy = true
            message = null
            val result = withContext(Dispatchers.IO) {
                val p = project.importPhoto(uri)
                if (p == null) null else Pair(p, project.loadPhoto())
            }
            busy = false
            val r = result
            val bmp = r?.second
            if (r == null || bmp == null) message = "Das Foto konnte nicht geladen werden." else openProject(r.first, bmp)
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) importFrom(uri) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) importFrom(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(ctx.cacheDir, "camera/photo.jpg")))
    }
    var pendingTake by remember { mutableStateOf(false) }
    fun launchTake() {
        val f = File(ctx.cacheDir, "camera/photo.jpg")
        f.parentFile?.mkdirs()
        try {
            takePhoto.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
        } catch (e: Exception) {
            message = "Keine Kamera-App gefunden. Bitte ein Foto aus der Galerie wählen."
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (pendingTake) {
            pendingTake = false
            // ohne Erlaubnis kann die Kamera-App nicht über uns starten (Android verlangt sie, wenn die App sie deklariert)
            if (ok) launchTake() else message = "Für ein Foto mit der Kamera braucht die App die Kamera-Erlaubnis. Du kannst auch ein Foto aus der Galerie wählen."
        }
    }
    val pickInterior = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { project.importInterior(uri) }
            if (ok) {
                interior = withContext(Dispatchers.IO) { project.loadInterior() }
                plan = plan?.let { it.copy(door = it.door.copy(interior = InteriorKind.CUSTOM)) }
            }
        }
    }

    // Startseite: eigenes Hintergrundbild und eigene Musik zu einem Hintergrund
    val pickBgImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val id = withContext(Dispatchers.IO) { bgStore.importImage(uri) }
            if (id != null) changeSettings(settings.copy(customBgs = settings.customBgs + id, bgId = id)) else message = "Das Bild konnte nicht geladen werden."
        }
    }
    val pickBgMusic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pendingMusicBg
        pendingMusicBg = null
        if (uri != null && id != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { bgStore.importMusic(id, uri) }
            if (ok) {
                changeSettings(settings.copy(bgMusic = settings.bgMusic + (id to Backgrounds.MUSIC_FILE)))
                musicVersion++
            } else {
                message = "Die Musikdatei konnte nicht geladen werden."
            }
        }
    }

    fun startCheck() {
        updateDialog = UpdateDialogState.Checking
        scope.launch {
            val r = Updater.check(ctx)
            updateResult = if (r.available) r else null
            updateDialog = updateDialogFrom(r)
        }
    }

    fun runExport() {
        val p = plan ?: return
        val ph = photo ?: return
        export = ExportState.Running(0f)
        exportCancelled.set(false)
        scope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    val s = Simulator(p).run()
                    val name = "Wichtelein2_" + System.currentTimeMillis() / 1000 + ".mp4"
                    val out = File(ctx.filesDir, "exports/$name")
                    // Ton (Geräusche und Musik) laut Einstellungen in die Tonspur mischen
                    val pcm = Mixdown.mix(s, settings.soundGroups, MusicChoice.track(settings.music), settings.fxVolume, settings.musicVolume)
                    Exporter.export(
                        p, ph, sprites, interior, s, out, audio = pcm,
                        onProgress = { export = ExportState.Running(it) },
                        cancelled = { exportCancelled.get() },
                    ).file
                }
                val uri = withContext(Dispatchers.IO) { Exporter.saveToGallery(ctx, file) }
                export = ExportState.Done(file, uri)
            } catch (e: InterruptedException) {
                export = null
            } catch (e: Throwable) {
                export = ExportState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    WichtelTheme(settings.themeMode) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    version = Updater.installedVersionName(ctx),
                    hasProject = hasProject,
                    busy = busy,
                    message = message,
                    updateHint = updateResult?.let { "Update verfügbar: ${it.version ?: ""}" },
                    settings = settings,
                    designs = designs,
                    loadBackground = { id, maxSide -> bgStore.loadBitmap(id, designs, maxSide) },
                    musicFile = { id -> bgStore.musicFile(id).takeIf { it.length() > 0 } },
                    musicVersion = musicVersion,
                    onChange = { changeSettings(it) },
                    onAddBackground = { pickBgImage.launch("image/*") },
                    onPickBackgroundMusic = { id ->
                        pendingMusicBg = id
                        pickBgMusic.launch("audio/*")
                    },
                    onDeleteBackground = { id ->
                        bgStore.delete(id)
                        changeSettings(
                            settings.copy(
                                customBgs = settings.customBgs - id,
                                bgMusic = settings.bgMusic - id,
                                bgId = if (settings.bgId == id) Backgrounds.DEFAULT_ID else settings.bgId,
                            ),
                        )
                    },
                    onPick = { pickPhoto.launch("image/*") },
                    onTake = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            launchTake()
                        } else {
                            pendingTake = true
                            cameraPermission.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    onContinue = {
                        scope.launch {
                            busy = true
                            val r = withContext(Dispatchers.IO) { Pair(project.loadPlan(), project.loadPhoto()) }
                            busy = false
                            val pl = r.first
                            val bmp = r.second
                            if (pl != null && bmp != null) openProject(pl, bmp) else message = "Das letzte Projekt ist nicht mehr da."
                        }
                    },
                    onAr = { screen = Screen.AR },
                    onSettings = { screen = Screen.SETTINGS },
                    onUpdateHint = { updateResult?.let { updateDialog = updateDialogFrom(it) } },
                )
                Screen.SETTINGS -> {
                    BackHandler { screen = Screen.HOME }
                    SettingsScreen(
                        settings = settings,
                        sprites = sprites,
                        version = Updater.installedVersionName(ctx) + " (Build " + Updater.installedBuild(ctx) + ")",
                        onChange = { s: AppSettings -> changeSettings(s) },
                        onCheckUpdate = { startCheck() },
                        onBack = { screen = Screen.HOME },
                    )
                }
                Screen.EDITOR -> {
                    val p = plan
                    val ph = photo
                    if (p != null && ph != null) {
                        BackHandler { screen = Screen.HOME }
                        EditorScreen(
                            plan = p,
                            photo = ph,
                            sprites = sprites,
                            customInterior = interior,
                            colorsKey = settings.colors,
                            onPlan = { plan = it },
                            onBack = { screen = Screen.HOME },
                            onPreview = {
                                scope.launch {
                                    sim = withContext(Dispatchers.Default) { Simulator(p).run() }
                                    screen = Screen.PREVIEW
                                }
                            },
                            onExport = { runExport() },
                            onPickInterior = { pickInterior.launch("image/*") },
                        )
                    }
                }
                Screen.AR -> {
                    BackHandler { screen = Screen.HOME }
                    ArScreen(settings, sprites) { screen = Screen.HOME }
                }
                Screen.PREVIEW -> {
                    val p = plan
                    val ph = photo
                    val s = sim
                    if (p != null && ph != null && s != null) {
                        BackHandler { screen = Screen.EDITOR }
                        PreviewScreen(p, ph, sprites, interior, s, settings) { screen = Screen.EDITOR }
                    }
                }
            }
        }
        UpdateDialog(
            state = updateDialog,
            onUpdate = { url ->
                updateDialog = null
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onDismiss = { updateDialog = null },
        )
        when (val e = export) {
            null -> Unit
            is ExportState.Running -> AlertDialog(
                onDismissRequest = { },
                title = { Text("Video wird gebaut …") },
                text = {
                    Column {
                        LinearProgressIndicator(progress = { e.progress }, modifier = Modifier.testTag("export_progress"))
                        Text("%d %%".format((e.progress * 100).toInt()))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { exportCancelled.set(true) }, modifier = Modifier.testTag("export_cancel")) { Text("Abbrechen") }
                },
            )
            is ExportState.Done -> AlertDialog(
                onDismissRequest = { export = null },
                title = { Text("Video fertig") },
                text = {
                    Text(
                        if (e.uri != null) "Gespeichert unter Filme/Wichtelein2 (Galerie)." else "Gespeichert in der App (Galerie nicht verfügbar).",
                        modifier = Modifier.testTag("export_done"),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = e.uri ?: FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", e.file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Video teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        export = null
                    }) { Text("Teilen") }
                },
                dismissButton = { TextButton(onClick = { export = null }, modifier = Modifier.testTag("export_ok")) { Text("OK") } },
            )
            is ExportState.Failed -> AlertDialog(
                onDismissRequest = { export = null },
                title = { Text("Video fehlgeschlagen") },
                text = { Text(e.message, modifier = Modifier.testTag("export_error")) },
                confirmButton = { TextButton(onClick = { export = null }) { Text("OK") } },
            )
        }
    }
}
