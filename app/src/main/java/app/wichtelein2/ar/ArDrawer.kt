package app.wichtelein2.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import app.wichtelein2.core.ArMath
import app.wichtelein2.core.ArSnapshot
import app.wichtelein2.core.ArView
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.Frame
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Step
import app.wichtelein2.core.ViewMode
import app.wichtelein2.render.Renderer
import app.wichtelein2.render.SpriteLibrary

/**
 * Zeichnet Wichteltür und Wichtel in den Raum der AR-Kamera: Die Tür steht aufrecht am Anker auf dem Boden,
 * der Wichtel läuft auf dem Boden davor. Von oben gesehen nimmt er die Draufsicht-Bilder, von der Seite die seitlichen,
 * und er dreht sich passend zum Betrachter, wenn man um ihn herumgeht.
 */
class ArDrawer(private val sprites: SpriteLibrary, private val interior: Bitmap? = null) {
    /** Ablauf-Plan im AR-Raum: 1 Einheit ≈ 1 mm, Tür in der Mitte (Ansicht „von oben“ = gleiche Größe überall). */
    fun planFor(steps: List<Step>): Plan {
        val base = Plan.default(2000, 1400).copy(view = ViewMode.TOP, steps = steps)
        val dh = 300f
        val door = base.door.copy(x = 1000f, y = 700f, w = dh * DoorShape.ARCH.aspect, h = dh, shape = DoorShape.ARCH)
        return base.copy(door = door)
    }

    private val blank: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var renderer: Renderer? = null
    private var rendererPlan: Plan? = null

    private fun rendererFor(plan: Plan): Renderer {
        val r = renderer
        if (r != null && rendererPlan == plan) return r
        return Renderer(plan, blank, sprites, interior).also { renderer = it; rendererPlan = plan }
    }

    /**
     * Zeichnet ein Bild des Ablaufs; [doorMeters] ist die Höhe der Tür in Metern (der Wichtel ist so hoch wie
     * Tür × Verhältnis des Plans). Liefert false, wenn nichts gezeichnet werden konnte (kein Anker, Tür hinter der Kamera).
     */
    fun draw(canvas: Canvas, plan: Plan, snap: ArSnapshot, f: Frame, doorMeters: Float): Boolean {
        val mpu = doorMeters / plan.door.h
        val view = ArView(snap, plan.door.x, plan.door.y, mpu)
        val doorFoot = view.foot(plan.door.x, plan.door.y) ?: return false
        val doorPx = view.heightPx(plan.door.x, plan.door.y, doorMeters)
        if (doorPx < 2f) return false
        val r = rendererFor(plan)

        // Tür (mit Innenraum und Wichtel, solange er drinnen ist): flaches Bild, das zur Kamera zeigt
        val k = doorPx / plan.door.h
        canvas.save()
        canvas.translate(doorFoot.x, doorFoot.y)
        canvas.scale(k, k)
        canvas.translate(-plan.door.x, -plan.door.y)
        r.drawDoorBack(canvas, f, f.houseT)
        r.drawDoorFront(canvas, f.doorOpen)
        canvas.restore()

        if (f.houseT <= 0.5f) {
            val foot = view.foot(f.x, f.y)
            if (foot != null) {
                val hpx = view.heightPx(f.x, f.y, doorMeters * plan.wichtelRatio)
                val mode = ArMath.viewFor(view.elevationDeg(f.x, f.y))
                val dir = ArMath.cameraDir(f.dir, view.azimuthDeg(f.x, f.y))
                r.drawWichtelPx(canvas, mode, foot.x, foot.y, hpx, dir, f.anim, f.frame, f.houseT)
            }
        }
        return true
    }
}
