package app.wichtelein2.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.wichtelein2.core.BgDesign
import app.wichtelein2.core.Backgrounds
import app.wichtelein2.core.Mixdown
import app.wichtelein2.core.MusicTrack
import app.wichtelein2.core.SoundGroup
import app.wichtelein2.core.Plan
import app.wichtelein2.core.PlanJson
import app.wichtelein2.core.WichtelColors
import java.io.File
import kotlin.math.max

/**
 * App-Einstellungen: Design (0 System, 1 hell, 2 dunkel), Wichtelfarben (null = Original) und Ton.
 * Ton: Geräusche je Gruppe an/aus, Musik (0 aus, 1..4 Melodie), Lautstärken. Der Wichtel spricht nie.
 */
data class AppSettings(
    val themeMode: Int = 0,
    val colors: WichtelColors = WichtelColors(),
    val soundGroups: Set<SoundGroup> = SoundGroup.entries.toSet(),
    val music: Int = 1,
    val fxVolume: Float = 0.8f,
    val musicVolume: Float = 0.5f,
    /** Startbild (Kennung eines eingebauten oder eigenen Bilds) */
    val bgId: String = Backgrounds.DEFAULT_ID,
    /** Musik je Startbild (Kürzel, siehe [Backgrounds]); ohne Eintrag gilt die Standard-Melodie des Bilds */
    val bgMusic: Map<String, String> = emptyMap(),
    /** Kennungen der eigenen Startbilder in der Reihenfolge des Hinzufügens */
    val customBgs: List<String> = emptyList(),
    /** Musik auf der Startseite an/aus und ihre Lautstärke */
    val homeMusic: Boolean = true,
    val bgVolume: Float = 0.5f,
)

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("wichtelein2", Context.MODE_PRIVATE)

    private fun color(key: String): Int? = if (p.contains(key)) p.getInt(key, 0) else null

    fun load(): AppSettings = AppSettings(
        themeMode = p.getInt("themeMode", 0).coerceIn(0, 2),
        colors = WichtelColors(color("hat"), color("jacket"), color("pants"), color("shoes")),
        soundGroups = Mixdown.fromMask(p.getInt("soundGroups", Mixdown.toMask(SoundGroup.entries.toSet()))),
        music = p.getInt("music", 1).coerceIn(0, MusicTrack.entries.size),
        fxVolume = p.getFloat("fxVolume", 0.8f).coerceIn(0f, 1f),
        musicVolume = p.getFloat("musicVolume", 0.5f).coerceIn(0f, 1f),
        bgId = p.getString("bgId", Backgrounds.DEFAULT_ID) ?: Backgrounds.DEFAULT_ID,
        bgMusic = Backgrounds.decodeChoices(p.getString("bgMusic", "") ?: ""),
        customBgs = (p.getString("customBgs", "") ?: "").split(",").filter { Backgrounds.isCustom(it) },
        homeMusic = p.getBoolean("homeMusic", true),
        bgVolume = p.getFloat("bgVolume", 0.5f).coerceIn(0f, 1f),
    )

    fun save(s: AppSettings) {
        val e = p.edit()
        e.putInt("themeMode", s.themeMode)
        e.putInt("soundGroups", Mixdown.toMask(s.soundGroups))
        e.putInt("music", s.music)
        e.putFloat("fxVolume", s.fxVolume)
        e.putFloat("musicVolume", s.musicVolume)
        e.putString("bgId", s.bgId)
        e.putString("bgMusic", Backgrounds.encodeChoices(s.bgMusic))
        e.putString("customBgs", s.customBgs.joinToString(","))
        e.putBoolean("homeMusic", s.homeMusic)
        e.putFloat("bgVolume", s.bgVolume)
        fun put(key: String, c: Int?) {
            if (c == null) e.remove(key) else e.putInt(key, c)
        }
        put("hat", s.colors.hat)
        put("jacket", s.colors.jacket)
        put("pants", s.colors.pants)
        put("shoes", s.colors.shoes)
        e.apply()
    }
}

/** Das aktuelle Projekt (Foto, Plan, eigenes Innenraum-Bild) im privaten App-Ordner. */
class ProjectStore(private val context: Context) {
    private val dir = File(context.filesDir, "project").also { it.mkdirs() }
    private val photoFile = File(dir, "photo.jpg")
    private val planFile = File(dir, "plan.json")
    private val interiorFile = File(dir, "interior.jpg")

    fun hasProject() = photoFile.exists() && planFile.exists()

    fun loadPlan(): Plan? = try {
        if (hasProject()) PlanJson.fromJson(planFile.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun savePlan(plan: Plan) {
        planFile.writeText(PlanJson.toJson(plan))
    }

    fun loadPhoto(): Bitmap? = if (photoFile.exists()) BitmapFactory.decodeFile(photoFile.path) else null

    fun loadInterior(): Bitmap? = if (interiorFile.exists()) BitmapFactory.decodeFile(interiorFile.path) else null

    /** Übernimmt ein Foto (Galerie/Kamera): richtig gedreht, auf höchstens maxSide verkleinert, neuer Startplan. */
    fun importPhoto(uri: Uri, maxSide: Int = 2048): Plan? {
        val bmp = decodeScaled(uri, maxSide) ?: return null
        return importBitmap(bmp)
    }

    fun importBitmap(bmp: Bitmap): Plan {
        photoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        interiorFile.delete()
        val plan = Plan.default(bmp.width, bmp.height)
        savePlan(plan)
        return plan
    }

    fun importInterior(uri: Uri): Boolean {
        val bmp = decodeScaled(uri, 1024) ?: return false
        interiorFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return true
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? = decodeScaledBitmap(context, uri, maxSide)
}

/** Liest ein Bild (Galerie, Kamera), dreht es richtig (EXIF) und verkleinert es auf höchstens maxSide. */
fun decodeScaledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val rot = try {
        cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    } catch (e: Exception) {
        0
    }
    val longSide = max(bmp.width, bmp.height)
    val k = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
    if (rot != 0 || k < 1f) {
        val m = Matrix()
        if (k < 1f) m.postScale(k, k)
        if (rot != 0) m.postRotate(rot.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    return bmp
}

/**
 * Eigene Startbilder und eigene Musik zu den Startbildern (private App-Ordner). Bilder: filesDir/backgrounds/<Kennung>.jpg,
 * Musik: filesDir/bgmusic/<Kennung>.audio (Kopie der gewählten Datei, damit sie auch später noch lesbar ist).
 */
class BackgroundStore(private val context: Context) {
    private val dir = File(context.filesDir, "backgrounds").also { it.mkdirs() }
    private val musicDir = File(context.filesDir, "bgmusic").also { it.mkdirs() }

    fun imageFile(id: String) = File(dir, "$id.jpg")
    fun musicFile(id: String) = File(musicDir, "$id.audio")
    fun hasMusic(id: String) = musicFile(id).length() > 0

    /** Übernimmt ein Bild aus der Galerie als neues Startbild; liefert die neue Kennung oder null. */
    fun importImage(uri: Uri): String? {
        val bmp = decodeScaledBitmap(context, uri, 1920) ?: return null
        val id = Backgrounds.CUSTOM_PREFIX + System.currentTimeMillis()
        imageFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return id
    }

    /** Kopiert eine Musikdatei zum Startbild [id]. */
    fun importMusic(id: String, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            musicFile(id).outputStream().use { out -> input.copyTo(out) }
        }
        hasMusic(id)
    } catch (e: Exception) {
        false
    }

    fun delete(id: String) {
        imageFile(id).delete()
        musicFile(id).delete()
    }

    /** Das Bild eines Startbilds (eingebaut aus den Assets, eigenes aus dem App-Ordner); [maxSide] verkleinert für Vorschaubilder. */
    fun loadBitmap(id: String, designs: List<BgDesign>, maxSide: Int = 1920): Bitmap? = try {
        val design = designs.firstOrNull { it.id == id }
        val opts = BitmapFactory.Options()
        if (design != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(design.file).use { BitmapFactory.decodeStream(it, null, bounds) }
            opts.inSampleSize = sampleFor(max(bounds.outWidth, bounds.outHeight), maxSide)
            context.assets.open(design.file).use { BitmapFactory.decodeStream(it, null, opts) }
        } else {
            val f = imageFile(id)
            if (!f.exists()) null else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                opts.inSampleSize = sampleFor(max(bounds.outWidth, bounds.outHeight), maxSide)
                BitmapFactory.decodeFile(f.path, opts)
            }
        }
    } catch (e: Throwable) {
        null
    }

    private fun sampleFor(longSide: Int, maxSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
