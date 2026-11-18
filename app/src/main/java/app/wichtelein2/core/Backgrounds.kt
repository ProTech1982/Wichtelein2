package app.wichtelein2.core

import org.json.JSONObject

/** Flackernde Leuchte auf einem Startbild (Kaminfeuer, Kerze, Laterne …). x, y = Bruchteil der Bildgröße, r = Bruchteil der Bildbreite. */
data class BgGlow(val x: Float, val y: Float, val r: Float, val color: Int, val flicker: Float, val speed: Float)

/** Funkelndes Licht (Stern, Lichterkette, Glühwürmchen). */
data class BgTwinkle(val x: Float, val y: Float, val r: Float, val color: Int, val phase: Float)

/**
 * Ein eingebautes Startbild: Bilddatei in den Assets, was darüber animiert wird (Schnee, Leuchten, Funkeln) und welche Melodie
 * standardmäßig dazu spielt (1 bis 4 wie [MusicTrack], 0 = keine).
 */
data class BgDesign(
    val id: String,
    val label: String,
    val file: String,
    val music: Int,
    val snow: Float,
    val glows: List<BgGlow>,
    val twinkles: List<BgTwinkle>,
)

/**
 * Startbilder und ihre Musik. Die Wahl der Musik wird je Bild als Kürzel gespeichert:
 * "off" = aus, "t1".."t4" = eingebaute Melodie, "file" = eigene Musikdatei (liegt dann in der App).
 * Eigene Bilder haben Kennungen mit dem Anfang "custom_".
 */
object Backgrounds {
    const val DEFAULT_ID = "kamin"
    const val MUSIC_OFF = "off"
    const val MUSIC_FILE = "file"
    const val CUSTOM_PREFIX = "custom_"

    fun isCustom(id: String) = id.startsWith(CUSTOM_PREFIX)

    fun trackCode(track: MusicTrack) = "t" + (track.ordinal + 1)

    fun track(code: String): MusicTrack? =
        if (code.length >= 2 && code[0] == 't') code.substring(1).toIntOrNull()?.let { MusicChoice.track(it) } else null

    fun isValidMusic(code: String) = code == MUSIC_OFF || code == MUSIC_FILE || track(code) != null

    /** Kürzel für eine Melodie-Nummer aus der Bilddatei (0 = aus). */
    fun codeForNumber(n: Int) = if (n <= 0) MUSIC_OFF else MusicChoice.track(n)?.let { trackCode(it) } ?: MUSIC_OFF

    /** Gewählte Musik eines Startbilds; ohne eigene Wahl die Standard-Melodie des Bilds (bei eigenen Bildern „Schneeflocke“). */
    fun musicFor(choices: Map<String, String>, id: String, designs: List<BgDesign>): String {
        val c = choices[id]
        if (c != null && isValidMusic(c)) return c
        return codeForNumber(designs.firstOrNull { it.id == id }?.music ?: 1)
    }

    fun musicLabel(code: String): String = when {
        code == MUSIC_OFF -> "Aus"
        code == MUSIC_FILE -> "Eigene Datei"
        else -> track(code)?.label ?: "Aus"
    }

    /** "id=code;id=code" für die Einstellungen. */
    fun encodeChoices(m: Map<String, String>): String = m.entries.joinToString(";") { it.key + "=" + it.value }

    fun decodeChoices(s: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (part in s.split(";")) {
            val i = part.indexOf('=')
            if (i <= 0) continue
            val id = part.substring(0, i)
            val code = part.substring(i + 1)
            if (isValidMusic(code)) out[id] = code
        }
        return out
    }

    private fun color(s: String): Int = (0xFF000000L or s.removePrefix("#").toLong(16)).toInt()

    /** Liest backgrounds.json (von tools/make_backgrounds.py erzeugt). */
    fun parse(json: String): List<BgDesign> {
        val arr = JSONObject(json).getJSONArray("designs")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val g = o.optJSONArray("glows")
            val t = o.optJSONArray("twinkles")
            BgDesign(
                id = o.getString("id"),
                label = o.getString("label"),
                file = o.getString("file"),
                music = o.optInt("music", 1),
                snow = o.optDouble("snow", 0.0).toFloat(),
                glows = if (g == null) emptyList() else List(g.length()) { k ->
                    val e = g.getJSONObject(k)
                    BgGlow(e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.getDouble("r").toFloat(), color(e.getString("color")),
                        e.optDouble("flicker", 0.3).toFloat(), e.optDouble("speed", 1.0).toFloat())
                },
                twinkles = if (t == null) emptyList() else List(t.length()) { k ->
                    val e = t.getJSONObject(k)
                    BgTwinkle(e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.getDouble("r").toFloat(), color(e.getString("color")),
                        e.optDouble("phase", 0.0).toFloat())
                },
            )
        }
    }
}
