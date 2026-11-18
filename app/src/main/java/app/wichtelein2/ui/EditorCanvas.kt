package app.wichtelein2.ui

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.wichtelein2.core.Anim
import app.wichtelein2.core.EditOps
import app.wichtelein2.core.Layer
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Pt
import app.wichtelein2.core.Scaler
import app.wichtelein2.core.Sel
import app.wichtelein2.core.ShapeKind
import app.wichtelein2.core.ViewMode
import app.wichtelein2.render.Renderer
import app.wichtelein2.render.SpriteLibrary

private enum class DragMode { None, Move, Resize }

private fun typeColor(t: ObjType): Int = when (t) {
    ObjType.BED -> 0xFF8E24AA.toInt()
    ObjType.TREE -> 0xFF2E7D32.toInt()
    ObjType.HOUSE -> 0xFFE64A19.toInt()
    ObjType.LADDER -> 0xFFF9A825.toInt()
    ObjType.STAIRS -> 0xFFFFB300.toInt()
    ObjType.GRASS -> 0xFF7CB342.toInt()
    ObjType.BUSH -> 0xFF00897B.toInt()
    ObjType.STONE -> 0xFF607D8B.toInt()
    ObjType.FENCE -> 0xFF795548.toInt()
    ObjType.TABLE -> 0xFF1E88E5.toInt()
    ObjType.BOX -> 0xFFD81B60.toInt()
}

/**
 * Foto mit Tür, markierten Gegenständen, Horizont und (auf Wunsch) Wichtel-Größenvorschau. Antippen wählt aus (nur in der
 * aktiven Ebene, alles andere ist gesperrt und blass), Ziehen verschiebt nur die Auswahl, der Griff unten rechts ändert die
 * Größe. pickPoint: nächster Fingertipp setzt einen Ort.
 */
@Composable
fun EditorCanvas(
    plan: Plan,
    photo: Bitmap,
    sprites: SpriteLibrary,
    customInterior: Bitmap?,
    spritesReady: Boolean,
    sel: Sel,
    layer: Layer,
    activeStep: Int,
    showWichtel: Boolean,
    pickPoint: Boolean,
    onSel: (Sel) -> Unit,
    onPlan: (Plan) -> Unit,
    onPicked: (Pt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = remember(plan, photo, customInterior) { Renderer(plan, photo, sprites, customInterior) }
    val planState = rememberUpdatedState(plan)
    val selState = rememberUpdatedState(sel)
    val layerState = rememberUpdatedState(layer)
    val pickState = rememberUpdatedState(pickPoint)
    val onSelC = rememberUpdatedState(onSel)
    val onPlanC = rememberUpdatedState(onPlan)
    val onPickedC = rememberUpdatedState(onPicked)
    val density = LocalDensity.current.density
    // Fingerauflage-Punkt (Bildpunkte im Canvas), damit der Treffertest dort ansetzt und nicht erst nach dem Tippabstand
    val downPos = remember { floatArrayOf(0f, 0f) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .aspectRatio(plan.width.toFloat() / plan.height.toFloat())
                .testTag("editor_canvas")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        downPos[0] = d.position.x
                        downPos[1] = d.position.y
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                        } while (e.changes.any { it.pressed })
                    }
                }
                .pointerInput(plan.width, plan.height) {
                    detectTapGestures { off ->
                        val k = size.width / planState.value.width.toFloat()
                        val p = Pt(off.x / k, off.y / k)
                        if (pickState.value) onPickedC.value(p) else onSelC.value(EditOps.hit(planState.value, p, 28f * density / k, layerState.value))
                    }
                }
                .pointerInput(plan.width, plan.height) {
                    var mode = DragMode.None
                    var target: Sel = Sel.None
                    var working: Plan = planState.value   // Zwischenstand, damit schnelle Ziehbewegungen nichts verlieren
                    detectDragGestures(
                        onDragStart = { _ ->
                            val k = size.width / planState.value.width.toFloat()
                            val p = Pt(downPos[0] / k, downPos[1] / k)
                            val tol = 28f * density / k
                            working = planState.value
                            val h = EditOps.handle(planState.value, selState.value)
                            // Griff nur nahe an seinem Kreis, damit kleine Türen und Gegenstände am Körper verschiebbar bleiben
                            if (h != null && h.dist(p) <= 18f * density / k) {
                                mode = DragMode.Resize
                            } else if (EditOps.hitsSelection(planState.value, selState.value, p, tol)) {
                                // Verschoben wird nur, was gerade ausgewählt ist; alles andere bleibt, wo es ist
                                mode = DragMode.Move
                                target = selState.value
                            } else {
                                mode = DragMode.None
                            }
                            if (mode == DragMode.Resize) target = selState.value
                        },
                        onDragEnd = { mode = DragMode.None },
                        onDragCancel = { mode = DragMode.None },
                        onDrag = { change, drag ->
                            val k = size.width / planState.value.width.toFloat()
                            when (mode) {
                                DragMode.Move -> {
                                    change.consume()
                                    working = EditOps.move(working, target, drag.x / k, drag.y / k)
                                    onPlanC.value(working)
                                }
                                DragMode.Resize -> {
                                    change.consume()
                                    working = EditOps.dragHandle(working, target, Pt(change.position.x / k, change.position.y / k))
                                    onPlanC.value(working)
                                }
                                DragMode.None -> Unit
                            }
                        },
                    )
                },
        ) {
            val k = size.width / plan.width.toFloat()
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.scale(k, k)
                renderer.draw(nc, null)
                if (spritesReady && showWichtel) {
                    val sc = Scaler(plan)
                    val d = plan.door
                    val outY = d.y + 0.55f * sc.heightAt(d.y)
                    renderer.drawWichtel(nc, d.x, outY, 0, Anim.IDLE, 0, 0f)
                }
                drawOverlays(nc, plan, sel, k, density, layer, activeStep)
                nc.restore()
            }
        }
    }
}

/** Blasse Farbe für gesperrte (nicht aktive) Ebenen. */
private fun dim(col: Int, active: Boolean): Int =
    if (active) col else (col and 0x00FFFFFF) or ((((col ushr 24) and 0xFF) * 0.35f).toInt() shl 24)

private fun drawOverlays(nc: android.graphics.Canvas, plan: Plan, sel: Sel, k: Float, density: Float, layer: Layer, activeStep: Int) {
    val px = 1f / k            // 1 Bildschirm-Pixel in Foto-Pixeln
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * density * px; color = 0xFFFFFFFF.toInt(); setShadowLayer(3f * px, 0f, 0f, 0xFF000000.toInt()) }

    // Horizont
    if (plan.view == ViewMode.SIDE) {
        val act = layer == Layer.HORIZON
        val on = sel == Sel.Horizon
        line.color = dim(if (on) 0xFFFFEB3B.toInt() else 0xAAFFFFFF.toInt(), act)
        line.strokeWidth = (if (on) 3f else 1.5f) * density * px
        line.pathEffect = DashPathEffect(floatArrayOf(14f * density * px, 8f * density * px), 0f)
        nc.drawLine(0f, plan.horizon, plan.width.toFloat(), plan.horizon, line)
        line.pathEffect = null
        text.color = dim(0xFFFFFFFF.toInt(), act)
        nc.drawText("Horizont", 6f * density * px, plan.horizon - 5f * density * px, text)
        text.color = 0xFFFFFFFF.toInt()
    }

    // Gegenstände
    val objAct = layer == Layer.OBJECTS
    for (o in plan.objects) {
        val on = sel == Sel.Obj(o.id)
        val col = typeColor(o.type)
        nc.save()
        nc.translate(o.x, o.y)
        nc.rotate(o.rot)
        val r = RectF(-o.w / 2f, -o.h / 2f, o.w / 2f, o.h / 2f)
        fill.color = dim((col and 0x00FFFFFF) or 0x40000000, objAct)
        line.color = dim(col, objAct)
        line.strokeWidth = (if (on) 3.5f else 2f) * density * px
        if (o.shape == ShapeKind.OVAL) {
            nc.drawOval(r, fill)
            nc.drawOval(r, line)
        } else {
            nc.drawRect(r, fill)
            nc.drawRect(r, line)
        }
        // Leiter: Holme und Sprossen; Treppe: Stufen, die in Steigrichtung ansteigen (Pfeil nach oben)
        if (o.type == ObjType.LADDER) {
            line.strokeWidth = 2f * density * px
            val rx = o.w * 0.28f
            nc.drawLine(-rx, -o.h / 2f, -rx, o.h / 2f, line)
            nc.drawLine(rx, -o.h / 2f, rx, o.h / 2f, line)
            val rungs = 8
            for (k in 1 until rungs) {
                val yy = -o.h / 2f + o.h * k / rungs
                nc.drawLine(-rx, yy, rx, yy, line)
            }
        } else if (o.type == ObjType.STAIRS) {
            line.strokeWidth = 2.5f * density * px
            val right = EditOps.stairsRiseRight(plan, o)
            val n = 5
            val path = Path()
            fun sx(i: Int) = (if (right) -o.w / 2f + o.w * i / n else o.w / 2f - o.w * i / n)
            path.moveTo(sx(0), o.h / 2f)
            for (k in 0 until n) {
                val top = o.h / 2f - o.h * (k + 1) / n
                path.lineTo(sx(k), top)
                path.lineTo(sx(k + 1), top)
            }
            nc.drawPath(path, line)
        }
        // Standfläche, die der Wichtel nicht betreten darf
        val f = o.footprintLocal(plan.view)
        if (f != null) {
            line.color = dim(0xCCFF1744.toInt(), objAct)
            line.strokeWidth = 1.5f * density * px
            line.pathEffect = DashPathEffect(floatArrayOf(6f * density * px, 4f * density * px), 0f)
            nc.drawRect(f[0], f[1], f[2], f[3], line)
            line.pathEffect = null
        }
        text.color = dim(0xFFFFFFFF.toInt(), objAct)
        nc.drawText(o.type.label, -o.w / 2f + 4f * density * px, -o.h / 2f + 15f * density * px, text)
        text.color = 0xFFFFFFFF.toInt()
        nc.restore()
    }

    // Tür
    val doorAct = layer == Layer.DOOR
    val d = plan.door
    nc.save()
    nc.translate(d.x, d.y)
    nc.rotate(d.rot)
    val dOn = sel == Sel.Door
    line.color = dim(if (dOn) 0xFFFFEB3B.toInt() else 0x99FFFFFF.toInt(), doorAct)
    line.strokeWidth = (if (dOn) 3f else 1.2f) * density * px
    nc.drawRect(-d.w / 2f, -d.h, d.w / 2f, 0f, line)
    // Angelseite markieren
    val hx = if (d.hinge == app.wichtelein2.core.Hinge.LEFT) -d.w / 2f else d.w / 2f
    fill.color = dim(0xFFFFEB3B.toInt(), doorAct)
    nc.drawCircle(hx, -d.h * 0.25f, 5f * density * px, fill)
    nc.drawCircle(hx, -d.h * 0.75f, 5f * density * px, fill)
    nc.restore()

    // Schritt-Orte; bei Leiter/Treppe der Pfad (unten → oben) als Linie mit Pfeilspitze
    val stepsAct = layer == Layer.STEPS
    plan.steps.forEachIndexed { i, st ->
        val (at, to) = EditOps.stepPoints(plan, st)
        val cur = stepsAct && i == activeStep
        if (at != null && to != null) {
            line.color = dim(if (cur) 0xFFFFEB3B.toInt() else 0xFF1E88E5.toInt(), cur)
            line.strokeWidth = (if (cur) 4f else 2.5f) * density * px
            nc.drawLine(at.x, at.y, to.x, to.y, line)
            val ang = Math.atan2((to.y - at.y).toDouble(), (to.x - at.x).toDouble())
            val ah = 12f * density * px
            for (da in doubleArrayOf(2.6, -2.6)) {
                nc.drawLine(to.x, to.y, (to.x + ah * Math.cos(ang + da)).toFloat(), (to.y + ah * Math.sin(ang + da)).toFloat(), line)
            }
        }
        fun marker(pt: Pt, label: String, on: Boolean) {
            fill.color = dim(if (on) 0xFFFFEB3B.toInt() else 0xFF1E88E5.toInt(), cur)
            nc.drawCircle(pt.x, pt.y, 13f * density * px, fill)
            text.color = dim(if (on) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), cur)
            text.textAlign = Paint.Align.CENTER
            nc.drawText(label, pt.x, pt.y + 5f * density * px, text)
            text.textAlign = Paint.Align.LEFT
            text.color = 0xFFFFFFFF.toInt()
        }
        if (at != null) marker(at, "${i + 1}", sel == Sel.StepAt(i))
        if (to != null) marker(to, "${i + 1}↑", sel == Sel.StepTo(i))
    }

    // Griff zum Ändern der Größe
    val h = EditOps.handle(plan, sel)
    if (h != null) {
        fill.color = 0xFFFFFFFF.toInt()
        nc.drawCircle(h.x, h.y, 11f * density * px, fill)
        line.color = 0xFF000000.toInt()
        line.strokeWidth = 2f * density * px
        nc.drawCircle(h.x, h.y, 11f * density * px, line)
    }
}
