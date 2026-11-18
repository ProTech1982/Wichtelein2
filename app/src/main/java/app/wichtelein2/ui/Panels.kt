package app.wichtelein2.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.EditOps
import app.wichtelein2.core.Hinge
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.OpenDir
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Retreat
import app.wichtelein2.core.Sel
import app.wichtelein2.core.ShapeKind
import app.wichtelein2.core.ViewMode
import kotlinx.coroutines.delay

/** Knopf, der beim Gedrückthalten wiederholt (Pfeiltasten zum feinen Platzieren); ein kurzes Tippen macht genau einen Schritt. */
@Composable
fun PadButton(label: String, tag: String, modifier: Modifier = Modifier, onStep: () -> Unit) {
    val current by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var repeating by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            repeating = false
            delay(380)
            repeating = true
            while (true) {
                current()
                delay(70)
            }
        }
    }
    Box(
        modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .testTag(tag)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClickLabel = label) {
                if (!repeating) current()
                repeating = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Platzieren: Pfeile hoch/runter/links/rechts, Drehen, Größe (gesamt, Breite, Höhe) für die Auswahl.
 * Je nach Auswahl sind nicht alle Knöpfe sinnvoll (Schrittort: nur Pfeile, Horizont: hoch/runter).
 */
@Composable
fun PlacePad(plan: Plan, sel: Sel, onPlan: (Plan) -> Unit) {
    val n = EditOps.nudge(plan)
    val canSize = sel == Sel.Door || sel is Sel.Obj
    val canMoveX = sel != Sel.Horizon
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PadButton("▲", "pad_up") { onPlan(EditOps.move(plan, sel, 0f, -n)) }
            Row {
                if (canMoveX) PadButton("◀", "pad_left") { onPlan(EditOps.move(plan, sel, -n, 0f)) } else Spacer(Modifier.size(46.dp))
                Spacer(Modifier.size(46.dp))
                if (canMoveX) PadButton("▶", "pad_right") { onPlan(EditOps.move(plan, sel, n, 0f)) } else Spacer(Modifier.size(46.dp))
            }
            PadButton("▼", "pad_down") { onPlan(EditOps.move(plan, sel, 0f, n)) }
        }
        if (canSize) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("⟲", "pad_rot_left") { onPlan(EditOps.rotate(plan, sel, -3f)) }
                    Text("Drehen", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
                    PadButton("⟳", "pad_rot_right") { onPlan(EditOps.rotate(plan, sel, 3f)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("−", "pad_size_minus") { onPlan(EditOps.scale(plan, sel, 0.97f)) }
                    Text("Größe", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
                    PadButton("+", "pad_size_plus") { onPlan(EditOps.scale(plan, sel, 1.03f)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("−", "pad_w_minus") { onPlan(EditOps.stretch(plan, sel, 0.97f, 1f)) }
                    Text("Breite", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
                    PadButton("+", "pad_w_plus") { onPlan(EditOps.stretch(plan, sel, 1.03f, 1f)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("−", "pad_h_minus") { onPlan(EditOps.stretch(plan, sel, 1f, 0.97f)) }
                    Text("Höhe", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
                    PadButton("+", "pad_h_plus") { onPlan(EditOps.stretch(plan, sel, 1f, 1.03f)) }
                }
            }
        }
    }
}

private fun selLabel(plan: Plan, sel: Sel): String = when (sel) {
    Sel.None -> "Nichts ausgewählt – im Foto antippen"
    Sel.Door -> "Ausgewählt: Tür"
    Sel.Horizon -> "Ausgewählt: Horizont"
    is Sel.Obj -> "Ausgewählt: " + (plan.objectById(sel.id)?.type?.label ?: "Gegenstand")
    is Sel.StepAt -> {
        val a = plan.steps.getOrNull(sel.index)?.action
        if (a != null && a.isClimb) "Ausgewählt: Pfad unten von Schritt ${sel.index + 1}" else "Ausgewählt: Ort von Schritt ${sel.index + 1}"
    }
    is Sel.StepTo -> "Ausgewählt: Pfad oben von Schritt ${sel.index + 1}"
}

/** Kopfzeile eines Bereichs: Auswahl-Text und Hilfe-Knopf. */
@Composable
private fun PanelHeader(plan: Plan, sel: Sel, title: String, help: String, tag: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(selLabel(plan, sel), fontSize = 12.sp, modifier = Modifier.weight(1f).testTag("sel_label"))
        HelpButton(title, help, tag)
    }
}

// ---------------------------------------------------------------------------------------- Tür
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoorPanel(plan: Plan, sel: Sel, hasCustomInterior: Boolean, onSel: (Sel) -> Unit, onPlan: (Plan) -> Unit, onPickInterior: () -> Unit) {
    val d = plan.door
    Column(Modifier.fillMaxWidth()) {
        PanelHeader(plan, sel, HelpTexts.DOOR_TITLE, HelpTexts.DOOR, "help_door")
        OutlinedButton(onClick = { onSel(Sel.Door) }, modifier = Modifier.testTag("select_door")) { Text("Tür auswählen") }
        PlacePad(plan, sel, onPlan)
        Text("Form", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DoorShape.entries.forEach { s ->
                FilterChip(d.shape == s, { onPlan(plan.copy(door = d.copy(shape = s))) }, { Text(s.label) }, modifier = Modifier.testTag("door_shape_${s.key}"))
            }
        }
        Text("Türangeln und Aufschlag (wie die echte Tür im Foto)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Hinge.entries.forEach { h ->
                FilterChip(d.hinge == h, { onPlan(plan.copy(door = d.copy(hinge = h))) }, { Text(h.label) }, modifier = Modifier.testTag("door_hinge_${h.name.lowercase()}"))
            }
            OpenDir.entries.forEach { o ->
                FilterChip(d.openDir == o, { onPlan(plan.copy(door = d.copy(openDir = o))) }, { Text(o.label) }, modifier = Modifier.testTag("door_open_${o.name.lowercase()}"))
            }
        }
        Text("Hinter der Tür", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InteriorKind.entries.forEach { k ->
                FilterChip(
                    d.interior == k,
                    {
                        if (k == InteriorKind.CUSTOM && !hasCustomInterior) onPickInterior()
                        onPlan(plan.copy(door = d.copy(interior = k)))
                    },
                    { Text(k.label) },
                    modifier = Modifier.testTag("door_interior_${k.name.lowercase()}"),
                )
            }
            if (d.interior == InteriorKind.CUSTOM) {
                OutlinedButton(onClick = onPickInterior, modifier = Modifier.testTag("door_interior_pick")) { Text("Bild ändern") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- Objekte
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObjectsPanel(plan: Plan, sel: Sel, onSel: (Sel) -> Unit, onPlan: (Plan) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Markiere Dinge aus dem Foto (Bett, Baum, Haus …). Der Wichtel läuft um sie herum und kann sich dahinter verstecken.",
            fontSize = 12.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ObjType.entries.filter { it != ObjType.LADDER && it != ObjType.STAIRS }.forEach { t ->
                OutlinedButton(
                    onClick = {
                        val q = EditOps.addObject(plan, t)
                        onPlan(q)
                        onSel(Sel.Obj(q.objects.last().id))
                    },
                    modifier = Modifier.testTag("add_${t.key}"),
                ) { Text("+ ${t.label}") }
            }
        }
        if (plan.objects.isNotEmpty()) {
            Text("Markiert (zum Bearbeiten antippen, der Rest ist gesperrt)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.objects.forEachIndexed { i, o ->
                    FilterChip(sel == Sel.Obj(o.id), { onSel(Sel.Obj(o.id)) }, { Text("${o.type.label} ${i + 1}") }, modifier = Modifier.testTag("obj_chip_${o.id}"))
                }
            }
        }
        PanelHeader(plan, sel, HelpTexts.OBJECTS_TITLE, HelpTexts.OBJECTS, "help_objects")
        val s = sel
        if (s is Sel.Obj) {
            val o = plan.objectById(s.id)
            if (o != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShapeKind.entries.forEach { k ->
                        FilterChip(o.shape == k, { onPlan(EditOps.setObjectShape(plan, o.id, k)) }, { Text(k.label) }, modifier = Modifier.testTag("obj_shape_${k.name.lowercase()}"))
                    }
                    OutlinedButton(onClick = { onPlan(EditOps.removeObject(plan, o.id)); onSel(Sel.None) }, modifier = Modifier.testTag("obj_delete")) { Text("Löschen") }
                }
                Text(HelpTexts.objectInfo(o.type, plan.view), fontSize = 11.sp, modifier = Modifier.testTag("obj_info"))
            }
        }
        PlacePad(plan, sel, onPlan)
    }
}

// ---------------------------------------------------------------------------------------- Ablauf
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepsPanel(
    plan: Plan,
    stepIdx: Int,
    onStepIdx: (Int) -> Unit,
    sel: Sel,
    onSel: (Sel) -> Unit,
    onPlan: (Plan) -> Unit,
    onPickPoint: (Int, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PanelHeader(plan, sel, HelpTexts.STEPS_TITLE, HelpTexts.STEPS, "help_steps")
        Text("Ablauf: Der Wichtel kommt aus der Tür, macht die Schritte der Reihe nach und geht wieder hinein.", fontSize = 12.sp)
        val i = stepIdx.coerceIn(0, plan.steps.size - 1)
        val st = plan.steps[i]
        // Wer einen Schritt unten anklickt, bearbeitet genau diesen: Auswahl im Foto und Pfeile folgen mit
        fun chooseStep(k: Int) {
            onStepIdx(k)
            val (a, _) = EditOps.stepPoints(plan, plan.steps[k])
            onSel(if (a != null) Sel.StepAt(k) else Sel.None)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            plan.steps.forEachIndexed { k, s ->
                FilterChip(i == k, { chooseStep(k) }, { Text("${k + 1} ${s.action.label}") }, modifier = Modifier.testTag("step_chip_$k"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionType.entries.forEach { a ->
                OutlinedButton(
                    onClick = {
                        val q = EditOps.addStep(plan, a)
                        onPlan(q)
                        onStepIdx(q.steps.size - 1)
                        onSel(Sel.None)
                    },
                    modifier = Modifier.testTag("add_step_${a.key}"),
                ) { Text("+ ${a.label}") }
            }
        }
        Text("Schritt ${i + 1} von ${plan.steps.size}", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionType.entries.forEach { a ->
                FilterChip(
                    st.action == a,
                    {
                        onPlan(EditOps.updateStep(plan, i) {
                            val t = it.copy(action = a)
                            // Gegenstand und Pfad gehören zur Aktion: beim Wechsel zwischen Leiter/Treppe/anderem zurücksetzen
                            if (a.isClimb != it.action.isClimb || (a.isClimb && a != it.action)) {
                                if (a.isClimb) {
                                    val pts = EditOps.climbDefaultPoints(plan, a)
                                    t.copy(at = pts.first, to = pts.second, objectId = -1)
                                } else t.copy(at = null, to = null, objectId = -1)
                            } else t
                        })
                        onSel(Sel.None)
                    },
                    { Text(a.label) },
                    modifier = Modifier.testTag("step_action_${a.key}"),
                )
            }
        }
        if (st.action == ActionType.HIDE || st.action == ActionType.SCARE) {
            Text(if (st.action == ActionType.HIDE) "Wohin verstecken?" else "Wohin flüchten?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Retreat.entries.forEach { r ->
                    FilterChip(st.retreat == r, { onPlan(EditOps.updateStep(plan, i) { it.copy(retreat = r) }) }, { Text(r.label) }, modifier = Modifier.testTag("step_retreat_${r.key}"))
                }
            }
            if (st.retreat == Retreat.OBJECT) {
                val cands = plan.objects.filter { it.canHide(plan.view) }
                if (cands.isEmpty()) {
                    Text("Noch kein Gegenstand zum Verstecken – im Tab „Objekte“ einen anlegen (Baum, Busch, Bett …).", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cands.forEach { o ->
                            FilterChip(st.objectId == o.id, { onPlan(EditOps.updateStep(plan, i) { it.copy(objectId = o.id) }) }, { Text("${o.type.label} ${plan.objects.indexOf(o) + 1}") }, modifier = Modifier.testTag("step_obj_${o.id}"))
                        }
                    }
                }
            }
        }
        if ((st.action == ActionType.SWEEP || st.action == ActionType.SCARE) && EditOps.onTopAt(plan, i)) {
            Text(
                "Der Wichtel steht hier oben auf der Leiter/Treppe (Punkt B): ${st.action.label} findet dort statt, der Ort wird nicht gebraucht. " +
                    "Erst ein Schritt „Hinunter“ oder Verstecken/Flucht bringt ihn wieder nach unten.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("on_top_hint"),
            )
        }
        if ((st.action == ActionType.SWEEP || st.action == ActionType.SCARE) && !EditOps.onTopAt(plan, i)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (st.action == ActionType.SWEEP) "Fegeort:" else "Ort des Schrecks:", fontSize = 13.sp)
                OutlinedButton(onClick = { onPickPoint(i, false) }, modifier = Modifier.testTag("step_pick_point")) {
                    Text(if (st.at == null) "Im Foto wählen" else "Neu wählen")
                }
                if (st.at != null) {
                    OutlinedButton(onClick = { onSel(Sel.StepAt(i)) }, modifier = Modifier.testTag("step_select_point")) { Text("Verschieben") }
                    OutlinedButton(onClick = { onPlan(EditOps.updateStep(plan, i) { it.copy(at = null) }); onSel(Sel.None) }, modifier = Modifier.testTag("step_auto_point")) { Text("Automatisch") }
                }
            }
        }
        if (st.action.isClimb) {
            val what = st.action.label
            Text("Richtung", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(st.up, { onPlan(EditOps.updateStep(plan, i) { it.copy(up = true) }) }, { Text("▲ Hinauf (unten → oben)") }, modifier = Modifier.testTag("step_dir_up"))
                FilterChip(!st.up, { onPlan(EditOps.updateStep(plan, i) { it.copy(up = false) }) }, { Text("▼ Hinunter (oben → unten)") }, modifier = Modifier.testTag("step_dir_down"))
            }
            Text("$what: Punkt A (unten) und Punkt B (oben) legst du im Foto fest, ein Gegenstand ist nicht nötig.", fontSize = 12.sp)
            if (st.action == ActionType.STAIRS) {
                val pts = EditOps.climbPath(plan, st)
                val right = pts == null || pts.second.x >= pts.first.x
                Text("Steigung der Treppe", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(right, { onPlan(EditOps.setStairsSlope(plan, i, true)) }, { Text("↗ nach rechts hoch") }, modifier = Modifier.testTag("stairs_right"))
                    FilterChip(!right, { onPlan(EditOps.setStairsSlope(plan, i, false)) }, { Text("↖ nach links hoch") }, modifier = Modifier.testTag("stairs_left"))
                }
            }
            Text("Punkte A (unten) und B (oben)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onSel(Sel.StepAt(i)) }, modifier = Modifier.testTag("step_path_bottom")) { Text("A verschieben") }
                OutlinedButton(onClick = { onSel(Sel.StepTo(i)) }, modifier = Modifier.testTag("step_path_top")) { Text("B verschieben") }
                OutlinedButton(onClick = { onPickPoint(i, false) }, modifier = Modifier.testTag("step_pick_bottom")) { Text("A im Foto wählen") }
                OutlinedButton(onClick = { onPickPoint(i, true) }, modifier = Modifier.testTag("step_pick_top")) { Text("B im Foto wählen") }
                OutlinedButton(onClick = {
                    val pts = EditOps.climbDefaultPoints(plan, st.action)
                    onPlan(EditOps.updateStep(plan, i) { it.copy(at = pts.first, to = pts.second) })
                    onSel(Sel.None)
                }, modifier = Modifier.testTag("step_auto_path")) { Text("Zurücksetzen") }
            }
            Text(
                if (st.action == ActionType.STAIRS) {
                    "Die Treppe wird seitlich schräg gegangen (hohe Knie), nicht geklettert. Bei „Hinunter“ steigt er erst hinauf, falls er noch unten steht."
                } else {
                    "Die Leiter wird senkrecht geklettert (Blick zur Leiter). Bei „Hinunter“ steigt er erst hinauf, falls er noch unten steht."
                },
                fontSize = 11.sp,
            )
        }
        if (st.action == ActionType.SWEEP || st.action.isClimb || (st.action != ActionType.SWEEP && st.retreat == Retreat.OBJECT)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = when {
                    st.action == ActionType.SWEEP -> "Dauer"
                    st.action.isClimb -> "Oben bleiben"
                    else -> "Versteckt"
                }
                Text("$label: ${st.seconds.toInt()} s", fontSize = 13.sp, modifier = Modifier.testTag("step_seconds"))
                PadButton("−", "step_sec_minus") { onPlan(EditOps.updateStep(plan, i) { it.copy(seconds = (it.seconds - 1f).coerceAtLeast(1f)) }) }
                PadButton("+", "step_sec_plus") { onPlan(EditOps.updateStep(plan, i) { it.copy(seconds = (it.seconds + 1f).coerceAtMost(60f)) }) }
            }
        }
        // Pfeile nur für den gerade gewählten Schritt (nie für einen anderen)
        val sl = sel
        if ((sl is Sel.StepAt && sl.index == i) || (sl is Sel.StepTo && sl.index == i)) PlacePad(plan, sel, onPlan)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onPlan(EditOps.moveStep(plan, i, -1)); if (i > 0) onStepIdx(i - 1); onSel(Sel.None) }, modifier = Modifier.testTag("step_up")) { Text("↑ Früher") }
            OutlinedButton(onClick = { onPlan(EditOps.moveStep(plan, i, 1)); if (i < plan.steps.size - 1) onStepIdx(i + 1); onSel(Sel.None) }, modifier = Modifier.testTag("step_down")) { Text("↓ Später") }
            OutlinedButton(onClick = { onPlan(EditOps.removeStep(plan, i)); onStepIdx(0); onSel(Sel.None) }, enabled = plan.steps.size > 1, modifier = Modifier.testTag("step_delete")) { Text("Löschen") }
        }
    }
}

// ---------------------------------------------------------------------------------------- Ansicht
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViewPanel(plan: Plan, sel: Sel, onSel: (Sel) -> Unit, onPlan: (Plan) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Blickwinkel des Fotos (bestimmt die Wegfindung)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            HelpButton(HelpTexts.VIEW_TITLE, HelpTexts.VIEW, "help_view")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ViewMode.entries.forEach { v ->
                FilterChip(plan.view == v, { onPlan(plan.copy(view = v)) }, { Text(v.label) }, modifier = Modifier.testTag("view_${v.key}"))
            }
        }
        Text(
            if (plan.view == ViewMode.SIDE) {
                "Seitlich: Foto in normaler Blickrichtung. Der Wichtel wird von der Seite gesehen und nach hinten kleiner. Ziehe die Horizont-Linie dorthin, wo der Boden endet. " +
                    "Ein markiertes Haus ist ein Kasten auf dem Boden: nur der rote Streifen unten ist gesperrt, dahinter kann sich der Wichtel verstecken."
            } else {
                "Von oben (Aufsicht): Das Foto zeigt die Szene von oben, der Wichtel ist überall gleich groß. Ein markiertes Haus ist seine Grundfläche: " +
                    "das ganze Viereck ist gesperrt und der Wichtel läuft darum herum. Bäume, Büsche und Tische dagegen verdecken ihn, wenn er darunter steht. Mehr unter „? Hilfe“."
            },
            fontSize = 12.sp,
        )
        if (plan.view == ViewMode.SIDE) {
            OutlinedButton(onClick = { onSel(Sel.Horizon) }, modifier = Modifier.testTag("select_horizon")) { Text("Horizont auswählen") }
            Text("Horizont", fontSize = 13.sp)
            Slider(
                value = (plan.horizon / plan.height).coerceIn(0.05f, 0.9f),
                onValueChange = { onPlan(plan.copy(horizon = it * plan.height)) },
                valueRange = 0.05f..0.9f,
                modifier = Modifier.testTag("horizon_slider"),
            )
            if (sel == Sel.Horizon) PlacePad(plan, sel, onPlan)
        }
        Text("Größe des Wichtels im Verhältnis zur Tür", fontSize = 13.sp)
        Slider(
            value = plan.wichtelRatio,
            onValueChange = { onPlan(plan.copy(wichtelRatio = it)) },
            valueRange = 0.4f..1.4f,
            modifier = Modifier.testTag("ratio_slider"),
        )
        Spacer(Modifier.height(4.dp))
    }
}
