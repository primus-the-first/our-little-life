package com.ourlittlelife.game.privacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.ourlittlelife.game.data.PrivateMedia
import com.ourlittlelife.rules.Private
import java.io.ByteArrayOutputStream
import java.io.File

/** Photos and voice messages for the private side: made small enough to keep, and never left lying on the phone. */
object Media {

    private const val PHOTO_EDGE = 1280

    /** This app's own scratch folder for a photo being taken or a message being recorded. Emptied after each use. */
    fun scratch(context: Context): File = File(context.cacheDir, "private").apply { mkdirs() }

    fun clearScratch(context: Context) {
        scratch(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * The picture at [uri], turned the right way up, shrunk to at most 1280 px on its long side and saved
     * as a JPEG small enough to keep. Re-encoding also drops where and when it was taken. Null if it can't be read.
     */
    fun shrinkPhoto(context: Context, uri: Uri): ByteArray? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > PHOTO_EDGE) {
                    val scale = PHOTO_EDGE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > PHOTO_EDGE * 2) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        } ?: return@runCatching null
        encode(bitmap)
    }.getOrNull()

    /** JPEG, stepping the quality down until it fits. */
    fun encode(bitmap: Bitmap): ByteArray? {
        for (quality in listOf(80, 68, 55, 42, 30)) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= PrivateMedia.MAX_BYTES) return out.toByteArray()
        }
        return null
    }
}

/** Records a voice message into the scratch folder: speech-quality AAC, small, a minute at most. */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private val file get() = File(Media.scratch(context), "voice.m4a")

    val recording: Boolean get() = recorder != null

    /** False if the microphone can't be opened. */
    fun start(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val made = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        made.setAudioSource(MediaRecorder.AudioSource.MIC)
        made.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        made.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        made.setAudioChannels(1)
        made.setAudioSamplingRate(16_000)
        made.setAudioEncodingBitRate(24_000)
        made.setMaxDuration(Private.VOICE_SECONDS * 1000)
        made.setOutputFile(file.absolutePath)
        made.prepare()
        made.start()
        recorder = made
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse { cancel(); false }

    /** Stops and hands back the message and how many seconds it runs; null if nothing usable was recorded. */
    fun finish(): Pair<ByteArray, Int>? {
        val made = recorder ?: return null
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceIn(1, Private.VOICE_SECONDS)
        val ok = runCatching { made.stop() }.isSuccess
        made.release()
        recorder = null
        val bytes = file.takeIf { ok && it.exists() }?.readBytes()
        file.delete()
        return bytes?.takeIf { it.isNotEmpty() && it.size <= PrivateMedia.MAX_BYTES }?.let { it to seconds }
    }

    fun cancel() {
        recorder?.let { made -> runCatching { made.stop() }; made.release() }
        recorder = null
        file.delete()
    }
}

/** Plays a voice message straight from memory, so it's never written to the phone's storage. */
class VoicePlayer {

    private var player: MediaPlayer? = null

    fun play(bytes: ByteArray, onDone: () -> Unit) {
        stop()
        runCatching {
            val made = MediaPlayer()
            made.setDataSource(object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= bytes.size) return -1
                    val count = minOf(size, bytes.size - position.toInt())
                    System.arraycopy(bytes, position.toInt(), buffer, offset, count)
                    return count
                }

                override fun getSize(): Long = bytes.size.toLong()
                override fun close() = Unit
            })
            made.setOnCompletionListener { stop(); onDone() }
            made.prepare()
            made.start()
            player = made
        }.onFailure { stop(); onDone() }
    }

    fun stop() {
        player?.let { made -> runCatching { made.stop() }; made.release() }
        player = null
    }
}
