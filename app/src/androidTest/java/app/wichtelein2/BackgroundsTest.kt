package app.wichtelein2

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.audio.BackgroundMusic
import app.wichtelein2.core.Backgrounds
import app.wichtelein2.core.BgDesign
import app.wichtelein2.data.AppSettings
import app.wichtelein2.data.BackgroundStore
import app.wichtelein2.data.SettingsStore
import app.wichtelein2.ui.HomeBackdrop
import app.wichtelein2.ui.HomeFx
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Startbilder (eingebaut und eigene), die Musik dazu und das Zeichnen mit den Leuchten darüber. */
@RunWith(AndroidJUnit4::class)
class BackgroundsTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx = TestUtil.context
    private val designs: List<BgDesign> = Backgrounds.parse(ctx.assets.open("backgrounds/backgrounds.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    private val store = BackgroundStore(ctx)

    @Before
    fun before() {
        HomeFx.animate = false   // sonst wartet die Test-Uhr ewig auf Ruhe
        HomeFx.music = false
        ctx.getSharedPreferences("wichtelein2", 0).edit().clear().commit()
    }

    @After
    fun after() {
        HomeFx.animate = true
        HomeFx.music = true
    }

    @Test
    fun everyBuiltInBackgroundLoadsInFullAndPreviewSize() {
        assertTrue(designs.size in 6..10)
        for (d in designs) {
            val full = store.loadBitmap(d.id, designs)
            assertNotNull("Bild ${d.id}", full)
            assertEquals(1080, full!!.width)
            assertEquals(1920, full.height)
            val small = store.loadBitmap(d.id, designs, 360)!!
            assertTrue("Vorschau kleiner (${small.width})", small.width in 270..540)
        }
        assertNull(store.loadBitmap("custom_gibts_nicht", designs))
    }

    @Test
    fun backdropDrawsEveryDesignWithItsLightsAndSavesPictures() {
        val bitmaps = designs.map { store.loadBitmap(it.id, designs)!! }
        var idx by mutableIntStateOf(0)
        rule.setContent { HomeBackdrop(bitmaps[idx], designs[idx], Modifier.size(270.dp, 480.dp)) }
        for (i in designs.indices) {
            rule.runOnIdle { idx = i }
            rule.waitForIdle()
            val shot = rule.onRoot().captureToImage().asAndroidBitmap()
            TestUtil.shot("start_${designs[i].id}", shot)
            assertTrue("Bild ${designs[i].id} ist nicht einfarbig", colourCount(shot) > 40)
        }
    }

    @Test
    fun ownBackgroundWithoutDesignStillDraws() {
        val own = TestUtil.photo(900, 700)
        rule.setContent { HomeBackdrop(own, null, Modifier.size(270.dp, 480.dp)) }
        rule.waitForIdle()
        val shot = rule.onRoot().captureToImage().asAndroidBitmap()
        TestUtil.shot("start_eigenes_bild", shot)
        assertTrue(colourCount(shot) > 40)
    }

    @Test
    fun customImageAndMusicAreStoredAndDeleted() {
        val src = File(ctx.cacheDir, "eigenes.png")
        src.outputStream().use { TestUtil.photo(1200, 800).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val id = store.importImage(Uri.fromFile(src))
        assertNotNull(id)
        assertTrue(Backgrounds.isCustom(id!!))
        val bmp = store.loadBitmap(id, designs)
        assertNotNull(bmp)
        assertEquals(1200, bmp!!.width)

        assertFalse(store.hasMusic(id))
        val song = File(ctx.cacheDir, "lied.wav")
        song.writeBytes(wav(1.0f))
        assertTrue(store.importMusic(id, Uri.fromFile(song)))
        assertTrue(store.hasMusic(id))
        assertEquals(song.length(), store.musicFile(id).length())

        store.delete(id)
        assertNull(store.loadBitmap(id, designs))
        assertFalse(store.hasMusic(id))
        assertFalse(store.importMusic(id, Uri.fromFile(File(ctx.cacheDir, "gibts_nicht.wav"))))
    }

    @Test
    fun backgroundSettingsAreStoredAndLoaded() {
        val s = AppSettings(
            bgId = "custom_5", bgMusic = mapOf("kamin" to "t3", "custom_5" to "file", "dorf" to "off"),
            customBgs = listOf("custom_5"), homeMusic = false, bgVolume = 0.2f,
        )
        SettingsStore(ctx).save(s)
        val l = SettingsStore(ctx).load()
        assertEquals(s.bgId, l.bgId)
        assertEquals(s.bgMusic, l.bgMusic)
        assertEquals(s.customBgs, l.customBgs)
        assertFalse(l.homeMusic)
        assertEquals(0.2f, l.bgVolume, 0.001f)
        // Frische Einstellungen: Kamin, Musik an
        ctx.getSharedPreferences("wichtelein2", 0).edit().clear().commit()
        val d = SettingsStore(ctx).load()
        assertEquals(Backgrounds.DEFAULT_ID, d.bgId)
        assertTrue(d.homeMusic)
    }

    @Test
    fun builtInMusicLoopsAndStops() {
        runBlocking {
            val m = BackgroundMusic()
            m.play("t1", null, 0.3f)
            val end = System.currentTimeMillis() + 5_000
            while (!m.isPlaying && System.currentTimeMillis() < end) Thread.sleep(50)
            assertTrue("Melodie läuft", m.isPlaying)
            m.setVolume(0.1f)
            m.stop()
            assertFalse(m.isPlaying)
            // aus und eine fehlende eigene Datei bleiben still und stürzen nicht ab
            m.play("off", null, 0.3f)
            m.play("file", File(ctx.cacheDir, "gibts_nicht.wav"), 0.3f)
            assertFalse(m.isPlaying)
            m.stop()
        }
    }

    private fun colourCount(b: Bitmap): Int {
        val seen = HashSet<Int>()
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                seen.add((Color.red(p) shr 4) shl 8 or ((Color.green(p) shr 4) shl 4) or (Color.blue(p) shr 4))
                x += 7
            }
            y += 7
        }
        return seen.size
    }

    /** Kleine Mono-WAV-Datei (Sinuston), damit es eine echte Musikdatei zum Kopieren gibt. */
    private fun wav(sec: Float): ByteArray {
        val rate = 22050
        val n = (sec * rate).toInt()
        val data = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = (Math.sin(2 * Math.PI * 440 * i / rate) * 8000).toInt()
            data[2 * i] = (v and 0xFF).toByte()
            data[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        fun le32(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
        fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        return "RIFF".toByteArray() + le32(36 + data.size) + "WAVEfmt ".toByteArray() + le32(16) + le16(1) + le16(1) + le32(rate) +
            le32(rate * 2) + le16(2) + le16(16) + "data".toByteArray() + le32(data.size) + data
    }
}
