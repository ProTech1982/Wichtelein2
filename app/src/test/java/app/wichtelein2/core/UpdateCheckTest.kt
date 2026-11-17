package app.wichtelein2.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val json = """{"tag_name":"build-12","name":"Wichtelein2 v0.1.0 (Build 12)","body":"## Neu\n- Tür","html_url":"https://x/y",
        "assets":[{"name":"Wichtelein2.apk","browser_download_url":"https://x/Wichtelein2.apk"}]}"""

    @Test
    fun newerBuildIsOffered() {
        val r = UpdateCheck.parse(JSONObject(json), 11)
        assertTrue(r.available)
        assertEquals("https://x/Wichtelein2.apk", r.downloadUrl)
    }

    @Test
    fun sameOrOlderBuildIsNot() {
        assertFalse(UpdateCheck.parse(JSONObject(json), 12).available)
        assertFalse(UpdateCheck.parse(JSONObject(json), 40).available)
    }

    @Test
    fun buildNumberAndNotes() {
        assertEquals(17L, UpdateCheck.buildNumber("build-17"))
        assertEquals(-1L, UpdateCheck.buildNumber("v"))
        assertEquals("Neu\n- Tür", UpdateCheck.cleanNotes("## Neu\n- Tür"))
        assertTrue(UpdateCheck.cleanNotes("").startsWith("Keine"))
    }

    @Test
    fun apiPointsToWichtelein2() {
        assertTrue(UpdateCheck.API.endsWith("/Wichtelein2/releases/latest"))
    }
}
