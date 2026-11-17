package app.wichtelein2.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ton des Wichtels: nur Geräusche und Musik, keine Sprache. Alles wird selbst erzeugt (keine fremden Klangdateien).
 * Dieser Teil kennt kein Android, damit er sich auf dem PC testen lässt.
 */
/** Gruppen von Geräuschen, die man in den Einstellungen einzeln an- und ausschalten kann. */
enum class SoundGroup(val label: String) {
    BROOM("Besen beim Fegen"),
    WHISTLE("Pfeifen beim Fegen (Weihnachtslieder)"),
    BELLS("Glocken beim Fegen (Weihnachtsmelodie)"),
    STARTLE("Erschrecken („Huch“, „Ohh“)"),
    DOOR("Tür auf und zu (Knarren, Knacken)"),
    STEPS("Tip-Tap beim Laufen, Glöckchen, Rascheln"),
}

enum class Fx(val label: String, val group: SoundGroup) {
    /** Mützenglöckchen beim Laufen */
    TINKLE("Glöckchen", SoundGroup.STEPS),
    DOOR_OPEN("Tür auf", SoundGroup.DOOR),
    DOOR_CLOSE("Tür zu", SoundGroup.DOOR),
    /** kleiner Schritt: Tip-Tap */
    STEP("Tip-Tap", SoundGroup.STEPS),
    SWEEP("Besen", SoundGroup.BROOM),
    /** Erschrecken ohne Wörter: kurzes „Huch“ und langgezogenes „Ohh“ */
    HUCH("Huch", SoundGroup.STARTLE),
    OHH("Ohh", SoundGroup.STARTLE),
    /** Ducken: Stoff raschelt */
    RUSTLE("Rascheln", SoundGroup.STEPS),
    WHISTLE_1("Pfeifen 1", SoundGroup.WHISTLE),
    WHISTLE_2("Pfeifen 2", SoundGroup.WHISTLE),
    WHISTLE_3("Pfeifen 3", SoundGroup.WHISTLE),
    BELLS_1("Glocken 1", SoundGroup.BELLS),
    BELLS_2("Glocken 2", SoundGroup.BELLS),
    BELLS_3("Glocken 3", SoundGroup.BELLS),
}

/** Kurze Melodie-Anfänge (Ton:Länge in Schlägen) zum Pfeifen und für die Glocken beim Fegen. */
object Phrases {
    val list: List<Pair<String, Float>> = listOf(
        "D5:.5 G5:.75 G5:.25 G5:1 A5:.5 B5:.75 B5:.25 B5:1 A5:.5 B5:.5 C6:.5 D6:1.5" to 0.28f,          // O Tannenbaum
        "E5:.5 E5:.5 E5:1 E5:.5 E5:.5 E5:1 E5:.5 G5:.5 C5:.75 D5:.25 E5:2" to 0.24f,                     // Jingle Bells
        "G5:.75 A5:.25 G5:.5 E5:1.5 G5:.75 A5:.25 G5:.5 E5:1.5 D6:1 D6:.5 B5:1.5" to 0.3f,               // Stille Nacht
    ).map { it }
}

class SoundEvent(val timeMs: Long, val fx: Fx, val gain: Float = 1f)

enum class MusicTrack(val label: String) {
    SNOWFLAKE("Schneeflocke (Spieluhr)"),
    TANNE("O Tannenbaum"),
    JINGLE("Jingle Bells"),
    NACHT("Stille Nacht"),
}

/** Wahl in den Einstellungen: 0 = aus, 1..4 = eingebaute Melodie. */
object MusicChoice {
    const val OFF = 0
    fun track(choice: Int): MusicTrack? = MusicTrack.entries.getOrNull(choice - 1)
}

object SoundSynth {
    const val RATE = 44100
    private val cache = HashMap<Fx, FloatArray>()

    fun get(fx: Fx): FloatArray = synchronized(cache) { cache.getOrPut(fx) { normalize(render(fx)) } }

    private fun normalize(a: FloatArray, limit: Float = 0.8f): FloatArray {
        var peak = 0f
        for (v in a) peak = maxOf(peak, abs(v))
        if (peak > limit) {
            val k = limit / peak
            for (i in a.indices) a[i] *= k
        }
        return a
    }

    private fun n(sec: Float) = (sec * RATE).toInt()

    /** Glockenton: obere Teiltöne klingen schneller ab. */
    private fun bell(out: FloatArray, startSec: Float, freq: Float, amp: Float, decay: Float = 6f) {
        val s0 = n(startSec)
        val partials = floatArrayOf(1f, 2.76f, 5.4f, 8.9f)
        val weights = floatArrayOf(1f, 0.45f, 0.22f, 0.1f)
        val dec = floatArrayOf(1f, 1.7f, 2.6f, 4f)
        val len = min(out.size - s0, n(1.4f))
        for (i in 0 until len) {
            val t = i.toFloat() / RATE
            var v = 0f
            for (p in partials.indices) v += weights[p] * sin(2.0 * PI * freq * partials[p] * t).toFloat() * exp(-decay * dec[p] * t)
            out[s0 + i] += amp * min(1f, i / 40f) * v
        }
    }

    /** Rauschen mit einfachem Tiefpass (nahe 1 = dumpf) und Hüllkurve. */
    private fun noise(out: FloatArray, startSec: Float, durSec: Float, amp: Float, lowpass: Float, rng: Random, env: (Float) -> Float) {
        val s0 = n(startSec)
        val len = min(out.size - s0, n(durSec))
        var y = 0f
        for (i in 0 until len) {
            val x = rng.nextFloat() * 2f - 1f
            y = lowpass * y + (1f - lowpass) * x
            out[s0 + i] += amp * env(i.toFloat() / len) * y * (1.4f - lowpass * 0.6f) * 2.2f
        }
    }

    /** Gedämpfter Ton (Holz, Schritt). */
    private fun thump(out: FloatArray, startSec: Float, freq: Float, amp: Float, decay: Float) {
        val s0 = n(startSec)
        val len = min(out.size - s0, n(0.5f))
        for (i in 0 until len) {
            val t = i.toFloat() / RATE
            out[s0 + i] += amp * sin(2.0 * PI * freq * t).toFloat() * exp(-decay * t)
        }
    }

    /** Selbstlaut mit Formanten: Stimmton (Obertöne) durch zwei Resonanzen geformt; f0 gleitet von f0a nach f0b. */
    private fun vowel(out: FloatArray, startSec: Float, durSec: Float, f0a: Float, f0b: Float, f1: Float, f2: Float, amp: Float) {
        val s0 = n(startSec)
        val len = min(out.size - s0, n(durSec))
        val phases = DoubleArray(24)
        for (i in 0 until len) {
            val u = i.toFloat() / len
            val t = i.toFloat() / RATE
            val f0 = (f0a + (f0b - f0a) * u) * (1f + 0.012f * sin(2.0 * PI * 5.5 * t).toFloat())
            var v = 0f
            for (h in 1..23) {
                val fh = f0 * h
                if (fh > 5000f) break
                phases[h] += 2.0 * PI * fh / RATE
                val g = exp(-((fh - f1) / 160f).pow(2)) + 0.6f * exp(-((fh - f2) / 220f).pow(2)) + 0.04f
                v += g / h * sin(phases[h]).toFloat()
            }
            val env = min(1f, u * 12f) * min(1f, (1f - u) * 6f)
            out[s0 + i] += amp * env * v
        }
    }

    /** Pfeifton: fast reiner Sinus mit leichtem Vibrato und etwas Luft. */
    private fun whistleNote(out: FloatArray, startSec: Float, durSec: Float, f: Float, amp: Float, rng: Random) {
        val s0 = n(startSec)
        val len = min(out.size - s0, n(durSec))
        var ph = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / RATE
            val fr = f * (1f + 0.006f * sin(2.0 * PI * 5.5 * t).toFloat())
            ph += 2.0 * PI * fr / RATE
            val attack = min(1f, t / 0.03f)
            val release = min(1f, (len - i).toFloat() / (RATE * 0.05f))
            out[s0 + i] += amp * attack * release * (sin(ph).toFloat() + 0.04f * (rng.nextFloat() - 0.5f))
        }
    }

    /** Eine kurze Weihnachtsmelodie, gepfiffen oder als Glöckchen (Glockenspiel). */
    private fun phrase(index: Int, whistle: Boolean, rng: Random): FloatArray {
        val (text, beat) = Phrases.list[index]
        val notes = text.split(' ').map { val (nm, l) = it.split(':'); nm to l.toFloat() }
        val total = notes.sumOf { it.second.toDouble() }.toFloat() * beat + 0.9f
        val out = FloatArray(n(total))
        var t = 0f
        for ((name, len) in notes) {
            val f = MusicSynth.hz(name)
            if (whistle) whistleNote(out, t, len * beat * 0.92f, f, 0.45f, rng) else bell(out, t, f, 0.4f, 5f)
            t += len * beat
        }
        return out
    }

    private fun render(fx: Fx): FloatArray {
        val rng = Random(fx.ordinal * 7919 + 13)
        return when (fx) {
            Fx.TINKLE -> FloatArray(n(0.5f)).also { o ->
                bell(o, 0f, 3136f, 0.16f, 12f)
                bell(o, 0.07f, 3951f, 0.12f, 14f)
            }
            Fx.DOOR_OPEN -> FloatArray(n(1.0f)).also { o ->
                // Quietschen: Ton mit schwankender Höhe plus Raunen
                var ph = 0.0
                val len = n(0.95f)
                for (i in 0 until len) {
                    val u = i.toFloat() / len
                    val f = 380f + 520f * u + 60f * sin(u * 38f)
                    ph += 2.0 * PI * f / RATE
                    val saw = ((ph / (2 * PI)) % 1.0).toFloat() * 2f - 1f
                    val env = sin(u * PI).toFloat().pow(0.7f)
                    o[i] += 0.16f * env * (0.7f * saw + 0.3f * sin(ph).toFloat())
                }
                noise(o, 0f, 0.95f, 0.05f, 0.9f, rng) { sin(it * PI).toFloat() }
                thump(o, 0f, 95f, 0.2f, 14f)
                // Knacken im Holz
                for (k in 0 until 6) noise(o, 0.08f + k * 0.15f + rng.nextFloat() * 0.07f, 0.008f, 0.55f, 0.1f, rng) { exp(-4f * it) }
            }
            Fx.DOOR_CLOSE -> FloatArray(n(0.7f)).also { o ->
                thump(o, 0f, 70f, 0.8f, 11f)
                thump(o, 0f, 140f, 0.35f, 18f)
                noise(o, 0f, 0.08f, 0.5f, 0.55f, rng) { exp(-6f * it) }
                bell(o, 0.02f, 2500f, 0.05f, 14f)
                noise(o, 0.1f, 0.01f, 0.45f, 0.1f, rng) { exp(-4f * it) }   // Knacks
                noise(o, 0.17f, 0.008f, 0.35f, 0.1f, rng) { exp(-4f * it) }
            }
            Fx.STEP -> FloatArray(n(0.16f)).also { o ->
                // Tip-Tap: zwei kleine, helle Klopfer
                thump(o, 0f, 520f, 0.45f, 90f)
                noise(o, 0f, 0.012f, 0.3f, 0.2f, rng) { exp(-5f * it) }
                thump(o, 0.07f, 430f, 0.32f, 100f)
                noise(o, 0.07f, 0.01f, 0.2f, 0.2f, rng) { exp(-5f * it) }
            }
            Fx.SWEEP -> FloatArray(n(0.5f)).also { o ->
                noise(o, 0f, 0.45f, 0.5f, 0.72f, rng) { u -> sin(u * PI).toFloat().pow(1.4f) }
            }
            Fx.HUCH -> FloatArray(n(0.5f)).also { o ->
                // „H“ (Hauch), „u“ steigend, „ch“ (Zischen): ein Huch ohne echte Sprache
                noise(o, 0f, 0.06f, 0.25f, 0.4f, rng) { sin(it * PI).toFloat() }
                vowel(o, 0.05f, 0.17f, 330f, 560f, 320f, 800f, 0.5f)
                noise(o, 0.2f, 0.14f, 0.22f, 0.15f, rng) { u -> sin(u * PI).toFloat() * (1f - u * 0.5f) }
            }
            Fx.OHH -> FloatArray(n(0.9f)).also { o ->
                // „Ohhh“: langgezogenes o, Tonhöhe fällt, Zittern am Ende
                vowel(o, 0.02f, 0.8f, 470f, 330f, 500f, 900f, 0.55f)
            }
            Fx.RUSTLE -> FloatArray(n(0.3f)).also { o ->
                noise(o, 0f, 0.26f, 0.35f, 0.45f, rng) { u -> sin(u * PI).toFloat() * (0.6f + 0.4f * sin(u * 70f)) }
            }
            Fx.WHISTLE_1, Fx.WHISTLE_2, Fx.WHISTLE_3 -> phrase(fx.ordinal - Fx.WHISTLE_1.ordinal, whistle = true, rng = rng)
            Fx.BELLS_1, Fx.BELLS_2, Fx.BELLS_3 -> phrase(fx.ordinal - Fx.BELLS_1.ordinal, whistle = false, rng = rng)
        }
    }
}

object MusicSynth {
    const val RATE = SoundSynth.RATE
    private val cache = HashMap<MusicTrack, FloatArray>()

    fun render(t: MusicTrack): FloatArray = synchronized(cache) { cache.getOrPut(t) { build(t) } }

    private val names = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** "G4" oder "F#4" in Hz. */
    fun hz(note: String): Float {
        var i = 1
        var semi = names.getValue(note[0])
        if (note[i] == '#') {
            semi += 1
            i++
        }
        val midi = 12 * (note.substring(i).toInt() + 1) + semi
        return (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
    }

    private class Tune(val notes: List<Pair<String, Float>>, val beatSec: Float, val drone: List<String>, val arpeggio: List<String> = emptyList()) {
        val beats: Float get() = notes.sumOf { it.second.toDouble() }.toFloat()
    }

    private fun parse(s: String) = s.trim().split(Regex("\\s+")).map { val (n, l) = it.split(':'); n to l.toFloat() }

    private val tunes = mapOf(
        MusicTrack.SNOWFLAKE to Tune(
            parse(
                "E5:1 D5:1 C5:1 D5:1.5 E5:.5 G5:1 E5:2 D5:1 C5:3 A4:1 C5:1 D5:1 E5:1.5 D5:.5 C5:1 D5:2 A4:1 C5:3 " +
                    "G5:1.5 E5:.5 D5:1 E5:1.5 C5:.5 A4:1 D5:1 E5:1 G5:1 E5:3 D5:1 C5:1 A4:1 C5:2 D5:1 E5:1 D5:1 C5:1 C5:3",
            ),
            0.42f, listOf("C3", "G3"), listOf("C4", "E4", "G4", "E4"),
        ),
        MusicTrack.TANNE to Tune(
            parse("D4:1 G4:1.5 G4:.5 G4:2 A4:1 B4:1.5 B4:.5 B4:2 A4:1 B4:1 C5:1 D5:1.5 B4:.5 A4:1 G4:1 F#4:1 G4:3"),
            0.5f, listOf("G2", "D3"),
        ),
        MusicTrack.JINGLE to Tune(
            parse("E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1.5 D4:.5 E4:4 F4:1 F4:1 F4:1.5 F4:.5 F4:1 E4:1 E4:1 E4:.5 E4:.5 E4:1 D4:1 D4:1 E4:1 D4:2 G4:2"),
            0.3f, listOf("C3", "G3"),
        ),
        MusicTrack.NACHT to Tune(
            parse(
                "G4:1.5 A4:.5 G4:1 E4:3 G4:1.5 A4:.5 G4:1 E4:3 D5:2 D5:1 B4:3 C5:2 C5:1 G4:3 A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3 " +
                    "A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3 D5:2 D5:1 F5:1.5 D5:.5 B4:1 C5:3 E5:3 C5:1 G4:1 E4:1 G4:1.5 F4:.5 D4:1 C4:3",
            ),
            0.42f, listOf("G2", "D3"),
        ),
    )

    private fun bell(out: FloatArray, start: Float, f: Float, lenSec: Float, amp: Float) {
        val s0 = (start * RATE).toInt()
        val len = min((lenSec * RATE).toInt(), out.size - s0)
        if (s0 < 0 || len <= 0) return
        val parts = floatArrayOf(1f, 2f, 3.01f, 4.2f)
        val w = floatArrayOf(1f, 0.35f, 0.12f, 0.05f)
        for (i in 0 until len) {
            val t = i.toFloat() / RATE
            var v = 0f
            for (p in parts.indices) v += w[p] * sin(2.0 * PI * f * parts[p] * t).toFloat() * exp(-(1.6f + 1.4f * p) * t / (lenSec * 0.55f))
            val attack = min(1f, i / 80f)
            val release = min(1f, (len - i).toFloat() / (RATE * 0.04f))
            out[s0 + i] += amp * attack * release * v
        }
    }

    private fun build(t: MusicTrack): FloatArray {
        val tune = tunes.getValue(t)
        val beat = tune.beatSec
        val oneRun = tune.beats * beat
        val out = FloatArray(((oneRun + 1.5f) * RATE).toInt())
        var time = 0f
        for ((name, len) in tune.notes) {
            bell(out, time, hz(name), (len * beat * 1.9f).coerceAtLeast(0.6f), 0.28f)
            time += len * beat
        }
        // leise Begleitung: Bordun oder Arpeggio
        val barSec = if (t == MusicTrack.JINGLE) 4 * beat else 3 * beat
        var d = 0
        var tt = 0f
        while (tt < oneRun) {
            bell(out, tt, hz(tune.drone[d % tune.drone.size]), barSec * 1.6f, 0.13f)
            d++
            tt += barSec
        }
        if (tune.arpeggio.isNotEmpty()) {
            var k = 0
            var ta = 0f
            while (ta < oneRun) {
                bell(out, ta, hz(tune.arpeggio[k % tune.arpeggio.size]), beat * 1.5f, 0.08f)
                k++
                ta += beat
            }
        }
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        if (peak > 0.9f) for (i in out.indices) out[i] *= 0.9f / peak
        return out
    }
}

/** Welche Geräusche zu welchem Zeitpunkt des Ablaufs gehören (aus den Bildern des Simulators). */
object SoundPlan {
    fun events(sim: Simulation): List<SoundEvent> {
        val out = ArrayList<SoundEvent>()
        var steps = 0
        var scares = 0
        var sweeps = 0
        var nextWhistle = 0L
        var nextBells = 0L
        var whistles = 0
        var bells = 0
        for (i in 1 until sim.frames.size) {
            val a = sim.frames[i - 1]
            val b = sim.frames[i]
            val ms = (b.t * 1000f).toLong()
            if (a.doorOpen < 0.05f && b.doorOpen >= 0.05f) out.add(SoundEvent(ms, Fx.DOOR_OPEN, 0.8f))
            if (a.doorOpen > 0.05f && b.doorOpen <= 0.05f) out.add(SoundEvent(ms, Fx.DOOR_CLOSE, 0.9f))
            if (b.houseT > 0.6f) continue
            if (b.anim == Anim.SWEEP && a.anim != Anim.SWEEP) {
                // neues Fegen: Pfeifen beginnt gleich, die Glocken etwas später, damit sie sich abwechseln
                sweeps++
                nextWhistle = ms + 300
                nextBells = ms + 2200
            }
            if (b.anim == Anim.SWEEP) {
                if (ms >= nextWhistle) {
                    val fx = Fx.entries[Fx.WHISTLE_1.ordinal + whistles % 3]
                    out.add(SoundEvent(ms, fx, 0.55f))
                    nextWhistle = ms + (SoundSynth.get(fx).size * 1000L / SoundSynth.RATE) + 2500
                    whistles++
                }
                if (ms >= nextBells) {
                    val fx = Fx.entries[Fx.BELLS_1.ordinal + bells % 3]
                    out.add(SoundEvent(ms, fx, 0.5f))
                    nextBells = ms + (SoundSynth.get(fx).size * 1000L / SoundSynth.RATE) + 3500
                    bells++
                }
            }
            val changed = b.anim != a.anim || b.frame != a.frame
            if (!changed) continue
            when (b.anim) {
                Anim.WALK, Anim.SNEAK, Anim.RUN -> if (b.frame % 4 == 0) {
                    val g = when (b.anim) { Anim.SNEAK -> 0.4f; Anim.RUN -> 1f; else -> 0.7f }
                    out.add(SoundEvent(ms, Fx.STEP, g))
                    steps++
                    if (b.anim != Anim.SNEAK && steps % 5 == 0) out.add(SoundEvent(ms + 20, Fx.TINKLE, 0.6f))
                }
                Anim.SWEEP -> if (b.frame % 4 == 0) out.add(SoundEvent(ms, Fx.SWEEP, 0.7f))
                Anim.SCARE -> if (a.anim != Anim.SCARE) {
                    out.add(SoundEvent(ms, if (scares % 2 == 0) Fx.HUCH else Fx.OHH, 0.9f))
                    scares++
                }
                Anim.DUCK -> if (a.anim != Anim.DUCK) out.add(SoundEvent(ms, Fx.RUSTLE, 0.7f))
                else -> Unit
            }
        }
        return out
    }
}

/** Mischt Geräusche (nur die gewählten Gruppen) und Musik zum fertigen Ton des Ablaufs (Mono, [SoundSynth.RATE] Hz). */
object Mixdown {
    const val TAIL_SEC = 0.6f

    fun mix(
        sim: Simulation,
        groups: Set<SoundGroup>,
        music: MusicTrack?,
        fxVolume: Float = 0.8f,
        musicVolume: Float = 0.5f,
    ): FloatArray? {
        if (groups.isEmpty() && music == null) return null
        val acc = FloatArray(((sim.duration + TAIL_SEC) * SoundSynth.RATE).toInt())
        if (music != null) {
            val m = MusicSynth.render(music)
            val fade = (1.5f * SoundSynth.RATE).toInt()
            for (i in acc.indices) {
                val remaining = acc.size - i
                val f = if (remaining < fade) remaining.toFloat() / fade else 1f
                acc[i] += m[i % m.size] * musicVolume * f
            }
        }
        for (ev in SoundPlan.events(sim)) {
            if (ev.fx.group !in groups) continue
            val data = SoundSynth.get(ev.fx)
            val start = (ev.timeMs * SoundSynth.RATE / 1000L).toInt()
            val g = ev.gain * fxVolume
            for (j in data.indices) {
                val idx = start + j
                if (idx >= acc.size) break
                if (idx >= 0) acc[idx] += data[j] * g
            }
        }
        var peak = 0f
        for (v in acc) peak = maxOf(peak, abs(v))
        if (peak > 0.95f) {
            val k = 0.95f / peak
            for (i in acc.indices) acc[i] *= k
        }
        return acc
    }

    /** Einstellungen speichern als Bitmaske (Bit i = SoundGroup.entries[i]). */
    fun toMask(groups: Set<SoundGroup>): Int = groups.fold(0) { m, g -> m or (1 shl g.ordinal) }
    fun fromMask(mask: Int): Set<SoundGroup> = SoundGroup.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()
}
