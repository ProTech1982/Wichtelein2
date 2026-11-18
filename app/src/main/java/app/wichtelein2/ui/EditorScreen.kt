package app.wichtelein2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.core.EditOps
import app.wichtelein2.core.Layer
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Sel
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.render.SpriteLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bearbeiten: Foto mit Tür, Gegenständen und Ablauf. Unten die Bereiche Tür, Objekte, Ablauf, Ansicht;
 * Platzieren mit Pfeilen, Drehen und Größe (auch direkt im Foto ziehen).
 */
@Composable
fun EditorScreen(
    plan: Plan,
    photo: Bitmap,
    sprites: SpriteLibrary,
    customInterior: Bitmap?,
    colorsKey: Any,
    onPlan: (Plan) -> Unit,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onPickInterior: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Sel>(Sel.Door) }
    var stepIdx by remember { mutableIntStateOf(0) }
    var pickIdx by remember { mutableIntStateOf(-1) }
    var pickTop by remember { mutableStateOf(false) }
    // Der Wichtel als Größenvorschau steht nur auf Wunsch (und im Reiter Ansicht) im Foto, damit er beim Platzieren die Tür nicht verdeckt
    var wichtelPreview by remember { mutableStateOf(false) }

    // Auswahl setzen: Im Reiter Tür bleibt die Tür immer gewählt; ein Schritt-Punkt macht seinen Schritt zum aktuellen
    fun select(s: Sel) {
        if (s == Sel.None && tab == 0) return
        sel = s
        when (s) {
            is Sel.StepAt -> stepIdx = s.index
            is Sel.StepTo -> stepIdx = s.index
            else -> Unit
        }
    }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(plan.view, colorsKey) {
        ready = false
        withContext(Dispatchers.Default) { sprites.preload(plan.view) }
        ready = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val panelMax = maxHeight * 0.42f
    Column(
        Modifier.fillMaxSize().testTag("editor_root").semantics {
            // Zustand für Tests und Bedienhilfen: Ansicht, Tür, Anzahl Gegenstände und Schritte
            stateDescription = "Ansicht ${plan.view.key}, Tür ${plan.door.x.toInt()}/${plan.door.y.toInt()} " +
                "${plan.door.w.toInt()}x${plan.door.h.toInt()} ${plan.door.rot.toInt()}°, " +
                "${plan.objects.size} Gegenstände, ${plan.steps.size} Schritte"
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) { Text("‹") }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = wichtelPreview || tab == 3,
                onClick = { wichtelPreview = !wichtelPreview },
                enabled = tab != 3,
                label = { Text("Wichtel", fontSize = 12.sp, maxLines = 1) },
                modifier = Modifier.testTag("toggle_wichtel"),
            )
            Button(onClick = onPreview, enabled = ready, modifier = Modifier.testTag("btn_preview")) { Text("▶ Vorschau") }
            Button(onClick = onExport, enabled = ready, modifier = Modifier.testTag("btn_export")) { Text("Video") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            EditorCanvas(
                plan = plan,
                photo = photo,
                sprites = sprites,
                customInterior = customInterior,
                spritesReady = ready,
                sel = sel,
                layer = Layer.ofTab(tab),
                activeStep = stepIdx,
                showWichtel = wichtelPreview || tab == 3,
                pickPoint = pickIdx >= 0,
                onSel = { select(it) },
                onPlan = onPlan,
                onPicked = { p ->
                    val i = pickIdx
                    val top = pickTop
                    pickIdx = -1
                    if (i in plan.steps.indices) {
                        onPlan(EditOps.placeStepPoint(plan, i, p, top))
                        sel = if (top) Sel.StepTo(i) else Sel.StepAt(i)
                    }
                },
            )
            if (pickIdx >= 0) {
                Text(
                    if (pickTop) "Tippe im Foto auf das obere Ende" else "Tippe im Foto auf den Ort",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("pick_banner"),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Tür", "Objekte", "Ablauf", "Ansicht").forEachIndexed { i, label ->
                FilterChip(
                    selected = tab == i,
                    onClick = {
                        tab = i
                        pickIdx = -1
                        sel = if (i == 0) Sel.Door else Sel.None
                    },
                    label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.testTag("tab_$i"),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = panelMax).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            when (tab) {
                0 -> DoorPanel(plan, sel, plan.door.interior == InteriorKind.CUSTOM || customInterior != null, { sel = it }, onPlan, onPickInterior)
                1 -> ObjectsPanel(plan, sel, { sel = it }, onPlan)
                2 -> StepsPanel(plan, stepIdx, { stepIdx = it }, sel, { sel = it }, onPlan) { i, top -> pickIdx = i; pickTop = top; sel = Sel.None }
                else -> ViewPanel(plan, sel, { sel = it }, onPlan)
            }
        }
    }
    }
}
