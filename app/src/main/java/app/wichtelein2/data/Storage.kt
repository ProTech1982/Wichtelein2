package app.wichtelein2.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.wichtelein2.core.Plan
import app.wichtelein2.core.PlanJson
import app.wichtelein2.core.WichtelColors
import java.io.File
import kotlin.math.max

/** App-Einstellungen: Design (0 System, 1 hell, 2 dunkel) und Wichtelfarben (null = Original). */
data class AppSettings(
    val themeMode: Int = 0,
    val colors: WichtelColors = WichtelColors(),
)

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("wichtelein2", Context.MODE_PRIVATE)

    private fun color(key: String): Int? = if (p.contains(key)) p.getInt(key, 0) else null

    fun load(): AppSettings = AppSettings(
        themeMode = p.getInt("themeMode", 0).coerceIn(0, 2),
        colors = WichtelColors(color("hat"), color("jacket"), color("pants"), color("shoes")),
    )

    fun save(s: AppSettings) {
        val e = p.edit()
        e.putInt("themeMode", s.themeMode)
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

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
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
}
