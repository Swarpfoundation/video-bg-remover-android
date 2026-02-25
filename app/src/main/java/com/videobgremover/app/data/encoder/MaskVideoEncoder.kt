package com.videobgremover.app.data.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.videobgremover.app.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Encoder for creating MP4 mask videos using MediaCodec.
 *
 * Encodes grayscale mask frames (as YUV) into H.264 video.
 * White = opaque, Black = transparent (luma channel carries mask).
 */
class MaskVideoEncoder {

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private var isMuxerStarted: Boolean = false

    private var frameCount: Int = 0
    private var configuredWidth: Int = 0
    private var configuredHeight: Int = 0
    private var frameDurationUs: Long = 0

    /**
     * Initialize the encoder.
     *
     * @param outputFile Output MP4 file
     * @param width Video width
     * @param height Video height
     * @param frameRate Frame rate (FPS)
     * @param bitrate Target bitrate
     */
    fun initialize(
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Int = 30,
        bitrate: Int = calculateBitrate(width, height)
    ): Boolean {
        if (width <= 0 || height <= 0 || frameRate <= 0) {
            Logger.e("Invalid encoder parameters: ${width}x${height} @ ${frameRate}fps")
            return false
        }
        if (width % 2 != 0 || height % 2 != 0) {
            Logger.e("Encoder requires even dimensions for YUV420 input: ${width}x${height}")
            return false
        }

        try {
            frameCount = 0
            configuredWidth = width
            configuredHeight = height
            frameDurationUs = 1_000_000L / frameRate

            // Create muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Create encoder format
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            // Create and configure encoder
            encoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            encoder?.start()

            Logger.d("MaskVideoEncoder initialized: ${width}x${height} @ ${frameRate}fps, ${bitrate}bps")
            return true
        } catch (e: Exception) {
            Logger.e("Failed to initialize encoder", e)
            release()
            return false
        }
    }

    /**
     * Encode a single mask frame.
     *
     * @param maskData FloatArray of mask values (0.0 to 1.0)
     * @param width Frame width
     * @param height Frame height
     * @return true if successful
     */
    suspend fun encodeFrame(
        maskData: FloatArray,
        width: Int,
        height: Int
    ): Boolean = withContext(Dispatchers.Default) {
        val enc = encoder ?: return@withContext false
        if (width != configuredWidth || height != configuredHeight) {
            Logger.e(
                "Frame size ${width}x${height} does not match encoder size " +
                    "${configuredWidth}x${configuredHeight}"
            )
            return@withContext false
        }
        if (maskData.size < width * height) {
            Logger.e("Mask data too small for frame: ${maskData.size} < ${width * height}")
            return@withContext false
        }

        try {
            // Convert mask to YUV
            val yuvData = convertMaskToYuv(maskData, width, height)

            // Get input buffer
            val inputBufferId = enc.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferId < 0) {
                drainEncoder(false)
                return@withContext false
            }

            val inputBuffer = enc.getInputBuffer(inputBufferId)
                ?: return@withContext false
            inputBuffer.clear()
            inputBuffer.put(yuvData)

            val pts = frameCount * frameDurationUs
            enc.queueInputBuffer(
                inputBufferId,
                0,
                yuvData.size,
                pts,
                0
            )

            // Drain output
            drainEncoder(false)

            frameCount++
            true
        } catch (e: Exception) {
            Logger.e("Failed to encode frame", e)
            false
        }
    }

    /**
     * Finish encoding and release resources.
     */
    suspend fun finish(): Boolean = withContext(Dispatchers.Default) {
        try {
            // Send end-of-stream
            val enc = encoder ?: return@withContext false
            var inputBufferId = enc.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferId < 0) {
                drainEncoder(false)
                inputBufferId = enc.dequeueInputBuffer(TIMEOUT_US)
            }
            if (inputBufferId < 0) {
                Logger.e("Failed to obtain input buffer for EOS")
                return@withContext false
            }

            val eosPts = frameCount * frameDurationUs
            enc.queueInputBuffer(
                inputBufferId,
                0,
                0,
                eosPts,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )

            // Drain remaining output
            drainEncoder(true)

            // Stop and release
            encoder?.stop()
            if (isMuxerStarted) {
                muxer?.stop()
            }

            Logger.d("MaskVideoEncoder finished: $frameCount frames encoded")
            true
        } catch (e: Exception) {
            Logger.e("Failed to finish encoding", e)
            false
        } finally {
            release()
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        try {
            encoder?.release()
            encoder = null

            muxer?.release()
            muxer = null

            isMuxerStarted = false
            trackIndex = -1
            frameCount = 0
            configuredWidth = 0
            configuredHeight = 0
            frameDurationUs = 0
        } catch (e: Exception) {
            Logger.e("Error releasing encoder", e)
        }
    }

    /**
     * Drain encoder output buffers.
     */
    private fun drainEncoder(endOfStream: Boolean) {
        val enc = encoder ?: return
        val mux = muxer ?: return

        val bufferInfo = MediaCodec.BufferInfo()
        var tryAgainCount = 0

        while (true) {
            val outputBufferId = enc.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    tryAgainCount++
                    if (tryAgainCount >= MAX_EOS_DRAIN_RETRIES) {
                        Logger.w("Timed out draining encoder EOS after $MAX_EOS_DRAIN_RETRIES retries")
                        break
                    }
                }

                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    tryAgainCount = 0
                    if (isMuxerStarted) {
                        throw RuntimeException("Format changed twice")
                    }
                    val newFormat = enc.outputFormat
                    trackIndex = mux.addTrack(newFormat)
                    mux.start()
                    isMuxerStarted = true
                }

                outputBufferId >= 0 -> {
                    tryAgainCount = 0
                    val outputBuffer = enc.getOutputBuffer(outputBufferId)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && isMuxerStarted) {
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                        mux.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                    }

                    enc.releaseOutputBuffer(outputBufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }

    /**
     * Convert mask float array to YUV420 bytes.
     * Mask values 0.0-1.0 are mapped to Y channel (luma).
     * U and V are set to 128 (neutral chroma for grayscale).
     */
    private fun convertMaskToYuv(
        maskData: FloatArray,
        width: Int,
        height: Int
    ): ByteArray {
        require(maskData.size >= width * height) {
            "Mask data too small: ${maskData.size} < ${width * height}"
        }
        val ySize = width * height
        val uvSize = ySize / 4
        val yuvData = ByteArray(ySize + uvSize * 2)

        // Fill Y plane (luma) - mask values directly
        for (i in 0 until ySize) {
            val maskValue = maskData[i].coerceIn(0f, 1f)
            yuvData[i] = (maskValue * 255).toInt().toByte()
        }

        // Fill U and V planes (chroma) - neutral gray (128)
        val uOffset = ySize
        val vOffset = ySize + uvSize

        for (i in 0 until uvSize) {
            yuvData[uOffset + i] = 128.toByte()
            yuvData[vOffset + i] = 128.toByte()
        }

        return yuvData
    }

    companion object {
        private const val MIME_TYPE = "video/avc" // H.264
        private const val I_FRAME_INTERVAL = 5 // seconds
        private const val TIMEOUT_US = 10000L // 10ms
        private const val MAX_EOS_DRAIN_RETRIES = 50

        /**
         * Calculate appropriate bitrate based on resolution.
         */
        private fun calculateBitrate(width: Int, height: Int): Int {
            // Grayscale video needs less bitrate than color
            val pixels = width * height
            return when {
                pixels <= 480 * 360 -> 500_000      // 360p
                pixels <= 1280 * 720 -> 1_500_000   // 720p
                pixels <= 1920 * 1080 -> 3_000_000  // 1080p
                else -> 5_000_000                    // 4K
            }
        }
    }
}
