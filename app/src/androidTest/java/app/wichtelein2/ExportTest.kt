package app.wichtelein2

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.Anim
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Retreat
import app.wichtelein2.core.SceneObject
import app.wichtelein2.core.Simulator
import app.wichtelein2.core.Step
import app.wichtelein2.core.ViewMode
import app.wichtelein2.render.Exporter
import app.wichtelein2.render.SpriteLibrary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportTest {
    private val ctx = TestUtil.context

    private fun exportAndCheck(plan: Plan, name: String): Pair<Bitmap, Bitmap> {
        val sprites = SpriteLibrary(ctx)
        val photo = TestUtil.photo(plan.width, plan.height)
        val sim = Simulator(plan).run()
        val out = File(ctx.filesDir, "exports/$name.mp4")
        var lastProgress = 0f
        val r = Exporter.export(plan, photo, sprites, null, sim, out, maxSide = 640, onProgress = { lastProgress = it })
        assertTrue("Datei fehlt", out.exists())
        assertTrue("Datei zu klein: ${out.length()}", out.length() > 20_000)
        assertEquals(1f, lastProgress, 0.001f)
        assertEquals(sim.frames.size, r.frames)

        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(out.path)
        val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        assertEquals(r.width, w)
        assertEquals(r.height, h)
        assertEquals("Videolänge", sim.duration * 1000f, durMs.toFloat(), 400f)
        val first = mmr.getFrameAtTime(200_000, MediaMetadataRetriever.OPTION_CLOSEST)
        // Mitten im Fegen
        val sweepFrame = sim.frames.first { it.anim == Anim.SWEEP }
        val mid = mmr.getFrameAtTime((sweepFrame.t * 1_000_000L).toLong() + 600_000, MediaMetadataRetriever.OPTION_CLOSEST)
        mmr.release()
        assertNotNull(first)
        assertNotNull(mid)
        TestUtil.shot("export_${name}_first", first!!)
        TestUtil.shot("export_${name}_sweep", mid!!)
        return Pair(first, mid)
    }

    @Test
    fun exportsPlayableVideoWithMovingWichtel() {
        val plan = Plan.default(640, 480).copy(steps = listOf(Step(ActionType.SWEEP, seconds = 2f)))
        val (first, mid) = exportAndCheck(plan, "sweep")
        // erstes Bild: Wichtel noch im Haus, später steht er draußen → Bilder unterscheiden sich deutlich
        assertTrue(TestUtil.diffPixels(first, mid, 0, 0, first.width, first.height, tol = 40) > 500)
    }

    @Test
    fun exportsAllActionsInTopAndSideView() {
        for (view in ViewMode.entries) {
            val tree = SceneObject(1, ObjType.TREE, 200f, 250f, 160f, 240f)
            val plan = Plan.default(640, 480).copy(
                view = view,
                objects = listOf(tree),
                nextId = 2,
                steps = listOf(
                    Step(ActionType.SWEEP, seconds = 2f),
                    Step(ActionType.HIDE, retreat = Retreat.OBJECT, objectId = 1, seconds = 2f),
                    Step(ActionType.SCARE, retreat = Retreat.DOOR),
                ),
            )
            exportAndCheck(plan, "all_${view.key}")
        }
    }
}
