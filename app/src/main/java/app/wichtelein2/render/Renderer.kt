package app.wichtelein2.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import app.wichtelein2.core.Anim
import app.wichtelein2.core.Frame
import app.wichtelein2.core.Hinge
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.OpenDir
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Pt
import app.wichtelein2.core.SceneObject
import app.wichtelein2.core.Scaler
import app.wichtelein2.core.ShapeKind
import app.wichtelein2.core.ViewMode

/**
 * Zeichnet eine Szene (Foto, Tür mit Innenraum, Wichtel, verdeckende Gegenstände) in ein Canvas.
 * Das Canvas muss schon so skaliert sein, dass 1 Einheit = 1 Foto-Pixel des Plans ist.
 */
class Renderer(
    private val plan: Plan,
    private val photo: Bitmap,
    private val sprites: SpriteLibrary,
    private val customInterior: Bitmap? = null,
) {
    private val scaler = Scaler(plan)
    private val photoRect = RectF(0f, 0f, plan.width.toFloat(), plan.height.toFloat())
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dstIn = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val black = Paint().apply { color = 0xFF000000.toInt() }
    private val doorMatrix = Matrix()
    private val tmpRect = RectF()

    /** Ganze Szene für ein Bild des Ablaufs; f = null: nur Foto und geschlossene Tür. */
    fun draw(canvas: Canvas, f: Frame?) {
        canvas.drawBitmap(photo, null, photoRect, null)
        val houseT = f?.houseT ?: 1f
        val open = f?.doorOpen ?: 0f
        drawDoorBack(canvas, f, houseT)
        drawDoorFront(canvas, open)
        if (f != null && houseT <= 0.5f) {
            drawWichtel(canvas, f.x, f.y, f.dir, f.anim, f.frame, houseT)
        }
        if (f != null && houseT <= 0.5f) drawOccluders(canvas, f)
    }

    /** Nur das Foto (zum Zeichnen der Bearbeitungshilfen darüber). */
    fun drawPhoto(canvas: Canvas) {
        canvas.drawBitmap(photo, null, photoRect, null)
    }

    // ------------------------------------------------------------------ Tür
    private fun updateDoorMatrix() {
        val d = plan.door
        val dm = sprites.doorMeta(d.shape)
        doorMatrix.reset()
        doorMatrix.postTranslate(-(dm.x + dm.bw / 2f), -(dm.y + dm.bh).toFloat())
        if (d.hinge == Hinge.RIGHT) doorMatrix.postScale(-1f, 1f)
        doorMatrix.postScale(d.w / dm.bw, d.h / dm.bh)
        doorMatrix.postRotate(d.rot)
        doorMatrix.postTranslate(d.x, d.y)
    }

    private fun interiorBitmap(): Bitmap? = when (plan.door.interior) {
        InteriorKind.BLACK -> null
        InteriorKind.CUSTOM -> customInterior
        else -> sprites.interior(plan.door.interior)
    }

    /** Hinter der Tür: Innenraum (durch die Türöffnung begrenzt) und der Wichtel, solange er überwiegend drinnen ist. */
    @Suppress("DEPRECATION")
    private fun drawDoorBack(canvas: Canvas, f: Frame?, houseT: Float) {
        val d = plan.door
        val dm = sprites.doorMeta(d.shape)
        updateDoorMatrix()
        tmpRect.set(0f, 0f, dm.w.toFloat(), dm.h.toFloat())
        doorMatrix.mapRect(tmpRect)
        val layer = canvas.saveLayer(tmpRect, null)
        canvas.save()
        canvas.concat(doorMatrix)
        val inner = RectF(dm.x.toFloat(), dm.y.toFloat(), (dm.x + dm.bw).toFloat(), (dm.y + dm.bh).toFloat())
        val bmp = interiorBitmap()
        if (bmp != null) canvas.drawBitmap(bmp, null, inner, paint) else canvas.drawRect(inner, black)
        canvas.restore()
        if (f != null && houseT > 0.5f) drawWichtel(canvas, f.x, f.y, f.dir, f.anim, f.frame, houseT)
        canvas.save()
        canvas.concat(doorMatrix)
        canvas.drawBitmap(sprites.doorHole(d.shape), 0f, 0f, dstIn)
        canvas.restore()
        canvas.restoreToCount(layer)
    }

    private fun angleKey(open: Float): String {
        val idx = (open.coerceIn(0f, 1f) * 5f + 0.5f).toInt().coerceIn(0, 5)
        if (idx == 0) return "0"
        val deg = intArrayOf(18, 36, 54, 72, 90)[idx - 1]
        return (if (plan.door.openDir == OpenDir.OUT) "o" else "i") + deg
    }

    private fun drawDoorFront(canvas: Canvas, open: Float) {
        val d = plan.door
        updateDoorMatrix()
        canvas.save()
        canvas.concat(doorMatrix)
        canvas.drawBitmap(sprites.door(d.shape, angleKey(open)), 0f, 0f, paint)
        canvas.restore()
    }

    // ------------------------------------------------------------------ Wichtel
    private fun smooth(u: Float): Float {
        val c = u.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** Zeichnet den Wichtel mit den Füßen bei (x, y); houseT blendet ihn beim Betreten der Tür aus. */
    fun drawWichtel(canvas: Canvas, x: Float, y: Float, dir: Int, anim: Anim, frame: Int, houseT: Float, scaleExtra: Float = 1f) {
        val meta = sprites.meta
        val hpx = scaler.heightAt(y) * scaleExtra
        val alpha = 1f - smooth((houseT - 0.55f) / 0.45f)
        if (alpha <= 0.01f) return
        val k = 1f - 0.30f * houseT
        val s = hpx * k / meta.heightPx
        val yOff = -0.10f * hpx * houseT
        val sheet = sprites.sheet(plan.view, anim)
        val c = meta.cell
        val src = Rect(frame * c, dir * c, (frame + 1) * c, (dir + 1) * c)
        val left = x - meta.feetX * s
        val top = y + yOff - meta.feetY * s
        // weicher Schatten am Boden
        val rx = 0.24f * hpx * k
        val ry = (if (plan.view == ViewMode.SIDE) 0.055f else 0.10f) * hpx * k
        shadowPaint.color = ((0x55 * alpha).toInt() shl 24)
        canvas.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), shadowPaint)
        paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(sheet, src, RectF(left, top, left + c * s, top + c * s), paint)
        paint.alpha = 255
    }

    // ------------------------------------------------------------------ Verdeckung durch Gegenstände
    /** Berührt der Körper des Wichtels (Fuß bei foot) den markierten Bereich des Gegenstands? */
    fun bodyOverlaps(o: SceneObject, foot: Pt): Boolean {
        val h = scaler.heightAt(foot.y)
        val xs = floatArrayOf(-0.22f * h, 0f, 0.22f * h)
        val ys = floatArrayOf(0f, -0.25f * h, -0.5f * h, -0.75f * h)
        for (dx in xs) for (dy in ys) if (o.contains(Pt(foot.x + dx, foot.y + dy))) return true
        return false
    }

    fun occludes(o: SceneObject, foot: Pt): Boolean =
        if (plan.view == ViewMode.SIDE) {
            foot.y < o.baseY && bodyOverlaps(o, foot)
        } else {
            o.type.hideTop && o.contains(foot)
        }

    @Suppress("DEPRECATION")
    private fun drawOccluders(canvas: Canvas, f: Frame) {
        val foot = Pt(f.x, f.y)
        for (o in plan.objects) {
            if (!occludes(o, foot)) continue
            val m = Matrix()
            m.postRotate(o.rot)
            m.postTranslate(o.x, o.y)
            val shape = Path()
            val r = RectF(-o.w / 2f, -o.h / 2f, o.w / 2f, o.h / 2f)
            if (o.shape == ShapeKind.OVAL) shape.addOval(r, Path.Direction.CW) else shape.addRect(r, Path.Direction.CW)
            shape.transform(m)
            val bounds = RectF()
            shape.computeBounds(bounds, true)
            bounds.inset(-2f, -2f)
            val layer = canvas.saveLayer(bounds, null)
            canvas.drawBitmap(photo, null, photoRect, null)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            canvas.drawPath(shape, maskPaint)
            canvas.restoreToCount(layer)
        }
    }
}
