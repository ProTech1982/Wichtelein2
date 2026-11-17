package app.wichtelein2.core

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft, dass die Blender-Bögen in den Assets zu den Typen im Code passen (läuft im Ordner app/). */
class AssetsTest {
    private val assets = File("src/main/assets")

    @Test
    fun spriteMetaMatchesAnimEnum() {
        val meta = JSONObject(File(assets, "sprites/sprites.json").readText())
        val anims = meta.getJSONObject("anims")
        assertEquals(Anim.entries.size, anims.length())
        for (a in Anim.entries) assertEquals(a.key, a.frames, anims.getInt(a.key))
        assertEquals(8, meta.getInt("dirs"))
        assertTrue(meta.getInt("cell") > 0)
    }

    @Test
    fun allSheetsExistForBothViews() {
        for (v in ViewMode.entries) for (a in Anim.entries) {
            assertTrue("${v.key}/${a.key}", File(assets, "sprites/${v.key}/${a.key}.webp").length() > 1000)
            assertTrue("${v.key}/${a.key}_m", File(assets, "sprites/${v.key}/${a.key}_m.webp").length() > 100)
        }
    }

    @Test
    fun doorSpritesExistForAllShapesAndAngles() {
        val meta = JSONObject(File(assets, "door/door.json").readText())
        val angles = meta.getJSONArray("angles")
        assertTrue(angles.length() >= 6)
        for (s in DoorShape.entries) {
            assertTrue(meta.getJSONObject("shapes").has(s.key))
            assertTrue(File(assets, "door/door_${s.key}_hole.webp").exists())
            for (i in 0 until angles.length()) {
                assertTrue(File(assets, "door/door_${s.key}_${angles.getString(i)}.webp").exists())
            }
        }
    }

    @Test
    fun interiorsExist() {
        assertTrue(File(assets, "interior/interior_stube.webp").exists())
        assertTrue(File(assets, "interior/interior_hoehle.webp").exists())
    }
}
