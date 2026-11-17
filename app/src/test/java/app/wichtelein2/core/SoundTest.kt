package app.wichtelein2.core

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTest {
    private fun sim(steps: List<Step>): Simulation =
        Simulator(Plan.default(1600, 1000).copy(steps = steps)).run()

    private val all = SoundGroup.entries.toSet()

    private val steps3 = listOf(
        Step(ActionType.SWEEP, seconds = 3f),
        Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 1f),
        Step(ActionType.SCARE, retreat = Retreat.DOOR),
    )

    @Test
    fun everyEffectIsAudibleAndWithinRange() {
        for (fx in Fx.entries) {
            val a = SoundSynth.get(fx)
            assertTrue("$fx zu kurz", a.size > 1000)
            val peak = a.maxOf { abs(it) }
            assertTrue("$fx still", peak > 0.1f)
            assertTrue("$fx übersteuert", peak <= 0.81f)
            assertTrue("$fx hat ungültige Werte", a.none { it.isNaN() || it.isInfinite() })
        }
    }

    @Test
    fun synthIsDeterministic() {
        val a = SoundSynth.get(Fx.SWEEP).copyOf()
        val b = SoundSynth.get(Fx.SWEEP)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun musicTracksAreLongAndQuiet() {
        for (t in MusicTrack.entries) {
            val m = MusicSynth.render(t)
            assertTrue("$t zu kurz", m.size > 8 * SoundSynth.RATE)
            val peak = m.maxOf { abs(it) }
            assertTrue(peak in 0.2f..0.91f)
        }
    }

    @Test
    fun noteFrequencies() {
        assertEquals(440f, MusicSynth.hz("A4"), 0.01f)
        assertEquals(261.63f, MusicSynth.hz("C4"), 0.05f)
        assertEquals(369.99f, MusicSynth.hz("F#4"), 0.05f)
    }

    @Test
    fun planHasDoorStepsSweepsScareAndTinkle() {
        val ev = SoundPlan.events(sim(steps3))
        val kinds = ev.map { it.fx }.toSet()
        for (fx in listOf(Fx.DOOR_OPEN, Fx.DOOR_CLOSE, Fx.STEP, Fx.SWEEP, Fx.HUCH, Fx.TINKLE)) assertTrue("$fx fehlt", fx in kinds)
        assertEquals(ev.count { it.fx == Fx.DOOR_OPEN }, ev.count { it.fx == Fx.DOOR_CLOSE })
    }

    @Test
    fun hidingBehindObjectDucks() {
        val tree = SceneObject(1, ObjType.TREE, 350f, 560f, 260f, 420f)
        val plan = Plan.default(1600, 1000).copy(
            objects = listOf(tree), nextId = 2,
            steps = listOf(Step(ActionType.HIDE, retreat = Retreat.OBJECT, objectId = 1, seconds = 2f)),
        )
        val ev = SoundPlan.events(Simulator(plan).run())
        assertTrue(ev.any { it.fx == Fx.RUSTLE })
    }

    @Test
    fun eventsAreInsideTheVideo() {
        val s = sim(steps3)
        val ev = SoundPlan.events(s)
        assertTrue(ev.isNotEmpty())
        for (e in ev) assertTrue(e.timeMs in 0..(s.duration * 1000).toLong())
    }

    @Test
    fun stepSoundsFollowTheSteps() {
        val s = sim(listOf(Step(ActionType.SWEEP, seconds = 2f)))
        val steps = SoundPlan.events(s).filter { it.fx == Fx.STEP }
        assertTrue(steps.size >= 3)
        for (i in 1 until steps.size) assertTrue(steps[i].timeMs - steps[i - 1].timeMs >= 100)
    }

    @Test
    fun mixdownCombinesAndLimits() {
        val s = sim(steps3)
        val m = Mixdown.mix(s, all, music = MusicTrack.SNOWFLAKE, fxVolume = 1f, musicVolume = 1f)
        assertNotNull(m)
        assertEquals((s.duration + Mixdown.TAIL_SEC) * SoundSynth.RATE, m!!.size.toFloat(), 2f)
        assertTrue(m.maxOf { abs(it) } <= 0.951f)
        val last = m.takeLast(200).maxOf { abs(it) }
        val mid = m.slice(m.size / 2 until m.size / 2 + 20000).maxOf { abs(it) }
        assertTrue(last < mid)
    }

    @Test
    fun mixdownPartsAreIndependent() {
        val s = sim(steps3)
        assertNull(Mixdown.mix(s, emptySet(), music = null))
        val onlyFx = Mixdown.mix(s, all, null)!!
        val onlyMusic = Mixdown.mix(s, emptySet(), MusicTrack.TANNE)!!
        assertTrue(onlyFx.maxOf { abs(it) } > 0.05f)
        assertTrue(onlyMusic.maxOf { abs(it) } > 0.05f)
        // Geräusche allein: der Anfang ist still (die Tür öffnet erst nach 0,6 s)
        assertTrue(onlyFx.take(2000).maxOf { abs(it) } < 0.001f)
    }

    @Test
    fun volumeScalesFx() {
        val s = sim(steps3)
        val loud = Mixdown.mix(s, all, null, fxVolume = 0.8f)!!.maxOf { abs(it) }
        val soft = Mixdown.mix(s, all, null, fxVolume = 0.2f)!!.maxOf { abs(it) }
        assertTrue(soft < loud * 0.5f)
    }
}
