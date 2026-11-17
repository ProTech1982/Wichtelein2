package app.wichtelein2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArMathTest {
    /** Einfache Kamera ohne Neigen: schaut von cam in -z (OpenGL-Matrizen, spaltenweise). */
    private fun snapshot(cam: FloatArray = floatArrayOf(0f, 1f, 2f), anchor: FloatArray? = floatArrayOf(0f, 0f, 0f)): ArSnapshot {
        val near = 0.05f
        val far = 60f
        val f = 1.5f
        val m = FloatArray(16)
        m[0] = f
        m[5] = f
        m[10] = -(far + near) / (far - near)
        m[11] = -1f
        m[14] = -2f * far * near / (far - near)
        val view = FloatArray(16)
        view[0] = 1f; view[5] = 1f; view[10] = 1f; view[15] = 1f
        view[12] = -cam[0]; view[13] = -cam[1]; view[14] = -cam[2]
        val vp = FloatArray(16)
        for (c in 0 until 4) for (r in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += m[k * 4 + r] * view[c * 4 + k]
            vp[c * 4 + r] = sum
        }
        return ArSnapshot(vp, anchor, floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f), cam, 1000, 2000, true, 1, 1)
    }

    @Test
    fun projectionMapsGroundAndHeight() {
        val v = ArView(snapshot(), 0f, 0f, 0.01f)
        val foot = v.foot(0f, 0f)!!
        assertEquals(500f, foot.x, 1f)
        assertTrue(foot.y > 1000f)
        assertTrue(v.heightPx(0f, 0f, 0.2f) > 50f)
        assertTrue(v.foot(50f, 0f)!!.x > foot.x)
        assertTrue(v.heightPx(0f, 100f, 0.2f) > v.heightPx(0f, 0f, 0.2f))
        assertTrue(v.foot(0f, 100f)!!.y > foot.y)
    }

    @Test
    fun noAnchorMeansNothingToDraw() {
        val v = ArView(snapshot(anchor = null), 0f, 0f, 0.01f)
        assertNull(v.foot(0f, 0f))
        assertEquals(0f, v.heightPx(0f, 0f, 0.2f), 0f)
    }

    @Test
    fun pointBehindCameraIsNotProjected() {
        val v = ArView(snapshot(), 0f, 0f, 1f)
        assertNull(v.foot(0f, 5f))
    }

    @Test
    fun elevationAndAzimuthFromCameraPosition() {
        val v = ArView(snapshot(floatArrayOf(0f, 2f, 2f)), 0f, 0f, 0.01f)
        assertEquals(45f, v.elevationDeg(0f, 0f), 0.5f)
        assertEquals(0f, v.azimuthDeg(0f, 0f), 0.5f)
        val right = ArView(snapshot(floatArrayOf(2f, 0.5f, 0f)), 0f, 0f, 0.01f)
        assertEquals(90f, right.azimuthDeg(0f, 0f), 0.5f)
        val left = ArView(snapshot(floatArrayOf(-2f, 0.5f, 0f)), 0f, 0f, 0.01f)
        assertEquals(-90f, left.azimuthDeg(0f, 0f), 0.5f)
        val behind = ArView(snapshot(floatArrayOf(0f, 0.5f, -2f)), 0f, 0f, 0.01f)
        assertEquals(180f, Math.abs(behind.azimuthDeg(0f, 0f)), 0.5f)
    }

    @Test
    fun viewChoiceByElevation() {
        assertEquals(ViewMode.SIDE, ArMath.viewFor(15f))
        assertEquals(ViewMode.TOP, ArMath.viewFor(60f))
    }

    @Test
    fun cameraDirectionTurnsWithTheViewer() {
        assertEquals(0, ArMath.cameraDir(0, 0f))
        assertEquals(6, ArMath.cameraDir(0, 90f))
        assertEquals(2, ArMath.cameraDir(0, -90f))
        assertEquals(0, ArMath.cameraDir(2, 90f))
        assertEquals(4, ArMath.cameraDir(0, 180f))
        for (d in 0..7) for (a in -180..180 step 15) assertTrue(ArMath.cameraDir(d, a.toFloat()) in 0..7)
    }
}
