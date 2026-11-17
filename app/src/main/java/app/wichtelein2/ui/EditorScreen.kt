package app.wichtelein2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.core.EditOps
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
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(plan.view, colorsKey) {
        ready = false
        withContext(Dispatchers.Default) { sprites.preload(plan.view) }
        ready = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) { Text("‹") }
            Text("Wichtelein2", fontSize = 18.sp, modifier = Modifier.weight(1f))
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
                pickPoint = pickIdx >= 0,
                onSel = { sel = it },
                onPlan = onPlan,
                onPicked = { p ->
                    val i = pickIdx
                    pickIdx = -1
                    if (i in plan.steps.indices) {
                        onPlan(EditOps.updateStep(plan, i) { it.copy(at = p) })
                        sel = Sel.StepAt(i)
                    }
                },
            )
            if (pickIdx >= 0) {
                Text(
                    "Tippe im Foto auf den Ort",
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("Tür", "Objekte", "Ablauf", "Ansicht").forEachIndexed { i, label ->
                FilterChip(
                    selected = tab == i,
                    onClick = {
                        tab = i
                        pickIdx = -1
                        sel = if (i == 0) Sel.Door else Sel.None
                    },
                    label = { Text(label) },
                    modifier = Modifier.testTag("tab_$i"),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            when (tab) {
                0 -> DoorPanel(plan, sel, plan.door.interior == InteriorKind.CUSTOM || customInterior != null, { sel = it }, onPlan, onPickInterior)
                1 -> ObjectsPanel(plan, sel, { sel = it }, onPlan)
                2 -> StepsPanel(plan, stepIdx, { stepIdx = it }, sel, { sel = it }, onPlan) { i -> pickIdx = i; sel = Sel.None }
                else -> ViewPanel(plan, sel, { sel = it }, onPlan)
            }
        }
    }
}
