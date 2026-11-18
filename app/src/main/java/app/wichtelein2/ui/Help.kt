package app.wichtelein2.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.ViewMode

/** Kleine Hilfe-Knöpfe und Erklärungstexte für den Editor (was bewirkt ein markierter Gegenstand, was ist „Von oben“ …). */
object HelpTexts {
    const val DOOR_TITLE = "Tür"
    const val DOOR =
        "Hier stellst du die Wichteltür so ein, dass sie zur echten Tür im Foto passt.\n\n" +
            "• Ziehe die Tür im Foto, oder nutze die Pfeile. Dabei ist nur die Tür aktiv; alles andere ist gesperrt und blass dargestellt.\n" +
            "• Der weiße Kreis oben rechts an der Tür ändert die Größe, ⟲ ⟳ drehen sie.\n" +
            "• Die gelben Punkte zeigen die Seite der Türangeln. „Aufschlag“ legt fest, ob sie nach außen oder nach innen aufgeht.\n" +
            "• „Hinter der Tür“ ist das, was man bei offener Tür sieht (schwarz, Stube, Höhle oder ein eigenes Bild).\n" +
            "• Der Wichtel steht hier nicht vor der Tür, damit er sie nicht verdeckt. Mit „Wichtel“ (oben) blendest du ihn zur Größenkontrolle ein."

    const val OBJECTS_TITLE = "Objekte"
    const val OBJECTS =
        "Objekte sind Dinge aus deinem Foto, die du mit einem Kasten oder Oval markierst (Bett, Baum, Haus, Leiter …). " +
            "Markieren malt nichts ins Foto, sondern sagt dem Wichtel, was da ist:\n\n" +
            "• Rot gestrichelt = Standfläche: Dort läuft der Wichtel nicht hinein, er geht außen herum (Wegfindung).\n" +
            "• Verstecken: Er kann sich hinter markierten Dingen verstecken. Geht er hinter ihnen entlang, deckt ein Ausschnitt aus deinem Foto ihn zu.\n" +
            "• Gras bremst ihn nur, Leiter und Treppe kann er hinauf- und hinuntersteigen (Tab „Ablauf“).\n\n" +
            "Tippe ein Objekt an, um es zu bearbeiten. Nur das gewählte Objekt lässt sich ziehen, alle anderen sind gesperrt. " +
            "Unter „Markiert“ siehst du eine Erklärung, was das gewählte Objekt in der aktuellen Ansicht (seitlich oder von oben) bewirkt."

    const val STEPS_TITLE = "Ablauf"
    const val STEPS =
        "Der Wichtel kommt aus der Tür, macht die Schritte der Reihe nach und geht danach wieder hinein.\n\n" +
            "• Tippe unten auf die Nummer eines Schritts, um genau diesen zu bearbeiten. Pfeile und „Verschieben“ gelten immer für den gewählten Schritt.\n" +
            "• Fegen / Erschrecken: Mit „Im Foto wählen“ legst du den Ort fest (Nummernkreis im Foto).\n" +
            "• Verstecken / Flucht: in die Tür oder hinter einen markierten Gegenstand.\n" +
            "• Leiter / Treppe: Pfad von unten nach oben. Wähle „Hinauf“ oder „Hinunter“ und stelle unten und oben im Foto ein. " +
            "Bei „Hinunter“ steigt der Wichtel erst hinauf, falls er noch unten steht. Folgt danach ein anderer Schritt, steigt er automatisch wieder hinab.\n" +
            "• Mit ↑ Früher / ↓ Später änderst du die Reihenfolge."

    const val VIEW_TITLE = "Ansicht"
    const val VIEW =
        "Hier sagst du der App, aus welchem Blickwinkel dein Foto aufgenommen ist. Davon hängt ab, wie der Wichtel gezeichnet wird und wie er läuft.\n\n" +
            "SEITLICH (normales Foto, wie du es mit dem Handy gerade aus aufnimmst):\n" +
            "• Der Wichtel wird nach hinten kleiner. Die Horizontlinie zeigt, wo der Boden endet.\n" +
            "• Ein markiertes Haus ist ein Kasten, der auf dem Boden steht: Nur der untere Streifen (rot gestrichelt) ist die Standfläche. " +
            "Der Wichtel läuft darum herum und kann sich dahinter verstecken, dann deckt dich der Ausschnitt aus dem Foto zu.\n\n" +
            "VON OBEN (Aufsicht, Foto von oben auf den Boden, Tisch oder Garten):\n" +
            "• Der Wichtel wird von oben gezeigt und ist überall gleich groß, der Horizont entfällt.\n" +
            "• Ein markiertes Haus ist hier seine Grundfläche: Das ganze Viereck ist gesperrt, der Wichtel läuft komplett herum. " +
            "Das Haus deckt ihn nie zu (von oben sieht man ja das Dach).\n" +
            "• Bäume, Büsche und Tische sind dagegen wie Dächer: Steht der Wichtel im markierten Bereich, ist er „darunter“ und wird vom Foto-Ausschnitt verdeckt.\n\n" +
            "Die Größe des Wichtels stellst du im Verhältnis zur Tür ein."

    /** Was bewirkt ein Gegenstand dieses Typs in der gewählten Ansicht? */
    fun objectInfo(t: ObjType, view: ViewMode): String {
        val top = view == ViewMode.TOP
        val fx = if (top) t.topFx else t.sideFx
        val fy = if (top) t.topFy else t.sideFy
        val hide = if (top) t.hideTop else t.hideSide
        val sb = StringBuilder()
        sb.append(if (top) "Von oben: " else "Seitlich: ")
        when {
            t == ObjType.LADDER || t == ObjType.STAIRS ->
                sb.append(
                    "${t.label} ist ein Hindernis; im Tab „Ablauf“ kannst du mit „+ ${t.label}“ den Wichtel hinauf- und hinuntersteigen lassen " +
                        "(Pfad von unten nach oben, einstellbar). Während er darauf steht, deckt die ${t.label} ihn nicht zu. ",
                )
            fx <= 0f || fy <= 0f -> sb.append("keine Standfläche, der Wichtel darf darüberlaufen. ")
            t == ObjType.GRASS -> sb.append("der Wichtel darf hinein, geht aber lieber außen herum (Gras bremst). ")
            !top -> sb.append("der rot gestrichelte Streifen unten ist die Standfläche: Dort läuft der Wichtel nicht hinein, er geht herum. Der Rest des Kastens ist nur die Höhe des Gegenstands. ")
            fx >= 1f && fy >= 1f -> sb.append("die ganze markierte Fläche ist gesperrt wie eine Grundfläche, der Wichtel läuft komplett herum. ")
            else -> sb.append("nur die kleine rote Fläche in der Mitte ist gesperrt (z. B. Stamm oder Tischbein), der Rest ist begehbar. ")
        }
        if (t != ObjType.LADDER && t != ObjType.STAIRS) {
            sb.append(
                when {
                    hide && top -> "Verstecken: ja. Steht der Wichtel im markierten Bereich, deckt der Foto-Ausschnitt ihn zu (er ist darunter). "
                    hide -> "Verstecken: ja. Geht der Wichtel dahinter (weiter oben im Foto), deckt der Foto-Ausschnitt ihn zu. "
                    else -> "Verstecken: nein, der Gegenstand deckt den Wichtel nicht zu. "
                },
            )
            sb.append("Die Form (Rechteck oder Oval) bestimmt den Ausschnitt, der den Wichtel verdeckt; ein Oval passt zu runden Dingen.")
        }
        return sb.toString().trim()
    }
}

/** „? Hilfe“-Knopf, der einen Erklärtext in einem Fenster zeigt. */
@Composable
fun HelpButton(title: String, text: String, tag: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.testTag(tag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) { Text("? Hilfe", fontSize = 12.sp) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, fontSize = 14.sp) } },
            confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag("${tag}_close")) { Text("OK") } },
        )
    }
}
