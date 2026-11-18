package app.wichtelein2.core

import java.util.Locale

/**
 * Textbericht zum Fehlersuchen: Plan (lesbar und als JSON), Warnungen der Simulation und eine Zeitleiste, wann welcher
 * Schritt, welche Bewegung, Tür- und Sichtbarkeitszustand galt. Ohne Android, damit er sich testen lässt.
 */
object DebugReport {
    private fun f(v: Float) = String.format(Locale.US, "%.1f", v)
    private fun s2(v: Float) = String.format(Locale.US, "%.2f", v)

    private fun doorState(open: Float) = when {
        open < 0.02f -> "zu"
        open > 0.98f -> "offen"
        else -> "bewegt"
    }

    private fun place(h: Float) = when {
        h < 0.02f -> "draußen"
        h > 0.98f -> "im Haus"
        else -> "in der Tür"
    }

    fun build(version: String, device: String, plan: Plan, sim: Simulation, maxLines: Int = 500): String {
        val sb = StringBuilder()
        sb.appendLine("Wichtelein2 Debug-Bericht")
        sb.appendLine("Version: $version")
        if (device.isNotBlank()) sb.appendLine("Gerät: $device")
        sb.appendLine("Dauer: ${s2(sim.duration)} s bei ${sim.fps} Bildern/s (${sim.frames.size} Bilder)")
        sb.appendLine()
        sb.appendLine("== Plan ==")
        sb.appendLine("Foto ${plan.width} x ${plan.height}, Ansicht ${plan.view}, Horizont ${f(plan.horizon)}")
        val d = plan.door
        sb.appendLine("Tür: x=${f(d.x)} y=${f(d.y)} w=${f(d.w)} h=${f(d.h)} drehung=${f(d.rot)} innen=${d.interior}")
        if (plan.objects.isEmpty()) sb.appendLine("Gegenstände: keine")
        plan.objects.forEach {
            sb.appendLine("Gegenstand #${it.id} ${it.type.label}: x=${f(it.x)} y=${f(it.y)} w=${f(it.w)} h=${f(it.h)} drehung=${f(it.rot)}")
        }
        plan.steps.forEachIndexed { i, st ->
            val at = st.at?.let { "bei (${f(it.x)}, ${f(it.y)})" } ?: "Ort automatisch"
            val to = st.to?.let { ", Pfadende (${f(it.x)}, ${f(it.y)})" } ?: ""
            val extra = when {
                st.action.isClimb -> ", ${if (st.up) "hinauf" else "hinunter"}"
                else -> ", Rückzug ${st.retreat.label}"
            }
            sb.appendLine("Schritt ${i + 1}: ${st.action.label} $at$to$extra, Gegenstand ${st.objectId}, ${f(st.seconds)} s")
        }
        sb.appendLine()
        sb.appendLine("== Warnungen ==")
        if (sim.warnings.isEmpty()) sb.appendLine("keine") else sim.warnings.distinct().forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("== Zeitleiste (Zustandswechsel) ==")
        sb.appendLine("Zeit | Schritt | Bewegung | Wichtel | Tür | Leiter/Treppe | Position")
        var lines = 0
        var key = ""
        for (fr in sim.frames) {
            val k = "${fr.step}|${fr.anim}|${place(fr.houseT)}|${doorState(fr.doorOpen)}|${fr.ignore >= 0}"
            if (k == key) continue
            key = k
            if (lines++ >= maxLines) {
                sb.appendLine("... (gekürzt)")
                break
            }
            val stepTxt = if (fr.step >= 0) (fr.step + 1).toString() else "-"
            sb.appendLine(
                "${s2(fr.t)} | $stepTxt | ${fr.anim} | ${place(fr.houseT)} | ${doorState(fr.doorOpen)} | " +
                    (if (fr.ignore >= 0) "ja (#${fr.ignore})" else "nein") + " | ${f(fr.x)}, ${f(fr.y)}",
            )
        }
        sb.appendLine()
        sb.appendLine("== Plan als JSON ==")
        sb.appendLine(PlanJson.toJson(plan))
        return sb.toString()
    }
}
