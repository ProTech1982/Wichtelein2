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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wichtelein2.core.ActionType
import app.wichtelein2.core.DoorShape
import app.wichtelein2.core.Hinge
import app.wichtelein2.core.InteriorKind
import app.wichtelein2.core.Layer
import app.wichtelein2.core.ObjType
import app.wichtelein2.core.OpenDir
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Retreat
import app.wichtelein2.core.ViewMode
import app.wichtelein2.data.ProjectStore
import app.wichtelein2.data.SettingsStore
import app.wichtelein2.ui.HomeFx
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bedienung der App auf dem kleinen Emulator-Bildschirm (320 x 640): alles muss erreichbar sein. */
@RunWith(AndroidJUnit4::class)
class UiTest {
    // Kamera-Erlaubnis vorab (AR-Bildschirm und Foto-Aufnahme fragen sonst mit einem System-Fenster)
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private val ctx = TestUtil.context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun prepare() {
        // Startseite ohne Dauer-Animation und Musik, sonst wartet die Test-Uhr ewig auf Ruhe
        HomeFx.animate = false
        HomeFx.music = false
        ctx.getSharedPreferences("wichtelein2", 0).edit().clear().commit()
        ProjectStore(ctx).importBitmap(TestUtil.photo(800, 600))
    }

    @After
    fun close() {
        scenario?.close()
        HomeFx.animate = true
        HomeFx.music = true
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
            // Im Test läuft die Compose-Uhr nur mit: ohne diesen Schritt käme das verzögerte Autospeichern (delay 250 ms) nie dran
            rule.mainClock.advanceTimeBy(300)
            Thread.sleep(100)
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
        rule.onNodeWithText("Version 0.3.3").assertIsDisplayed()
        rule.onNodeWithTag("btn_backgrounds").assertIsDisplayed()
        rule.onNodeWithTag("btn_home_music").assertIsDisplayed()
        rule.onNodeWithTag("btn_ar").assertIsDisplayed()
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
    fun draggingInThePhotoMovesAndResizesTheDoor() {
        openEditor()
        val before = awaitPlan("Startplan") { true }.door
        val node = rule.onNodeWithTag("editor_canvas")
        val wpx = node.fetchSemanticsNode().size.width.toFloat()
        val k = wpx / 800f
        // Tür an der Mitte anfassen und nach links ziehen
        val grab = androidx.compose.ui.geometry.Offset(before.x * k, (before.y - before.h / 2f) * k)
        node.performTouchInput {
            down(grab)
            moveTo(grab + androidx.compose.ui.geometry.Offset(-20f, 0f))
            moveTo(grab + androidx.compose.ui.geometry.Offset(-60f, 6f))
            moveTo(grab + androidx.compose.ui.geometry.Offset(-100f, 10f))
            up()
        }
        val moved = awaitPlan("verschoben") { it.door.x < before.x - 40f }.door
        // Der Tippabstand (ca. 8 Pixel) zählt nicht mit: erlaubt ist eine Abweichung von etwa einem Tippabstand
        assertEquals((before.x - 100f / k), moved.x, 30f)
        assertEquals((before.y + 10f / k), moved.y, 30f)
        // Griff oben rechts ziehen = größer
        val handle = androidx.compose.ui.geometry.Offset((moved.x + moved.w / 2f) * k, (moved.y - moved.h) * k)
        node.performTouchInput {
            down(handle)
            moveTo(handle + androidx.compose.ui.geometry.Offset(30f, -30f))
            moveTo(handle + androidx.compose.ui.geometry.Offset(60f, -60f))
            up()
        }
        val big = awaitPlan("größer gezogen") { it.door.h > moved.h + 20f }.door
        assertTrue("Breite wächst mit", big.w > moved.w + 10f)
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

    @Test
    fun soundSettingsAreStored() {
        start()
        rule.onNodeWithTag("btn_settings").performClick()
        assertEquals("alle Geräusche standardmäßig an", app.wichtelein2.core.SoundGroup.entries.toSet(), SettingsStore(ctx).load().soundGroups)
        // jede Gruppe lässt sich einzeln aus- und wieder einschalten
        for (g in app.wichtelein2.core.SoundGroup.entries) {
            tap("sound_${g.name}")
            rule.waitUntil(5_000) { g !in SettingsStore(ctx).load().soundGroups }
            assertEquals(app.wichtelein2.core.SoundGroup.entries.size - 1, SettingsStore(ctx).load().soundGroups.size)
            tap("sound_${g.name}")
            rule.waitUntil(5_000) { g in SettingsStore(ctx).load().soundGroups }
        }
        tap("music_3")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().music == 3 }
        tap("music_0")
        rule.waitUntil(5_000) { SettingsStore(ctx).load().music == 0 }
        rule.onNodeWithTag("fx_volume").performScrollTo()
        rule.onNodeWithTag("fx_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        rule.waitUntil(5_000) { SettingsStore(ctx).load().fxVolume < 0.5f }
        rule.onNodeWithTag("music_volume").performScrollTo()
        rule.onNodeWithTag("music_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.1f) }
        rule.waitUntil(5_000) { SettingsStore(ctx).load().musicVolume < 0.3f }
        shot("ui_settings_sound")
    }

    @Test
    fun previewHasSoundSwitch() {
        openEditor()
        rule.onNodeWithTag("btn_preview").performClick()
        rule.waitUntil(30_000) { has("preview_canvas") }
        rule.onNodeWithTag("preview_mute").assertIsDisplayed()
        rule.onNodeWithTag("preview_debug").assertIsDisplayed()
        rule.onNodeWithTag("preview_mute").performClick()
        rule.onNodeWithText("🔇 Ton aus").assertIsDisplayed()
        rule.onNodeWithTag("preview_close").performClick()
    }

    @Test
    fun arScreenOpensAndGoesBack() {
        start()
        rule.onNodeWithTag("btn_ar").performClick()
        rule.waitUntil(15_000) { has("ar_root") }
        // Der Emulator hat kein ARCore: es muss eine verständliche Meldung kommen (oder, wo AR geht, der Hinweis zum Boden)
        rule.waitUntil(60_000) { has("ar_message") || has("ar_hint") }
        shot("ui_ar")
        rule.onNodeWithTag("ar_back").performClick()
        rule.waitUntil(10_000) { has("btn_ar") }
    }

    // ------------------------------------------------------------------ Startseite: Hintergrund und Musik
    @Test
    fun startPageBackgroundAndMusicCanBeChosen() {
        start()
        rule.onNodeWithTag("btn_backgrounds").performClick()
        rule.waitUntil(15_000) { has("bg_picker") }
        for (id in listOf("kamin", "sessel", "baum", "dorf")) assertTrue("Vorschaubild $id", has("bg_thumb_$id"))
        rule.onNodeWithTag("bg_thumb_sessel").performScrollTo().performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().bgId == "sessel" }
        // Musik zum Bild wählen: Melodie, dann aus
        rule.onNodeWithTag("bg_music_t3").performScrollTo().performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().bgMusic["sessel"] == "t3" }
        rule.onNodeWithTag("bg_music_off").performScrollTo().performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().bgMusic["sessel"] == "off" }
        // das nächste Bild hat seine eigene Musikwahl
        rule.onNodeWithTag("bg_thumb_kamin").performScrollTo().performClick()
        rule.waitUntil(5_000) { SettingsStore(ctx).load().bgId == "kamin" }
        assertNull("Kamin behält seine Standard-Melodie", SettingsStore(ctx).load().bgMusic["kamin"])
        rule.onNodeWithTag("bg_volume").performScrollTo()
        rule.onNodeWithTag("bg_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.15f) }
        rule.waitUntil(5_000) { SettingsStore(ctx).load().bgVolume < 0.3f }
        rule.onNodeWithTag("bg_close").performScrollTo().performClick()
        rule.waitUntil(5_000) { !has("bg_picker") }
        rule.onNodeWithTag("btn_home_music").performClick()
        rule.waitUntil(5_000) { !SettingsStore(ctx).load().homeMusic }
        shot("ui_home_hintergrund")
    }

    // ------------------------------------------------------------------ Editor: gesperrt, Schritte, Leiter/Treppe, Hilfe
    @Test
    fun onlyTheSelectedObjectMovesWhenDraggingOverlappingObjects() {
        openEditor()
        tab(1)
        tap("add_tree")
        tap("add_bed")
        var p = awaitPlan("zwei Gegenstände") { it.objects.size == 2 }
        val tree = p.objects[0]
        val bed = p.objects[1]
        // Beide liegen übereinander. Baum wählen und ziehen: nur der Baum darf wandern, nicht das (oben liegende) Bett
        rule.onNodeWithTag("obj_chip_${tree.id}").performScrollTo().performClick()
        val node = rule.onNodeWithTag("editor_canvas")
        val k = node.fetchSemanticsNode().size.width.toFloat() / 800f
        val grab = androidx.compose.ui.geometry.Offset(tree.x * k, tree.y * k)
        node.performTouchInput {
            down(grab)
            moveTo(grab + androidx.compose.ui.geometry.Offset(30f, 0f))
            moveTo(grab + androidx.compose.ui.geometry.Offset(70f, 8f))
            moveTo(grab + androidx.compose.ui.geometry.Offset(110f, 12f))
            up()
        }
        p = awaitPlan("Baum verschoben") { it.objects[0].x > tree.x + 40f }
        assertEquals("Bett bleibt stehen", bed.x, p.objects[1].x, 0.01f)
        assertEquals("Bett bleibt stehen", bed.y, p.objects[1].y, 0.01f)
    }

    @Test
    fun draggingOutsideTheActiveTabMovesNothing() {
        openEditor()
        val before = awaitPlan("Startplan") { true }
        tab(1)   // Reiter Objekte: die Tür ist gesperrt
        val node = rule.onNodeWithTag("editor_canvas")
        val k = node.fetchSemanticsNode().size.width.toFloat() / 800f
        val grab = androidx.compose.ui.geometry.Offset(before.door.x * k, (before.door.y - before.door.h / 2f) * k)
        node.performTouchInput {
            down(grab)
            moveTo(grab + androidx.compose.ui.geometry.Offset(-30f, 0f))
            moveTo(grab + androidx.compose.ui.geometry.Offset(-90f, 5f))
            up()
        }
        Thread.sleep(700)
        rule.mainClock.advanceTimeBy(400)
        val after = awaitPlan("Plan unverändert") { true }
        assertEquals(before.door.x, after.door.x, 0.01f)
        assertEquals(before.door.y, after.door.y, 0.01f)
    }

    @Test
    fun clickingAStepChipEditsExactlyThatStep() {
        openEditor()
        tab(2)
        tap("add_step_scare")
        tap("add_step_hide")
        // Schritt 1 (Fegen) und Schritt 2 (Erschrecken) bekommen je einen Ort
        tap("step_chip_0")
        tap("step_pick_point")
        rule.onNodeWithTag("editor_canvas").performClick()
        tap("step_chip_1")
        tap("step_pick_point")
        rule.onNodeWithTag("editor_canvas").performClick()
        val p0 = awaitPlan("zwei Orte") { it.steps.size == 3 && it.steps[0].at != null && it.steps[1].at != null }
        // Nun den ersten Schritt unten anklicken und mit den Pfeilen schieben: nur der erste darf wandern
        tap("step_chip_2")
        tap("step_chip_0")
        tap("pad_up")
        tap("pad_up")
        val p1 = awaitPlan("erster Schritt höher") { it.steps[0].at!!.y < p0.steps[0].at!!.y - 1f }
        assertEquals(p0.steps[1].at!!.y, p1.steps[1].at!!.y, 0.01f)
        assertEquals(p0.steps[1].at!!.x, p1.steps[1].at!!.x, 0.01f)
        assertEquals(p0.steps[0].at!!.x, p1.steps[0].at!!.x, 0.01f)
    }

    @Test
    fun ladderAndStairsStepsHaveDirectionAndPath() {
        openEditor()
        tab(1)
        tap("add_ladder")
        tap("add_stairs")
        val p0 = awaitPlan("Leiter und Treppe") { it.objects.size == 2 }
        tab(2)
        tap("add_step_ladder")
        tap("add_step_stairs")
        var p = awaitPlan("zwei Schritte") { it.steps.size == 3 }
        assertEquals(ActionType.LADDER, p.steps[1].action)
        assertEquals(ActionType.STAIRS, p.steps[2].action)
        assertEquals(p0.objects[0].id, p.steps[1].objectId)
        assertEquals(p0.objects[1].id, p.steps[2].objectId)
        // Treppe: hinunter wählen und das obere Ende des Pfads mit den Pfeilen versetzen
        tap("step_dir_down")
        tap("step_path_top")
        tap("pad_right")
        tap("pad_right")
        p = awaitPlan("Treppe hinunter mit eigenem Pfad") { !it.steps[2].up && it.steps[2].to != null }
        assertTrue("Leiter bleibt hinauf", p.steps[1].up)
        assertNotNull(p.steps[2].to)
        assertEquals(null, p.steps[2].at)
        // Wieder automatisch
        tap("step_auto_path")
        awaitPlan("Pfad automatisch") { it.steps[2].to == null && it.steps[2].at == null }
        shot("ui_editor_treppe")
    }

    @Test
    fun helpDialogExplainsTheTopViewOption() {
        openEditor()
        tab(3)
        tap("help_view")
        rule.waitUntil(5_000) { has("help_view_close") }
        rule.onNodeWithText("Aufsicht", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("help_view_close").performClick()
        rule.waitUntil(5_000) { !has("help_view_close") }
        // Zu jedem Gegenstand erklärt die App, was er in der gewählten Ansicht bewirkt
        tab(1)
        tap("add_house")
        rule.onNodeWithTag("obj_info").performScrollTo().assertIsDisplayed()
        tab(3)
        tap("view_top")
        tab(1)
        tap("obj_chip_${awaitPlan("Haus") { it.objects.size == 1 }.objects[0].id}")
        rule.onNodeWithTag("obj_info").performScrollTo()
        rule.onNodeWithText("Von oben:", substring = true).assertIsDisplayed()
    }

    @Test
    fun wichtelPreviewIsOptionalInDoorTab() {
        openEditor()
        rule.onNodeWithTag("toggle_wichtel").assertIsDisplayed()
        rule.onNodeWithTag("toggle_wichtel").performClick()
        shot("ui_editor_wichtel_an")
        assertEquals(Layer.DOOR, Layer.ofTab(0))
    }
}
