package app.wichtelein2.core

import org.json.JSONObject

/**
 * Auswertung der GitHub-Releases-Antwort (gleiches Verfahren wie in Wichtelein: Tag „build-N“,
 * die Nummer wird mit der Build-Nummer der installierten App verglichen).
 */
object UpdateCheck {
    const val API = "https://api.github.com/repos/ProTech1982/Wichtelein2/releases/latest"

    data class Result(
        val available: Boolean = false,
        val version: String? = null,
        val notes: String? = null,
        val downloadUrl: String? = null,
        val error: String? = null,
    )

    fun parse(json: JSONObject, installed: Long): Result {
        val tag = json.optString("tag_name", "")
        val assets = json.optJSONArray("assets")
        var url: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "").endsWith(".apk")) {
                    url = a.optString("browser_download_url", "").ifEmpty { null }
                    break
                }
            }
        }
        if (url == null) url = json.optString("html_url", "").ifEmpty { null }
        return Result(
            available = buildNumber(tag) > installed && url != null,
            version = json.optString("name", tag),
            notes = json.optString("body", ""),
            downloadUrl = url,
        )
    }

    /** Aus einem Tag wie "build-17" die Nummer ziehen, sonst -1. */
    fun buildNumber(tag: String?): Long = tag?.filter { it in '0'..'9' }?.toLongOrNull() ?: -1L

    /** Änderungsprotokoll lesbar machen (Markdown-Zeichen weglassen, leer = Hinweis). */
    fun cleanNotes(notes: String?): String {
        val t = notes.orEmpty().lines()
            .joinToString("\n") { it.trimEnd().removePrefix("###").removePrefix("##").removePrefix("#").trimStart() }
            .trim()
        return t.ifEmpty { "Keine Beschreibung der Änderungen vorhanden." }
    }
}
