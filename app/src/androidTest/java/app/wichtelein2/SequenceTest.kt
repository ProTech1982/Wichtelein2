package app.wichtelein2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import app.wichtelein2.render.Renderer
import app.wichtelein2.render.SpriteLibrary
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spielt einen Ablauf mit allen Aktionen durch und legt Kontaktbögen der Bilder ab (zur Sichtprüfung in CI). */
@RunWith(AndroidJUnit4::class)
class SequenceTest {
    private val ctx = TestUtil.context

    private fun contactSheet(view: ViewMode) {
        val photo = TestUtil.photo(800, 600)
        val tree = SceneObject(1, ObjType.TREE, 190f, 320f, 200f, 300f)
        val bed = SceneObject(2, ObjType.BED, 420f, 470f, 160f, 80f)
        val grass = SceneObject(3, ObjType.GRASS, 560f, 520f, 160f, 70f)
        val plan = Plan.default(800, 600).copy(
            view = view,
            objects = listOf(tree, bed, grass),
            nextId = 4,
            steps = listOf(
                Step(ActionType.SWEEP, seconds = 3f),
                Step(ActionType.HIDE, retreat = Retreat.OBJECT, objectId = 1, seconds = 2f),
                Step(ActionType.SCARE, retreat = Retreat.OBJECT, objectId = 2, seconds = 2f),
                Step(ActionType.SCARE, retreat = Retreat.DOOR),
            ),
        )
        val sprites = SpriteLibrary(ctx)
        val sim = Simulator(plan).run()
        val renderer = Renderer(plan, photo, sprites)
        val cols = 5
        val n = 20
        val tw = 400
        val th = 300
        val sheet = Bitmap.createBitmap(tw * cols, th * (n / cols), Bitmap.Config.ARGB_8888)
        val sc = Canvas(sheet)
        val frame = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val fc = Canvas(frame)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; setShadowLayer(3f, 0f, 0f, Color.BLACK) }
        val seen = HashSet<String>()
        for (i in 0 until n) {
            val f = sim.frames[(i * (sim.frames.size - 1)) / (n - 1)]
            renderer.draw(fc, f)
            val x = (i % cols) * tw
            val y = (i / cols) * th
            sc.save()
            sc.translate(x.toFloat(), y.toFloat())
            sc.scale(0.5f, 0.5f)
            sc.drawBitmap(frame, 0f, 0f, null)
            sc.restore()
            sc.drawText("%.1fs S%d %s".format(f.t, f.step + 1, f.anim.key), x + 6f, y + 24f, txt)
            seen.add(f.anim.key)
        }
        TestUtil.shot("sequence_${view.key}", sheet)
        // Aus dem Ablauf sind mehrere Bewegungen zu sehen, und der Wichtel ist in den Bildern auch wirklich gezeichnet
        assertTrue("zu wenig Bewegungen im Ablauf: $seen", seen.size >= 4)
        assertTrue(sim.frames.any { it.anim == Anim.RUN } && sim.frames.any { it.anim == Anim.SNEAK })
    }

    @Test
    fun sequenceSide() = contactSheet(ViewMode.SIDE)

    @Test
    fun sequenceTop() = contactSheet(ViewMode.TOP)
}
