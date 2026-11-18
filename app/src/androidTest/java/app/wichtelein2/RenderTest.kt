package app.wichtelein2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.core.Anim
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.Frame
import app.wichtelein2.core.Hinge
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.OpenDir
import app.wichtelein2.core.Plan
import app.wichtelein2.core.SceneObject
import app.wichtelein2.core.Scaler
import app.wichtelein2.core.ViewMode
import app.wichtelein2.core.WichtelColors
import app.wichtelein2.render.Renderer
import app.wichtelein2.render.SpriteLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderTest {
    private val ctx = TestUtil.context

    private fun frame(x: Float, y: Float, anim: Anim = Anim.IDLE, frame: Int = 0, dir: Int = 0, houseT: Float = 0f, open: Float = 0f) =
        Frame(0f, x, y, dir, anim, frame, houseT, open, 0)

    private fun render(plan: Plan, photo: Bitmap, sprites: SpriteLibrary, f: Frame?, interior: Bitmap? = null): Bitmap {
        val out = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        Renderer(plan, photo, sprites, interior).draw(Canvas(out), f)
        return out
    }

    @Test
    fun spriteSheetsLoadForBothViewsAndAllAnims() {
        val s = SpriteLibrary(ctx)
        assertEquals(224, s.meta.cell)
        for (v in ViewMode.entries) for (a in Anim.entries) {
            val b = s.sheet(v, a)
            assertEquals(s.meta.cell * a.frames, b.width)
            assertEquals(s.meta.cell * 8, b.height)
        }
        for (shape in DoorShape.entries) {
            assertNotNull(s.door(shape, "0"))
            assertNotNull(s.doorHole(shape))
        }
        assertNotNull(s.interior(InteriorKind.STUBE))
        assertNotNull(s.interior(InteriorKind.HOEHLE))
    }

    @Test
    fun recolorChangesHatToBlueAndBackToOriginal() {
        val s = SpriteLibrary(ctx)
        fun blueRed(b: Bitmap): Pair<Int, Int> {
            var blue = 0
            var red = 0
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            for (p in px) {
                if (Color.alpha(p) < 200) continue
                if (Color.blue(p) > Color.red(p) + 60 && Color.blue(p) > 120) blue++
                if (Color.red(p) > Color.blue(p) + 120 && Color.red(p) > 150 && Color.green(p) < 90) red++
            }
            return Pair(blue, red)
        }
        val orig = blueRed(s.sheet(ViewMode.SIDE, Anim.IDLE))
        s.setColors(WichtelColors(hat = Color.rgb(20, 60, 230)))
        val changed = blueRed(s.sheet(ViewMode.SIDE, Anim.IDLE))
        assertTrue("Mütze sollte blau werden: $orig -> $changed", changed.first > orig.first + 500)
        assertTrue("rot sollte verschwinden: $orig -> $changed", changed.second < orig.second / 4)
        s.setColors(WichtelColors())
        val back = blueRed(s.sheet(ViewMode.SIDE, Anim.IDLE))
        assertEquals(orig, back)
    }

    @Test
    fun wichtelAppearsAtFootPointAndShadowIsDrawn() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val plan = Plan.default(photo.width, photo.height)
        val base = render(plan, photo, s, null)
        val x = 300f
        val y = 450f
        val img = render(plan, photo, s, frame(x, y))
        val h = Scaler(plan).heightAt(y)
        val changed = TestUtil.diffPixels(base, img, (x - h / 2).toInt(), (y - h).toInt(), (x + h / 2).toInt(), (y + 4).toInt())
        assertTrue("Wichtel sichtbar, geänderte Pixel: $changed", changed > 1500)
        // weit weg vom Wichtel bleibt alles unverändert
        assertEquals(0, TestUtil.diffPixels(base, img, 520, 380, 700, 560))
        TestUtil.shot("render_wichtel_side", img)
    }

    @Test
    fun allAnimationsAndDirectionsDrawVisibleWichtel() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        for (view in ViewMode.entries) {
            val plan = Plan.default(photo.width, photo.height).copy(view = view)
            val base = render(plan, photo, s, null)
            for (a in Anim.entries) {
                for (d in 0 until 8) {
                    val f = frame(300f, 450f, a, a.frames - 1, d)
                    val img = render(plan, photo, s, f)
                    val h = Scaler(plan).heightAt(450f)
                    val changed = TestUtil.diffPixels(base, img, (300 - h / 2).toInt(), (450 - h).toInt(), (300 + h / 2).toInt(), 455)
                    assertTrue("$view ${a.key} Richtung $d unsichtbar", changed > 800)
                }
            }
        }
    }

    @Test
    fun doorShowsInteriorWhenOpenAndWichtelFadesInside() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val shots = ArrayList<Bitmap>()
        for (shape in DoorShape.entries) {
            for (interior in listOf(InteriorKind.BLACK, InteriorKind.STUBE, InteriorKind.HOEHLE)) {
                val plan = Plan.default(photo.width, photo.height).let { it.copy(door = it.door.copy(shape = shape, interior = interior, x = 400f, y = 420f, w = 120f, h = 190f)) }
                val closed = render(plan, photo, s, frame(0f, 0f, houseT = 1f, open = 0f))
                val open = render(plan, photo, s, frame(0f, 0f, houseT = 1f, open = 1f))
                // Mitte der Türöffnung: zu = Holz, offen = Innenraum
                val cx = 400
                val cy = 420 - 95
                val diff = TestUtil.diffPixels(closed, open, cx - 20, cy - 20, cx + 20, cy + 20, tol = 30)
                assertTrue("$shape/$interior: offene Tür zeigt nichts Neues ($diff)", diff > 200)
                if (interior == InteriorKind.BLACK) {
                    val p = open.getPixel(cx + 10, cy)
                    assertTrue("schwarzer Hintergrund erwartet", Color.red(p) < 40 && Color.green(p) < 40 && Color.blue(p) < 40)
                }
                if (interior == InteriorKind.STUBE) shots.add(open)
            }
        }
        TestUtil.shot("render_door_open_rect", shots[0])
        TestUtil.shot("render_door_open_arch", shots[1])
        TestUtil.shot("render_door_open_round", shots[2])
    }

    @Test
    fun hingeRightMirrorsTheOpenLeaf() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val base = Plan.default(photo.width, photo.height).let { it.copy(door = it.door.copy(shape = DoorShape.RECT, x = 400f, y = 420f, w = 120f, h = 190f, openDir = OpenDir.OUT)) }
        val left = render(base, photo, s, frame(0f, 0f, houseT = 1f, open = 0.6f))
        val right = render(base.copy(door = base.door.copy(hinge = Hinge.RIGHT)), photo, s, frame(0f, 0f, houseT = 1f, open = 0.6f))
        val flipped = Bitmap.createBitmap(left.width, left.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(flipped)
        c.scale(-1f, 1f, 400f, 0f)
        c.drawBitmap(left, 0f, 0f, null)
        // Foto selbst ist nicht symmetrisch, deshalb nur den Türbereich vergleichen: dort muss das Blatt gespiegelt liegen
        val dl = TestUtil.diffPixels(left, right, 330, 220, 470, 425, tol = 20)
        assertTrue("Angelseite rechts muss anders aussehen als links ($dl)", dl > 800)
        TestUtil.shot("render_door_hinge_right", right)
    }

    @Test
    fun objectInFrontOfWichtelHidesHimButNotWhenWichtelIsInFront() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val tree = SceneObject(1, ObjType.TREE, 250f, 360f, 220f, 330f)   // Unterkante bei y = 525
        val plan = Plan.default(photo.width, photo.height).copy(objects = listOf(tree), nextId = 5)
        val empty = plan.copy(objects = emptyList())
        // Wichtel steht dahinter (Fuß über der Unterkante) → der Baum aus dem Foto deckt ihn ab
        val behind = frame(250f, 450f)
        val withTree = render(plan, photo, s, behind)
        val clean = render(empty, photo, s, null)
        val inside = TestUtil.diffPixels(withTree, clean, 215, 380, 285, 430)
        assertTrue("hinter dem Baum nichts mehr zu sehen, aber $inside Pixel weichen ab", inside < 60)
        // ohne Baum-Markierung wäre er zu sehen
        val visible = render(empty, photo, s, behind)
        assertTrue(TestUtil.diffPixels(visible, clean, 215, 380, 285, 430) > 300)
        // davor (Fuß unter der Unterkante) bleibt er sichtbar
        val front = render(plan, photo, s, frame(250f, 560f))
        assertTrue(TestUtil.diffPixels(front, clean, 215, 480, 285, 560) > 300)
        TestUtil.shot("render_occlusion", withTree)
    }

    @Test
    fun topViewOccludesOnlyOverheadObjectsWhenInside() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val tree = SceneObject(1, ObjType.TREE, 250f, 360f, 220f, 330f)
        val house = SceneObject(2, ObjType.HOUSE, 600f, 300f, 200f, 200f)
        val plan = Plan.default(photo.width, photo.height).copy(view = ViewMode.TOP, objects = listOf(tree, house), nextId = 5)
        val r = Renderer(plan, photo, s)
        assertTrue(r.occludes(tree, app.wichtelein2.core.Pt(250f, 360f)))
        assertTrue(!r.occludes(house, app.wichtelein2.core.Pt(600f, 300f)))
        assertTrue(!r.occludes(tree, app.wichtelein2.core.Pt(700f, 500f)))
    }

    @Test
    fun climbingAnimationsExistAndTheClimbedObjectDoesNotCoverTheWichtel() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val tree = app.wichtelein2.core.SceneObject(7, app.wichtelein2.core.ObjType.TREE, 300f, 400f, 240f, 300f)
        val plan = Plan.default(photo.width, photo.height).copy(objects = listOf(tree), nextId = 8)
        val base = render(plan, photo, s, null)
        val x = 300f
        val y = 480f          // Fuß liegt über der Unterkante des Gegenstands (y < 550) und mitten darin: er steht „dahinter“
        val h = Scaler(plan).heightAt(y)
        val covered = render(plan, photo, s, Frame(0f, x, y, 4, Anim.CLIMB, 3, 0f, 0f, 0))
        val free = render(plan, photo, s, Frame(0f, x, y, 4, Anim.CLIMB, 3, 0f, 0f, 0, ignore = 7))
        val region = intArrayOf((x - h / 2).toInt(), (y - h).toInt(), (x + h / 2).toInt(), (y + 4).toInt())
        val c = TestUtil.diffPixels(base, covered, region[0], region[1], region[2], region[3])
        val f = TestUtil.diffPixels(base, free, region[0], region[1], region[2], region[3])
        assertTrue("ohne ignore verdeckt der Gegenstand ($c), mit ignore sieht man den Wichtel ($f)", f > c + 800)
        TestUtil.shot("render_klettern", free)
    }

    @Test
    fun fixedScaleReferenceKeepsTheClimberTheSameSize() {
        val s = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val plan = Plan.default(photo.width, photo.height)
        val base = render(plan, photo, s, null)
        val x = 300f
        val high = 300f      // weit oben im Foto (nahe Horizont): normal deutlich kleiner
        val natural = render(plan, photo, s, Frame(0f, x, high, 4, Anim.STAIRS, 2, 0f, 0f, 0))
        val locked = render(plan, photo, s, Frame(0f, x, high, 4, Anim.STAIRS, 2, 0f, 0f, 0, refY = 480f))
        val hLocked = Scaler(plan).heightAt(480f)
        val box = intArrayOf((x - hLocked / 2).toInt(), (high - hLocked).toInt().coerceAtLeast(0), (x + hLocked / 2).toInt(), (high + 4).toInt())
        val n = TestUtil.diffPixels(base, natural, box[0], box[1], box[2], box[3])
        val l = TestUtil.diffPixels(base, locked, box[0], box[1], box[2], box[3])
        assertTrue("mit fester Größe füllt er mehr Fläche ($l) als mit Tiefenverkleinerung ($n)", l > n + 600)
    }

    @Test
    fun houseAroundTheDoorNeverHidesWichtelInTheDoorway() {
        val sp = SpriteLibrary(ctx)
        val photo = TestUtil.photo()
        val d0 = Plan.default(photo.width, photo.height).door
        val house = SceneObject(1, ObjType.HOUSE, d0.x, d0.y - 60f, 600f, 400f)
        val plan = Plan.default(photo.width, photo.height).copy(objects = listOf(house), nextId = 2)
        val foot = (plan.door.y + 20f)
        assertTrue("Fuß liegt hinter der Hausunterkante", foot < house.baseY)
        val base = render(plan, photo, sp, frame(plan.door.x, foot, houseT = 0.01f, open = 1f).copy(anim = Anim.IDLE).let { it.copy(x = -500f) })
        val img = render(plan, photo, sp, frame(plan.door.x, foot, Anim.WALK, 2, 4, houseT = 0.3f, open = 1f))
        val h = Scaler(plan).heightAt(foot)
        val changed = TestUtil.diffPixels(base, img, (plan.door.x - h / 2).toInt(), (foot - h).toInt(), (plan.door.x + h / 2).toInt(), foot.toInt())
        assertTrue("Wichtel im Türrahmen sichtbar, geänderte Pixel: $changed", changed > 800)
        TestUtil.shot("render_haus_um_tuer", img)
    }
}
