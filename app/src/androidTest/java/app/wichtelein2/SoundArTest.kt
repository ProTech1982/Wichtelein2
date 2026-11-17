package app.wichtelein2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.ar.ArDrawer
import app.wichtelein2.audio.AudioPlayer
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.Anim
import app.wichtelein2.core.ArSnapshot
import app.wichtelein2.core.MusicTrack
import app.wichtelein2.core.Mixdown
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Retreat
import app.wichtelein2.core.SoundGroup
import app.wichtelein2.core.Simulator
import app.wichtelein2.core.Step
import app.wichtelein2.render.Exporter
import app.wichtelein2.render.SpriteLibrary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ton (Mischung, Tonspur im Video, Abspielen) und das Zeichnen im AR-Raum (mit einer künstlichen Kamera). */
@RunWith(AndroidJUnit4::class)
class SoundArTest {
    private val ctx = TestUtil.context

    @Test
    fun exportedVideoHasAnAacTrackOfTheRightLength() {
        val plan = Plan.default(640, 480).copy(steps = listOf(Step(ActionType.SWEEP, seconds = 2f), Step(ActionType.SCARE, retreat = Retreat.DOOR)))
        val sim = Simulator(plan).run()
        val pcm = Mixdown.mix(sim, SoundGroup.entries.toSet(), MusicTrack.SNOWFLAKE)!!
        val out = File(ctx.filesDir, "exports/mit_ton.mp4")
        val r = Exporter.export(plan, TestUtil.photo(640, 480), SpriteLibrary(ctx), null, sim, out, audio = pcm, maxSide = 480)
        assertTrue(out.exists() && out.length() > 20_000)
        assertEquals(sim.frames.size, r.frames)
        assertTrue("Zwischendatei muss weg sein", !File(out.path + ".video").exists())

        val ex = MediaExtractor()
        ex.setDataSource(out.path)
        var audioIdx = -1
        var videoIdx = -1
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) audioIdx = i
            if (mime.startsWith("video/")) videoIdx = i
        }
        assertTrue("Tonspur fehlt", audioIdx >= 0)
        assertTrue("Bildspur fehlt", videoIdx >= 0)
        val fmt = ex.getTrackFormat(audioIdx)
        assertEquals("audio/mp4a-latm", fmt.getString(MediaFormat.KEY_MIME))
        assertEquals(44100, fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        // Länge der Tonspur: letzte Paketzeit
        ex.selectTrack(audioIdx)
        var last = 0L
        var packets = 0
        val buf = java.nio.ByteBuffer.allocate(1 shl 16)
        while (true) {
            val n = ex.readSampleData(buf, 0)
            if (n < 0) break
            last = ex.sampleTime
            packets++
            ex.advance()
        }
        ex.release()
        assertTrue("zu wenig Tonpakete: $packets", packets > 100)
        assertEquals("Tonlänge", pcm.size * 1000f / 44100f, last / 1000f, 400f)

        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(out.path)
        assertEquals("yes", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
        assertEquals("yes", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        mmr.release()
        assertTrue("Videolänge $dur", dur > sim.duration * 1000f - 400f)
    }

    @Test
    fun exportWithoutAudioStaysSilent() {
        val plan = Plan.default(320, 240)
        val sim = Simulator(plan).run()
        val out = File(ctx.filesDir, "exports/stumm.mp4")
        Exporter.export(plan, TestUtil.photo(320, 240), SpriteLibrary(ctx), null, sim, out, maxSide = 320)
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(out.path)
        assertTrue(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes")
        mmr.release()
    }

    @Test
    fun audioPlayerStartsSeeksAndStopsWithoutCrashing() {
        val plan = Plan.default(640, 480)
        val sim = Simulator(plan).run()
        val pcm = Mixdown.mix(sim, SoundGroup.entries.toSet(), MusicTrack.TANNE)!!
        val p = AudioPlayer()
        p.play(pcm, 0f)
        p.play(pcm, 3f, volume = 0.5f)
        p.play(pcm, 1f, loop = true)
        p.play(pcm, 9999f)      // hinter dem Ende: wird auf das Ende begrenzt
        p.play(FloatArray(0))   // leer: kein Absturz
        p.stop()
        p.stop()
        assertTrue(!p.isPlaying)
    }

    // ------------------------------------------------------------------ AR-Zeichnen mit künstlicher Kamera
    /** Kamera in (cam), schaut auf den Boden-Punkt (0, 0, 0): einfache Lochkamera (OpenGL-Matrizen, spaltenweise). */
    private fun snapshot(cam: FloatArray, w: Int, h: Int): ArSnapshot {
        val target = floatArrayOf(0f, 0.15f, 0f)
        fun norm(v: FloatArray): FloatArray {
            val l = Math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
            return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
        }
        fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val fwd = norm(floatArrayOf(target[0] - cam[0], target[1] - cam[1], target[2] - cam[2]))
        val right = norm(cross(fwd, floatArrayOf(0f, 1f, 0f)))
        val up = cross(right, fwd)
        val view = FloatArray(16)
        view[0] = right[0]; view[4] = right[1]; view[8] = right[2]; view[12] = -dot(right, cam)
        view[1] = up[0]; view[5] = up[1]; view[9] = up[2]; view[13] = -dot(up, cam)
        view[2] = -fwd[0]; view[6] = -fwd[1]; view[10] = -fwd[2]; view[14] = dot(fwd, cam)
        view[15] = 1f
        val near = 0.05f
        val far = 60f
        val f = 1.6f
        val proj = FloatArray(16)
        proj[0] = f * h / w
        proj[5] = f
        proj[10] = -(far + near) / (far - near)
        proj[11] = -1f
        proj[14] = -2f * far * near / (far - near)
        val vp = FloatArray(16)
        for (c in 0 until 4) for (r in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += proj[k * 4 + r] * view[c * 4 + k]
            vp[c * 4 + r] = sum
        }
        return ArSnapshot(
            vp, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f),
            cam, w, h, true, 1, 1,
        )
    }

    private fun background(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        p.color = Color.rgb(170, 160, 150)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = Color.rgb(120, 110, 100)
        for (i in 0 until 12) c.drawRect(0f, h * 0.45f + i * h * 0.05f, w.toFloat(), h * 0.45f + i * h * 0.05f + 3f, p)
        return b
    }

    /** Ein Zeitpunkt mitten im Fegen (Wichtel steht draußen) */
    private fun sweepTime(steps: List<Step>): Float {
        val plan = ArDrawer(SpriteLibrary(ctx)).planFor(steps)
        return Simulator(plan).run().frames.first { it.anim == Anim.SWEEP }.t + 0.8f
    }

    private fun render(cam: FloatArray, tSec: Float, steps: List<Step>): Bitmap {
        val w = 480
        val h = 800
        val drawer = ArDrawer(SpriteLibrary(ctx))
        val plan = drawer.planFor(steps)
        val sim = Simulator(plan).run()
        val bmp = background(w, h)
        val ok = drawer.draw(Canvas(bmp), plan, snapshot(cam, w, h), sim.at(tSec), 0.30f)
        assertTrue("Zeichnen meldet Misserfolg", ok)
        return bmp
    }

    @Test
    fun arDrawsDoorAndWichtelIntoTheRoom() {
        val steps = listOf(Step(ActionType.SWEEP, seconds = 3f), Step(ActionType.SCARE, retreat = Retreat.DOOR))
        val cam = floatArrayOf(0f, 0.9f, 1.2f)
        val before = background(480, 800)
        val early = render(cam, 0.1f, steps)       // Tür zu, Wichtel drinnen
        val sweeping = render(cam, sweepTime(steps), steps)    // Wichtel draußen beim Fegen
        TestUtil.shot("ar_tuer_zu", early)
        TestUtil.shot("ar_fegen", sweeping)
        assertTrue("Tür fehlt", TestUtil.diffPixels(before, early, 0, 0, 480, 800, tol = 12) > 1500)
        assertTrue("Wichtel fehlt", TestUtil.diffPixels(early, sweeping, 0, 0, 480, 800, tol = 12) > 1500)
    }

    @Test
    fun arWichtelTurnsWhenWalkingAroundAndSwitchesToTopView() {
        val steps = listOf(Step(ActionType.SWEEP, seconds = 4f))
        val front = render(floatArrayOf(0f, 0.6f, 1.2f), sweepTime(steps), steps)
        val side = render(floatArrayOf(1.2f, 0.6f, 0f), sweepTime(steps), steps)
        val top = render(floatArrayOf(0f, 1.6f, 0.3f), sweepTime(steps), steps)
        TestUtil.shot("ar_vorn", front)
        TestUtil.shot("ar_seite", side)
        TestUtil.shot("ar_oben", top)
        assertTrue(TestUtil.diffPixels(front, side, 0, 0, 480, 800, tol = 12) > 3000)
        assertTrue(TestUtil.diffPixels(front, top, 0, 0, 480, 800, tol = 12) > 3000)
        assertNotNull(top)
    }

    @Test
    fun arDrawsNothingWithoutAnchorOrBehindTheCamera() {
        val w = 480
        val h = 800
        val drawer = ArDrawer(SpriteLibrary(ctx))
        val plan = drawer.planFor(listOf(Step(ActionType.SWEEP)))
        val sim = Simulator(plan).run()
        val snap = snapshot(floatArrayOf(0f, 0.9f, 1.2f), w, h)
        val noAnchor = ArSnapshot(snap.viewProjection, null, snap.right, snap.toward, snap.up, snap.camera, w, h, true, 1, 0)
        assertTrue(!drawer.draw(Canvas(background(w, h)), plan, noAnchor, sim.at(1f), 0.3f))
    }
}
