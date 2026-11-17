package app.wichtelein2.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.wichtelein2.core.Plan
import app.wichtelein2.core.Simulation
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Schreibt den Ablauf als MP4 (H.264, ohne Ton): Bild für Bild gezeichnet und in den Encoder gegeben. */
object Exporter {
    class Result(val file: File, val frames: Int, val width: Int, val height: Int)

    fun export(
        plan: Plan,
        photo: Bitmap,
        sprites: SpriteLibrary,
        customInterior: Bitmap?,
        sim: Simulation,
        out: File,
        maxSide: Int = 1280,
        onProgress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): Result {
        val scale = min(1f, maxSide.toFloat() / max(plan.width, plan.height))
        var w = (plan.width * scale).toInt()
        var h = (plan.height * scale).toInt()
        w -= w % 2
        h -= h % 2
        val fps = sim.fps
        val renderer = Renderer(plan, photo, sprites, customInterior)
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, max(1_500_000, w * h * 5))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxStarted = false
        var written = 0
        val info = MediaCodec.BufferInfo()

        // Holt fertige Daten aus dem Encoder; end = auf das Ende des Stroms warten.
        fun drain(end: Boolean) {
            var idle = 0
            while (true) {
                val idx = encoder.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!end) return
                        idle++
                        if (idle > 500) throw IllegalStateException("Encoder liefert kein Ende")
                    }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxStarted = true
                    }
                    idx >= 0 -> {
                        val buf = encoder.getOutputBuffer(idx)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && buf != null && muxStarted) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            // Zeitstempel selbst setzen: Canvas-Eingabe liefert Uhrzeit statt Videozeit
                            info.presentationTimeUs = written * 1_000_000L / fps
                            muxer.writeSampleData(track, buf, info)
                            written++
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(idx, false)
                        if (eos) return
                    }
                }
            }
        }

        val frameBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBmp)
        try {
            for ((i, f) in sim.frames.withIndex()) {
                if (cancelled()) throw InterruptedException("abgebrochen")
                canvas.save()
                canvas.scale(w.toFloat() / plan.width, h.toFloat() / plan.height)
                renderer.draw(canvas, f)
                canvas.restore()
                val sc = surface.lockCanvas(null)
                try {
                    sc.drawBitmap(frameBmp, 0f, 0f, null)
                } finally {
                    surface.unlockCanvasAndPost(sc)
                }
                drain(false)
                if (i % 5 == 0) onProgress(i.toFloat() / sim.frames.size)
            }
            encoder.signalEndOfInputStream()
            drain(true)
            onProgress(1f)
        } finally {
            try { encoder.stop() } catch (_: Exception) { }
            encoder.release()
            surface.release()
            try { if (muxStarted) muxer.stop() } catch (_: Exception) { }
            muxer.release()
            frameBmp.recycle()
        }
        return Result(out, written, w, h)
    }

    /** Kopiert das fertige Video in „Filme/Wichtelein2“ (sichtbar in der Galerie); liefert die Uri. */
    fun saveToGallery(context: Context, file: File): Uri? {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Wichtelein2")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        cr.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
        v.clear()
        v.put(MediaStore.Video.Media.IS_PENDING, 0)
        cr.update(uri, v, null, null)
        return uri
    }
}
