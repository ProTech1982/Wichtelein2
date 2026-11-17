package app.wichtelein2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanJsonTest {
    @Test
    fun roundTrip() {
        val p = Plan.default(1280, 960).copy(
            view = ViewMode.TOP,
            horizon = 111f,
            door = Door(10f, 20f, 30f, 40f, 15f, DoorShape.ROUND, Hinge.RIGHT, OpenDir.IN, InteriorKind.HOEHLE),
            objects = listOf(
                SceneObject(5, ObjType.BED, 100f, 200f, 300f, 150f, 12f, ShapeKind.OVAL),
                SceneObject(6, ObjType.TREE, 500f, 300f, 100f, 400f),
            ),
            steps = listOf(
                Step(ActionType.HIDE, Pt(1f, 2f), Retreat.OBJECT, 6, 3.5f),
                Step(ActionType.SCARE),
            ),
            wichtelRatio = 0.7f,
            nextId = 7,
        )
        val q = PlanJson.fromJson(PlanJson.toJson(p))
        assertEquals(p, q)
    }

    @Test
    fun brokenFieldsFallBackToDefaults() {
        val q = PlanJson.fromJson("""{"w":800,"h":600,"view":"???","door":{"shape":"x"},"steps":[]}""")
        assertEquals(ViewMode.SIDE, q.view)
        assertTrue(q.steps.isNotEmpty())
        assertEquals(800, q.width)
    }
}
