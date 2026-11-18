package app.wichtelein2.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {
    @Test
    fun reportListsPlanWarningsTimelineAndJson() {
        val p = Plan.default(1600, 1000).copy(
            steps = listOf(Step(ActionType.SWEEP, seconds = 2f), Step(ActionType.STAIRS)),
            objects = listOf(SceneObject(1, ObjType.HOUSE, 700f, 560f, 300f, 360f)),
        )
        val sim = Simulator(p).run()
        val r = DebugReport.build("0.3.1", "Testgerät", p, sim)
        assertTrue(r.contains("Version: 0.3.1"))
        assertTrue(r.contains("Gegenstand #1 Haus"))
        assertTrue(r.contains("Schritt 2: Treppe"))
        assertTrue(r.contains("== Warnungen =="))
        assertTrue(r.contains("Keine Treppe markiert"))
        assertTrue(r.contains("== Zeitleiste"))
        assertTrue(r.contains("SWEEP"))
        assertTrue(r.contains("in der Tür"))
        assertTrue(r.contains("== Plan als JSON =="))
        assertTrue(PlanJson.fromJson(r.substringAfter("== Plan als JSON ==").trim()).steps.size == 2)
    }

    @Test
    fun longTimelinesAreCut() {
        val p = Plan.default(1600, 1000)
        val r = DebugReport.build("x", "", p, Simulator(p).run(), maxLines = 3)
        assertTrue(r.contains("gekürzt"))
    }
}
