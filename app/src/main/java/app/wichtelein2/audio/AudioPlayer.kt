package app.wichtelein2.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.wichtelein2.core.SoundSynth

/**
 * Spielt den fertig gemischten Ton ab (Vorschau und AR). Start an beliebiger Stelle, Pause = Stopp;
 * Kein Ton ist besser als ein Absturz: Fehler beim Abspielen werden verschluckt.
 */
class AudioPlayer {
    private var track: AudioTrack? = null

    /** true, wenn der Ton gerade läuft (für Tests) */
    val isPlaying: Boolean get() = try { track?.playState == AudioTrack.PLAYSTATE_PLAYING } catch (_: Throwable) { false }

    /** Spielt [pcm] ab Sekunde [fromSec]; mit [loop] immer wieder von vorn. */
    fun play(pcm: FloatArray, fromSec: Float = 0f, loop: Boolean = false, volume: Float = 1f) {
        stop()
        if (pcm.isEmpty()) return
        try {
            val from = (fromSec * SoundSynth.RATE).toInt().coerceIn(0, pcm.size - 1)
            val n = pcm.size - from
            val shorts = ShortArray(n) { (pcm[from + it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SoundSynth.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(shorts.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(shorts, 0, shorts.size)
            if (loop) t.setLoopPoints(0, shorts.size, -1)
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
            track = t
        } catch (_: Throwable) {
            track = null
        }
    }

    fun stop() {
        try {
            track?.stop()
        } catch (_: Throwable) {
        }
        try {
            track?.release()
        } catch (_: Throwable) {
        }
        track = null
    }
}
