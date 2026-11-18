package app.wichtelein2.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft die Startbilder aus den Assets (läuft im Ordner app/) und die Musikwahl. */
class BackgroundsTest {
    private val assets = File("src/main/assets")
    private val designs = Backgrounds.parse(File(assets, "backgrounds/backgrounds.json").readText())

    @Test
    fun hasSixToTenDesignsWithImagesAndValidData() {
        assertTrue("6 bis 10 Startbilder", designs.size in 6..10)
        assertEquals(designs.size, designs.map { it.id }.toSet().size)
        assertTrue(designs.any { it.id == Backgrounds.DEFAULT_ID })
        for (d in designs) {
            assertTrue("Bild fehlt: ${d.file}", File(assets, d.file).length() > 5_000)
            assertTrue(d.label.isNotBlank())
            assertTrue("Musik ${d.id}", d.music in 1..MusicTrack.entries.size)
            assertTrue(d.snow in 0f..1f)
            for (g in d.glows) assertTrue(g.x in 0f..1f && g.y in 0f..1f && g.r > 0f && g.r < 1.2f)
            for (t in d.twinkles) assertTrue(t.x in 0f..1f && t.y in 0f..1f && t.r > 0f)
            assertFalse("eingebaute Bilder heißen nicht custom_", d.id.startsWith(Backgrounds.CUSTOM_PREFIX))
        }
    }

    @Test
    fun theWishedMotifsExist() {
        val ids = designs.map { it.id }
        assertTrue("Kamin mit Feuer", "kamin" in ids)
        assertTrue("Wichtel im Sessel", "sessel" in ids)
        assertTrue(designs.first { it.id == "kamin" }.glows.isNotEmpty())
    }

    @Test
    fun musicCodesRoundTripAndFallBackToTheDesignDefault() {
        for (t in MusicTrack.entries) assertEquals(t, Backgrounds.track(Backgrounds.trackCode(t)))
        assertNull(Backgrounds.track("off"))
        assertNull(Backgrounds.track("t9"))
        assertTrue(Backgrounds.isValidMusic("file") && Backgrounds.isValidMusic("off") && Backgrounds.isValidMusic("t2"))
        assertFalse(Backgrounds.isValidMusic("xyz"))
        val m = mapOf("kamin" to "t3", "custom_1" to "file", "dorf" to "off")
        assertEquals(m, Backgrounds.decodeChoices(Backgrounds.encodeChoices(m)))
        assertEquals(emptyMap<String, String>(), Backgrounds.decodeChoices("kaputt;=x;a=zz"))
        assertEquals("t3", Backgrounds.musicFor(m, "kamin", designs))
        assertEquals("off", Backgrounds.musicFor(m, "dorf", designs))
        val dflt = designs.first { it.id == "sessel" }.music
        assertEquals(Backgrounds.codeForNumber(dflt), Backgrounds.musicFor(m, "sessel", designs))
        assertEquals("t1", Backgrounds.musicFor(m, "custom_9", designs))
        assertEquals("Aus", Backgrounds.musicLabel("off"))
        assertTrue(Backgrounds.isCustom("custom_17"))
    }
}
