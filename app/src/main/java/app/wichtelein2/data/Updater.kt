package app.wichtelein2.data

import android.content.Context
import android.os.Build
import app.wichtelein2.core.UpdateCheck
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Fragt die öffentliche GitHub-Releases-API, ob ein neuerer Build bereitsteht (wie in Wichtelein).
 * Bewusst ohne Zugangsdaten: Ein eingebetteter Token wäre aus der APK auslesbar, daher ist das Repository öffentlich.
 */
object Updater {
    suspend fun check(context: Context): UpdateCheck.Result = withContext(Dispatchers.IO) {
        val installed = installedBuild(context)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(UpdateCheck.API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Wichtelein2")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            if (code == 404) return@withContext UpdateCheck.Result(error = "Noch kein Update veröffentlicht")
            if (code != 200) return@withContext UpdateCheck.Result(error = "GitHub antwortete mit Code $code")
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            UpdateCheck.parse(json, installed)
        } catch (e: Exception) {
            UpdateCheck.Result(error = "Keine Verbindung zu GitHub")
        } finally {
            conn?.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    fun installedBuild(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
