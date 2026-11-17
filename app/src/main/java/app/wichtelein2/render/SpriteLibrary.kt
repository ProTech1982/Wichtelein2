package app.wichtelein2.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.wichtelein2.core.Anim
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.Recolor
import app.wichtelein2.core.ViewMode
import app.wichtelein2.core.WichtelColors
import org.json.JSONObject

/** Maße der Wichtel-Bögen (assets/sprites/sprites.json, aus Blender). */
class SpriteMeta(
    val cell: Int,
    val feetX: Float,
    val feetY: Float,
    val pxPerMeter: Float,
    val heightM: Float,
    val ref: FloatArray,
) {
    /** Höhe des Wichtels samt Mütze im Bogen in Pixeln. */
    val heightPx get() = heightM * pxPerMeter
}

class DoorMeta(val w: Int, val h: Int, val x: Int, val y: Int, val bw: Int, val bh: Int)

/**
 * Lädt Wichtel-Bögen, Tür-Bilder und Innenräume aus den Assets. Die Bögen werden beim ersten Gebrauch
 * mit den Wunschfarben umgefärbt und gemerkt (Recolor, Farbmaske aus Blender).
 */
class SpriteLibrary(private val context: Context) {
    val meta: SpriteMeta
    private val doorMeta = HashMap<DoorShape, DoorMeta>()
    val doorAngles: List<String>

    private val sheets = HashMap<String, Bitmap>()
    private val doors = HashMap<String, Bitmap>()
    private val interiors = HashMap<InteriorKind, Bitmap>()
    private var colors = WichtelColors()

    init {
        val m = JSONObject(read("sprites/sprites.json"))
        val ref = m.getJSONObject("refLuma")
        meta = SpriteMeta(
            cell = m.getInt("cell"),
            feetX = m.getJSONArray("feet").getDouble(0).toFloat(),
            feetY = m.getJSONArray("feet").getDouble(1).toFloat(),
            pxPerMeter = m.getDouble("pxPerMeter").toFloat(),
            heightM = m.getDouble("heightM").toFloat(),
            ref = floatArrayOf(
                ref.getDouble("hat").toFloat(), ref.getDouble("jacket").toFloat(),
                ref.getDouble("pants").toFloat(), ref.getDouble("shoes").toFloat(),
            ),
        )
        val d = JSONObject(read("door/door.json"))
        val shapes = d.getJSONObject("shapes")
        for (s in DoorShape.entries) {
            val j = shapes.getJSONObject(s.key)
            doorMeta[s] = DoorMeta(j.getInt("w"), j.getInt("h"), j.getInt("x"), j.getInt("y"), j.getInt("bw"), j.getInt("bh"))
        }
        val a = d.getJSONArray("angles")
        doorAngles = List(a.length()) { a.getString(it) }
    }

    private fun read(path: String) = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun decode(path: String, mutable: Boolean): Bitmap {
        val o = BitmapFactory.Options()
        o.inMutable = mutable
        return context.assets.open(path).use { BitmapFactory.decodeStream(it, null, o) } ?: error("Bild nicht lesbar: $path")
    }

    fun doorMeta(shape: DoorShape): DoorMeta = doorMeta.getValue(shape)

    /** Wunschfarben setzen; bereits umgefärbte Bögen werden verworfen. */
    @Synchronized
    fun setColors(c: WichtelColors) {
        if (c == colors) return
        colors = c
        sheets.clear()
    }

    /** Bogen einer Bewegung: Spalten = Bilder, Zeilen = 8 Blickrichtungen. */
    @Synchronized
    fun sheet(view: ViewMode, anim: Anim): Bitmap {
        val key = view.key + "/" + anim.key
        sheets[key]?.let { return it }
        val bmp = decode("sprites/$key.webp", mutable = true)
        if (!colors.isOriginal) {
            val mask = decode("sprites/${key}_m.webp", mutable = false)
            val n = bmp.width * bmp.height
            val px = IntArray(n)
            val mk = IntArray(n)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            mask.getPixels(mk, 0, mask.width, 0, 0, mask.width, mask.height)
            mask.recycle()
            Recolor.apply(px, mk, colors, meta.ref)
            bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        }
        sheets[key] = bmp
        return bmp
    }

    /** Lädt alle Bögen einer Ansicht vor (damit die Vorschau nicht ruckelt). */
    fun preload(view: ViewMode) {
        for (a in Anim.entries) sheet(view, a)
    }

    @Synchronized
    fun door(shape: DoorShape, angle: String): Bitmap =
        doors.getOrPut("${shape.key}_$angle") { decode("door/door_${shape.key}_$angle.webp", mutable = false) }

    fun doorHole(shape: DoorShape): Bitmap = door(shape, "hole")

    /** Eingebauter Innenraum; null bei Schwarz (und bei fehlendem eigenem Bild, das setzt der Aufrufer). */
    @Synchronized
    fun interior(kind: InteriorKind): Bitmap? = when (kind) {
        InteriorKind.STUBE -> interiors.getOrPut(kind) { decode("interior/interior_stube.webp", mutable = false) }
        InteriorKind.HOEHLE -> interiors.getOrPut(kind) { decode("interior/interior_hoehle.webp", mutable = false) }
        else -> null
    }
}
