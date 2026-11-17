package app.wichtelein2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

object TestUtil {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Foto mit Himmel, Wiese und ein paar Mustern, damit man Verdeckung und Tür gut erkennt. */
    fun photo(w: Int = 800, h: Int = 600): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(150, 200, 240)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.35f, p)
        p.color = Color.rgb(90, 150, 70)
        c.drawRect(0f, h * 0.35f, w.toFloat(), h.toFloat(), p)
        p.color = Color.rgb(120, 85, 60)
        for (i in 0 until 12) c.drawRect(i * w / 12f + 4, h * 0.2f, i * w / 12f + w / 12f - 4, h * 0.42f, p)
        p.color = Color.rgb(40, 100, 40)
        c.drawOval(w * 0.12f, h * 0.30f, w * 0.34f, h * 0.62f, p)
        p.color = Color.rgb(230, 230, 120)
        for (i in 0 until 40) c.drawCircle((i * 97 % w).toFloat(), h * 0.65f + (i * 53 % (h * 0.3f).toInt()), 6f, p)
        return b
    }

    /** Legt ein Bild zur Ansicht in files/shots ab (CI lädt den Ordner hoch). */
    fun shot(name: String, bmp: Bitmap) {
        val dir = File(context.filesDir, "shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun diffPixels(a: Bitmap, b: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, tol: Int = 6): Int {
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            val d = Math.abs(Color.red(p) - Color.red(q)) + Math.abs(Color.green(p) - Color.green(q)) + Math.abs(Color.blue(p) - Color.blue(q))
            if (d > tol) n++
        }
        return n
    }
}
