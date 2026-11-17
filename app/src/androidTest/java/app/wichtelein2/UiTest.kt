package app.wichtelein2

import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.Hinge
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.OpenDir
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Retreat
import app.wichtelein2.core.ViewMode
import app.wichtelein2.data.ProjectStore
import app.wichtelein2.data.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bedienung der App auf dem kleinen Emulator-Bildschirm (320 x 640): alles muss erreichbar sein. */
@RunWith(AndroidJUnit4::class)
class UiTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx = TestUtil.context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun prepare() {
        ctx.getSharedPreferences("wichtelein2", 0).edit().clear().commit()
        ProjectStore(ctx).importBitmap(TestUtil.photo(800, 600))
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun start() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun has(tag: String): Boolean = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun enabled(tag: String): Boolean = try {
        rule.onNodeWithTag(tag).assertIsEnabled()
        true
    } catch (e: AssertionError) {
        false
    }

    private fun openEditor() {
        start()
        rule.onNodeWithTag("btn_continue").performClick()
        rule.waitUntil(30_000) { has("btn_preview") }
        rule.waitUntil(60_000) { enabled("btn_preview") }
        shot("ui_editor_start")
    }

    /** Knoten im Bedienfeld: erst hinscrollen, dann antippen. */
    private fun tap(tag: String) {
        val n: SemanticsNodeInteraction = rule.onNodeWithTag(tag)
        n.performScrollTo()
        val b = n.fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        try {
            n.assertIsDisplayed()
        } catch (e: AssertionError) {
            throw AssertionError("$tag nicht sichtbar: Knoten $b, Fenster $root", e)
        }
        n.performClick()
        Log.i("UiTest", "getippt $tag bei $b (Fenster $root)")
    }

    /** Reiter unten im Editor (nicht scrollbar). */
    private fun tab(i: Int) {
        rule.onNodeWithTag("tab_$i").performClick()
    }

    /** Wartet, bis der automatisch gespeicherte Plan die Bedingung erfüllt (Speichern läuft 250 ms nach der Änderung). */
    private fun awaitPlan(what: String, cond: (Plan) -> Boolean): Plan {
        var last: Plan? = null
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end) {
            last = ProjectStore(ctx).loadPlan()
            if (last != null && cond(last)) return last
            Thread.sleep(150)
        }
        val ui = try {
            rule.onNodeWithTag("editor_root").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        } catch (e: Throwable) {
            "keine Oberfläche: ${e.message}"
        }
        throw AssertionError("Plan erfüllt nicht: $what; Oberfläche zeigt: $ui; gespeichert: $last")
    }

    private fun shot(name: String) {
        TestUtil.shot(name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun homeShowsButtonsAndVersion() {
        start()
        rule.onNodeWithTag("btn_pick_photo").assertIsDisplayed()
        rule.onNodeWithTag("btn_take_photo").assertIsDisplayed()
        rule.onNodeWithTag("btn_continue").assertIsDisplayed()
        rule.onNodeWithTag("btn_settings").assertIsDisplayed()
        rule.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        shot("ui_home")
    }

    @Test
    fun arrowButtonsMoveRotateAndResizeTheDoor() {
        openEditor()
        val before = awaitPlan("Startplan") { true }.door
        tap("pad_up")
        awaitPlan("hoch") { it.door.y < before.y - 1f }
        tap("pad_left")
        awaitPlan("links") { it.door.x < before.x - 1f }
        tap("pad_rot_right")
        awaitPlan("drehen") { it.door.rot > 1f }
        tap("pad_size_plus")
        awaitPlan("größer") { it.door.w > before.w * 1.01f && it.door.h > before.h * 1.01f }
        tap("pad_w_plus")
        awaitPlan("breiter") { it.door.w > before.w * 1.05f }
        tap("pad_h_minus")
        tap("pad_down")
        tap("pad_right")
        tap("pad_rot_left")
        shot("ui_editor_door")
        val after = awaitPlan("zurück") { Math.abs(it.door.rot) < 0.01f && Math.abs(it.door.y - before.y) < 1.5f && Math.abs(it.door.x - before.x) < 1.5f }.door
        assertTrue("Breite geändert", after.w > before.w * 1.05f)
    }

    @Test
    fun doorOptionsAreStored() {
        openEditor()
        tap("door_shape_round")
        tap("door_hinge_right")
        tap("door_open_in")
        tap("door_interior_black")
        val d = awaitPlan("Türoptionen") {
            it.door.shape == DoorShape.ROUND && it.door.hinge == Hinge.RIGHT && it.door.openDir == OpenDir.IN && it.door.interior == InteriorKind.BLACK
        }.door
        assertEquals(DoorShape.ROUND, d.shape)
        tap("door_interior_hoehle")
        awaitPlan("Höhle") { it.door.interior == InteriorKind.HOEHLE }
    }

    @Test
    fun objectsCanBeAddedMovedAndRemoved() {
        openEditor()
        tab(1)
        tap("add_tree")
        tap("add_bed")
        var p = awaitPlan("zwei Gegenstände") { it.objects.size == 2 }
        assertEquals(listOf(ObjType.TREE, ObjType.BED), p.objects.map { it.type })
        val bed = p.objects[1]
        tap("pad_up")
        tap("pad_rot_right")
        p = awaitPlan("Bett verschoben und gedreht") { it.objects.size == 2 && it.objects[1].y < bed.y && it.objects[1].rot > 1f }
        assertEquals(3f, p.objects[1].rot, 0.01f)
        shot("ui_editor_objects")
        tap("obj_delete")
        awaitPlan("Bett gelöscht") { it.objects.map { o -> o.type } == listOf(ObjType.TREE) }
    }

    @Test
    fun stepsCanBeBuiltWithHideObjectAndPoint() {
        openEditor()
        tab(1)
        tap("add_bush")
        tab(2)
        tap("add_step_hide")
        tap("step_retreat_object")
        tap("add_step_scare")
        tap("step_retreat_door")
        tap("step_pick_point")
        rule.onNodeWithTag("pick_banner").assertIsDisplayed()
        rule.onNodeWithTag("editor_canvas").performClick()
        val p = awaitPlan("Schritte") { it.steps.size == 3 && it.steps[2].at != null }
        assertEquals(listOf(ActionType.SWEEP, ActionType.HIDE, ActionType.SCARE), p.steps.map { it.action })
        assertEquals(p.objects[0].id, p.steps[1].objectId)
        assertEquals(Retreat.OBJECT, p.steps[1].retreat)
        assertEquals(Retreat.DOOR, p.steps[2].retreat)
        assertNotNull(p.steps[2].at)
        shot("ui_editor_steps")
    }

    @Test
    fun viewModeSwitchesAndHorizonMoves() {
        openEditor()
        tab(3)
        tap("view_top")
        awaitPlan("von oben") { it.view == ViewMode.TOP }
        tap("view_side")
        val p = awaitPlan("seitlich") { it.view == ViewMode.SIDE }
        tap("select_horizon")
        tap("pad_down")
        awaitPlan("Horizont tiefer") { it.horizon > p.horizon + 1f }
        shot("ui_editor_view")
    }

    @Test
    fun previewPlaysAndExportMakesVideo() {
        openEditor()
        rule.onNodeWithTag("btn_preview").performClick()
        rule.waitUntil(30_000) { has("preview_canvas") }
        Thread.sleep(1500)
        shot("ui_preview")
        rule.onNodeWithTag("preview_time").assertIsDisplayed()
        rule.onNodeWithTag("preview_close").performClick()
        rule.waitUntil(10_000) { has("btn_export") }
        rule.onNodeWithTag("btn_export").performClick()
        rule.waitUntil(240_000) { has("export_done") || has("export_error") }
        rule.onNodeWithTag("export_done").assertIsDisplayed()
    }

    @Test
    fun settingsThemeColorsAndUpdateDialog() {
        start()
        rule.onNodeWithTag("btn_settings").performClick()
        tap("theme_2")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().themeMode == 2 }
        Thread.sleep(600)
        shot("ui_settings_dark")
        tap("theme_1")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().themeMode == 1 }
        tap("color_hat_6")
        tap("color_pants_2")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().colors.hat != null && SettingsStore(ctx).load().colors.pants != null }
        tap("theme_1")
        Thread.sleep(1200)
        shot("ui_settings_light")
        tap("theme_0")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().themeMode == 0 }
        tap("colors_reset")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().colors.isOriginal }
        tap("btn_update")
        rule.waitUntil(15_000) { has("update_dialog") }
        rule.onNodeWithTag("update_dialog").assertIsDisplayed()
    }
}
