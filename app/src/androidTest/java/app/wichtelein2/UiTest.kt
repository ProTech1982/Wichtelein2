package app.wichtelein2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.Hinge
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.OpenDir
import app.wichtelein2.core.Plan
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

    private fun openEditor() {
        start()
        rule.onNodeWithTag("btn_continue").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTagSafe("btn_preview") }
        rule.waitUntil(60_000) { enabled("btn_preview") }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagSafe(tag: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun enabled(tag: String): Boolean = try {
        rule.onNodeWithTag(tag).assertIsEnabled()
        true
    } catch (e: AssertionError) {
        false
    }

    private fun savedPlan(): Plan {
        // Autospeichern läuft 250 ms nach der Änderung
        Thread.sleep(700)
        return ProjectStore(ctx).loadPlan()!!
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
        val before = savedPlan().door
        rule.onNodeWithTag("pad_up").performScrollTo().performClick()
        rule.onNodeWithTag("pad_left").performScrollTo().performClick()
        rule.onNodeWithTag("pad_rot_right").performScrollTo().performClick()
        rule.onNodeWithTag("pad_size_plus").performScrollTo().performClick()
        rule.onNodeWithTag("pad_w_plus").performScrollTo().performClick()
        rule.onNodeWithTag("pad_h_minus").performScrollTo().performClick()
        rule.onNodeWithTag("pad_down").performScrollTo().performClick()
        rule.onNodeWithTag("pad_right").performScrollTo().performClick()
        rule.onNodeWithTag("pad_rot_left").performScrollTo().performClick()
        shot("ui_editor_door")
        val up = savedPlan().door
        // hoch+runter und links+rechts heben sich auf, Drehung auch; Größe bleibt geändert
        assertEquals(before.x, up.x, 1.5f)
        assertEquals(before.y, up.y, 1.5f)
        assertEquals(before.rot, up.rot, 0.01f)
        assertTrue("Größe + Breite/Höhe geändert", up.w > before.w * 1.02f && up.h != before.h)
    }

    @Test
    fun doorOptionsAreStored() {
        openEditor()
        rule.onNodeWithTag("door_shape_round").performScrollTo().performClick()
        rule.onNodeWithTag("door_hinge_right").performScrollTo().performClick()
        rule.onNodeWithTag("door_open_in").performScrollTo().performClick()
        rule.onNodeWithTag("door_interior_black").performScrollTo().performClick()
        val d = savedPlan().door
        assertEquals(DoorShape.ROUND, d.shape)
        assertEquals(Hinge.RIGHT, d.hinge)
        assertEquals(OpenDir.IN, d.openDir)
        assertEquals(InteriorKind.BLACK, d.interior)
        rule.onNodeWithTag("door_interior_hoehle").performScrollTo().performClick()
        assertEquals(InteriorKind.HOEHLE, savedPlan().door.interior)
    }

    @Test
    fun objectsCanBeAddedMovedAndRemoved() {
        openEditor()
        rule.onNodeWithTag("tab_1").performClick()
        rule.onNodeWithTag("add_tree").performScrollTo().performClick()
        rule.onNodeWithTag("add_bed").performScrollTo().performClick()
        var p = savedPlan()
        assertEquals(listOf(ObjType.TREE, ObjType.BED), p.objects.map { it.type })
        val bed = p.objects[1]
        rule.onNodeWithTag("pad_up").performScrollTo().performClick()
        rule.onNodeWithTag("pad_rot_right").performScrollTo().performClick()
        p = savedPlan()
        assertTrue(p.objects[1].y < bed.y)
        assertEquals(3f, p.objects[1].rot, 0.01f)
        shot("ui_editor_objects")
        rule.onNodeWithTag("obj_delete").performScrollTo().performClick()
        assertEquals(listOf(ObjType.TREE), savedPlan().objects.map { it.type })
    }

    @Test
    fun stepsCanBeBuiltWithHideObjectAndPoint() {
        openEditor()
        rule.onNodeWithTag("tab_1").performClick()
        rule.onNodeWithTag("add_bush").performScrollTo().performClick()
        rule.onNodeWithTag("tab_2").performClick()
        rule.onNodeWithTag("add_step_hide").performScrollTo().performClick()
        rule.onNodeWithTag("step_retreat_object").performScrollTo().performClick()
        rule.onNodeWithTag("add_step_scare").performScrollTo().performClick()
        rule.onNodeWithTag("step_retreat_door").performScrollTo().performClick()
        rule.onNodeWithTag("step_pick_point").performScrollTo().performClick()
        rule.onNodeWithTag("pick_banner").assertIsDisplayed()
        rule.onNodeWithTag("editor_canvas").performClick()
        val p = savedPlan()
        assertEquals(listOf(ActionType.SWEEP, ActionType.HIDE, ActionType.SCARE), p.steps.map { it.action })
        assertTrue(p.steps[1].objectId == p.objects[0].id)
        assertNotNull(p.steps[2].at)
        shot("ui_editor_steps")
    }

    @Test
    fun viewModeSwitchesAndHorizonMoves() {
        openEditor()
        rule.onNodeWithTag("tab_3").performClick()
        rule.onNodeWithTag("view_top").performScrollTo().performClick()
        assertEquals(ViewMode.TOP, savedPlan().view)
        rule.onNodeWithTag("view_side").performScrollTo().performClick()
        assertEquals(ViewMode.SIDE, savedPlan().view)
        val h0 = savedPlan().horizon
        rule.onNodeWithTag("select_horizon").performScrollTo().performClick()
        rule.onNodeWithTag("pad_down").performScrollTo().performClick()
        assertTrue(savedPlan().horizon > h0)
        shot("ui_editor_view")
    }

    @Test
    fun previewPlaysAndExportMakesVideo() {
        openEditor()
        rule.onNodeWithTag("btn_preview").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTagSafe("preview_canvas") }
        Thread.sleep(2500)
        shot("ui_preview")
        rule.onNodeWithTag("preview_time").assertIsDisplayed()
        rule.onNodeWithTag("preview_close").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTagSafe("btn_export") }
        rule.onNodeWithTag("btn_export").performClick()
        rule.waitUntil(240_000) { rule.onAllNodesWithTagSafe("export_done") || rule.onAllNodesWithTagSafe("export_error") }
        rule.onNodeWithTag("export_done").assertIsDisplayed()
    }

    @Test
    fun settingsThemeColorsAndUpdateDialog() {
        start()
        rule.onNodeWithTag("btn_settings").performClick()
        rule.onNodeWithTag("theme_2").performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().themeMode == 2 }
        rule.onNodeWithTag("theme_1").performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().themeMode == 1 }
        rule.onNodeWithTag("color_hat_6").performScrollTo().performClick()
        rule.onNodeWithTag("color_pants_2").performScrollTo().performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().colors.hat != null && SettingsStore(ctx).load().colors.pants != null }
        Thread.sleep(1500)
        shot("ui_settings_light")
        rule.onNodeWithTag("theme_2").performClick()
        Thread.sleep(800)
        shot("ui_settings_dark")
        rule.onNodeWithTag("colors_reset").performScrollTo().performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().colors.isOriginal }
        rule.onNodeWithTag("btn_update").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTagSafe("update_dialog") }
        rule.onNodeWithTag("update_dialog").assertIsDisplayed()
    }
}
