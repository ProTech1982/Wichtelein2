package app.wichtelein2.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import app.wichtelein2.core.SoundSynth
import java.io.File
import java.nio.ByteBuffer

/**
 * Fügt einem fertigen MP4 ohne Ton eine AAC-Tonspur hinzu: Das Video wird unverändert übernommen (kein neues Kodieren),
 * der Ton wird mit MediaCodec als AAC kodiert und nach Zeitstempeln dazwischen gemischt.
 */
object AudioMux {
    private class Sample(val data: ByteArray, val ptsUs: Long, val flags: Int)

    fun addAudio(videoOnly: File, pcm: FloatArray, out: File) {
        val audio = encodeAac(pcm)
        val ex = MediaExtractor()
        ex.setDataSource(videoOnly.path)
        var vTrack = -1
        for (i in 0 until ex.trackCount) {
            if ((ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                vTrack = i
                break
            }
        }
        require(vTrack >= 0) { "Video ohne Bildspur" }
        ex.selectTrack(vTrack)
        val vFormat = ex.getTrackFormat(vTrack)

        if (out.exists()) out.delete()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val vOut = muxer.addTrack(vFormat)
            val aOut = muxer.addTrack(audio.first)
            muxer.start()
            started = true

            val maxSize = if (vFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) vFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buf = ByteBuffer.allocate(maxOf(maxSize, 2 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            var ai = 0
            val samples = audio.second

            fun writeAudioUpTo(limitUs: Long) {
                while (ai < samples.size && samples[ai].ptsUs <= limitUs) {
                    val s = samples[ai++]
                    info.set(0, s.data.size, s.ptsUs, s.flags)
                    muxer.writeSampleData(aOut, ByteBuffer.wrap(s.data), info)
                }
            }

            while (true) {
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val pts = ex.sampleTime
                writeAudioUpTo(pts)
                info.set(0, n, pts, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(vOut, buf, info)
                ex.advance()
            }
            writeAudioUpTo(Long.MAX_VALUE)
        } finally {
            ex.release()
            try { if (started) muxer.stop() } catch (_: Exception) { }
            muxer.release()
        }
    }

    /** Kodiert Mono-Rohdaten als AAC-LC; liefert das Ausgabeformat und alle Pakete. */
    private fun encodeAac(pcm: FloatArray): Pair<MediaFormat, List<Sample>> {
        val rate = SoundSynth.RATE
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val result = ArrayList<Sample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inputDone = false
        var outputDone = false
        var guard = 0
        try {
            while (!outputDone) {
                if (++guard > 2_000_000) throw IllegalStateException("AAC-Encoder liefert kein Ende")
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(5_000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        ib.clear()
                        val maxSamples = ib.remaining() / 2
                        val count = minOf(maxSamples, pcm.size - pos)
                        val ptsUs = pos * 1_000_000L / rate
                        if (count <= 0) {
                            codec.queueInputBuffer(ii, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            ib.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            for (k in 0 until count) ib.putShort((pcm[pos + k].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                            codec.queueInputBuffer(ii, 0, count * 2, ptsUs, 0)
                            pos += count
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    oi >= 0 -> {
                        val ob = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            ob.get(bytes)
                            result.add(Sample(bytes, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME))
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        codec.releaseOutputBuffer(oi, false)
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
        }
        return Pair(outFormat ?: format, result)
    }
}
