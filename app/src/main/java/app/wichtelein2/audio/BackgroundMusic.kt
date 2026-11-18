package app.wichtelein2.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import app.wichtelein2.core.Backgrounds
import app.wichtelein2.core.MusicSynth
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Musik zum Startbild: eine eingebaute Weihnachtsmelodie (selbst erzeugt, Endlosschleife) oder eine eigene Musikdatei.
 * Fehler beim Abspielen werden verschluckt: kein Ton ist besser als ein Absturz.
 */
class BackgroundMusic {
    private val synth = AudioPlayer()
    private var media: MediaPlayer? = null

    /** Spielt [code] (siehe [Backgrounds]) in Endlosschleife; [file] ist die eigene Datei bei "file". */
    suspend fun play(code: String, file: File?, volume: Float) {
        stop()
        val track = Backgrounds.track(code)
        if (track != null) {
            val pcm = withContext(Dispatchers.Default) { MusicSynth.render(track) }
            synth.play(pcm, loop = true, volume = volume)
        } else if (code == Backgrounds.MUSIC_FILE && file != null && file.length() > 0) {
            // Der Player wird gleich im Feld gemerkt, damit stop() ihn auch dann freigibt, wenn das Vorbereiten abgebrochen wird
            val mp = withContext(Dispatchers.IO) {
                try {
                    MediaPlayer().also { media = it }.apply {
                        setAudioAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                        )
                        setDataSource(file.path)
                        isLooping = true
                        setVolume(volume, volume)
                        prepare()
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            try {
                mp?.start()
            } catch (_: Throwable) {
            }
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        synth.setVolume(v)
        try {
            media?.setVolume(v, v)
        } catch (_: Throwable) {
        }
    }

    /** true, wenn gerade etwas läuft (für Tests) */
    val isPlaying: Boolean get() = synth.isPlaying || (try { media?.isPlaying == true } catch (_: Throwable) { false })

    fun stop() {
        synth.stop()
        try {
            media?.stop()
        } catch (_: Throwable) {
        }
        try {
            media?.release()
        } catch (_: Throwable) {
        }
        media = null
    }
}
