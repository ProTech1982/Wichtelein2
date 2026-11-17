package app.wichtelein2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecolorTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun originalColorsChangeNothing() {
        val px = intArrayOf(argb(255, 200, 30, 20))
        val copy = px.copyOf()
        Recolor.apply(px, intArrayOf(argb(255, 255, 0, 0)), WichtelColors())
        assertEquals(copy[0], px[0])
    }

    @Test
    fun hatBecomesBlueKeepingAlphaAndShade() {
        val px = intArrayOf(argb(200, 180, 25, 20), argb(255, 90, 12, 10))
        val mask = intArrayOf(argb(255, 255, 0, 0), argb(255, 255, 0, 0))
        Recolor.apply(px, mask, WichtelColors(hat = argb(255, 20, 40, 220)))
        for (p in px) {
            val r = (p shr 16) and 255
            val b = p and 255
            assertTrue("blau erwartet, war $p", b > r + 40)
        }
        assertEquals(200, px[0] ushr 24)
        assertTrue((px[1] and 255) < (px[0] and 255))
    }

    @Test
    fun unmaskedPixelsAndOtherRegionsStay() {
        val px = intArrayOf(argb(255, 10, 200, 10), argb(255, 50, 60, 70), argb(0, 0, 0, 0))
        val orig = px.copyOf()
        val mask = intArrayOf(argb(255, 0, 255, 0), argb(255, 0, 0, 0), argb(255, 255, 0, 0))
        Recolor.apply(px, mask, WichtelColors(hat = argb(255, 255, 255, 0)))
        assertEquals(orig[0], px[0])
        assertEquals(orig[1], px[1])
        assertEquals(orig[2], px[2])
    }

    @Test
    fun shoesMaskIsYellowAndRecolorsOnlyShoes() {
        val px = intArrayOf(argb(255, 200, 120, 60))
        val mask = intArrayOf(argb(255, 255, 255, 0))
        Recolor.apply(px, mask, WichtelColors(hat = argb(255, 0, 0, 255), shoes = argb(255, 20, 200, 20)))
        val g = (px[0] shr 8) and 255
        val r = (px[0] shr 16) and 255
        val b = px[0] and 255
        assertTrue(g > r && g > b)
    }
}
