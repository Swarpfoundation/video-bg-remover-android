package com.videobgremover.app.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.graphics.withSave
import com.videobgremover.app.core.Logger

/**
 * Replaces the background of a segmented subject with various options:
 * - Solid color
 * - Gradient
 * - Image/photo
 * - Blur effect
 * - Transparent (checkerboard)
 */
class BackgroundReplacer {

    sealed class Background {
        data class Color(val color: Int) : Background()
        data class Gradient(val startColor: Int, val endColor: Int, val orientation: GradientOrientation) : Background()
        data class Image(val bitmap: Bitmap, val scaleMode: ScaleMode = ScaleMode.COVER) : Background()
        data class Blur(val radius: Float = 25f) : Background()
        object Transparent : Background()

        enum class GradientOrientation { VERTICAL, HORIZONTAL, RADIAL }
        enum class ScaleMode { COVER, CONTAIN, FIT, STRETCH }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * Replace background of a subject image.
     *
     * @param subjectBitmap RGBA bitmap with transparent background (alpha from segmentation)
     * @param background Background to composite behind subject
     * @param width Output width
     * @param height Output height
     * @return Composited bitmap with replaced background
     */
    fun replaceBackground(
        subjectBitmap: Bitmap,
        background: Background,
        width: Int = subjectBitmap.width,
        height: Int = subjectBitmap.height
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw background
        drawBackground(canvas, background, width, height)

        // Composite subject on top
        // If subject has alpha, it will blend naturally
        val scaledSubject = if (subjectBitmap.width != width || subjectBitmap.height != height) {
            Bitmap.createScaledBitmap(subjectBitmap, width, height, true)
        } else {
            subjectBitmap
        }

        canvas.drawBitmap(scaledSubject, 0f, 0f, null)

        if (scaledSubject !== subjectBitmap) {
            scaledSubject.recycle()
        }

        return output
    }

    /**
     * Replace background using a mask (for when subject doesn't have alpha).
     */
    fun replaceBackgroundWithMask(
        subjectBitmap: Bitmap,
        mask: FloatArray, // 0.0 = background, 1.0 = subject
        background: Background,
        width: Int = subjectBitmap.width,
        height: Int = subjectBitmap.height
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw background
        drawBackground(canvas, background, width, height)

        // Create mask bitmap
        val maskBitmap = createMaskBitmap(mask, width, height)

        // Create subject bitmap scaled to output size
        val scaledSubject = if (subjectBitmap.width != width || subjectBitmap.height != height) {
            Bitmap.createScaledBitmap(subjectBitmap, width, height, true)
        } else {
            subjectBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        // Apply mask to subject using PorterDuff
        val subjectCanvas = Canvas(scaledSubject)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        subjectCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)

        // Draw masked subject on top of background
        canvas.drawBitmap(scaledSubject, 0f, 0f, null)

        maskBitmap.recycle()
        if (scaledSubject !== subjectBitmap) {
            scaledSubject.recycle()
        }

        return output
    }

    private fun drawBackground(canvas: Canvas, background: Background, width: Int, height: Int) {
        when (background) {
            is Background.Color -> {
                canvas.drawColor(background.color)
            }

            is Background.Gradient -> {
                val shader = when (background.orientation) {
                    Background.GradientOrientation.VERTICAL -> LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        background.startColor, background.endColor,
                        Shader.TileMode.CLAMP
                    )
                    Background.GradientOrientation.HORIZONTAL -> LinearGradient(
                        0f, 0f, width.toFloat(), 0f,
                        background.startColor, background.endColor,
                        Shader.TileMode.CLAMP
                    )
                    Background.GradientOrientation.RADIAL -> android.graphics.RadialGradient(
                        width / 2f, height / 2f,
                        maxOf(width, height) / 2f,
                        background.startColor, background.endColor,
                        Shader.TileMode.CLAMP
                    )
                }
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }

            is Background.Image -> {
                drawImageBackground(canvas, background.bitmap, background.scaleMode, width, height)
            }

            is Background.Blur -> {
                // Blur is handled separately (requires input image to blur)
                // For now, draw a neutral gray
                canvas.drawColor(Color.DKGRAY)
            }

            Background.Transparent -> {
                // Leave transparent (checkerboard will show in preview)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        }
    }

    private fun drawImageBackground(
        canvas: Canvas,
        bitmap: Bitmap,
        scaleMode: Background.ScaleMode,
        width: Int,
        height: Int
    ) {
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = calculateDstRect(bitmap.width, bitmap.height, width, height, scaleMode)

        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    private fun calculateDstRect(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        scaleMode: Background.ScaleMode
    ): Rect {
        return when (scaleMode) {
            Background.ScaleMode.STRETCH -> {
                Rect(0, 0, dstWidth, dstHeight)
            }

            Background.ScaleMode.FIT -> {
                val scale = minOf(
                    dstWidth.toFloat() / srcWidth,
                    dstHeight.toFloat() / srcHeight
                )
                val scaledWidth = (srcWidth * scale).toInt()
                val scaledHeight = (srcHeight * scale).toInt()
                val left = (dstWidth - scaledWidth) / 2
                val top = (dstHeight - scaledHeight) / 2
                Rect(left, top, left + scaledWidth, top + scaledHeight)
            }

            Background.ScaleMode.COVER -> {
                val scale = maxOf(
                    dstWidth.toFloat() / srcWidth,
                    dstHeight.toFloat() / srcHeight
                )
                val scaledWidth = (srcWidth * scale).toInt()
                val scaledHeight = (srcHeight * scale).toInt()
                val left = (dstWidth - scaledWidth) / 2
                val top = (dstHeight - scaledHeight) / 2
                Rect(left, top, left + scaledWidth, top + scaledHeight)
            }

            Background.ScaleMode.CONTAIN -> {
                calculateDstRect(srcWidth, srcHeight, dstWidth, dstHeight, Background.ScaleMode.FIT)
            }
        }
    }

    private fun createMaskBitmap(mask: FloatArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(mask.size) { i ->
            val alpha = (mask[i] * 255).toInt()
            alpha shl 24
        }
        bitmap.setPixels(pixels.map { it or 0xFFFFFF }.toIntArray(), 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Create a blurred background from the original frame.
     */
    fun createBlurredBackground(
        originalFrame: Bitmap,
        mask: FloatArray,
        blurRadius: Float = 25f
    ): Bitmap {
        // Simple box blur implementation
        // For production, use RenderScript or GPU for better performance
        val blurred = applyBoxBlur(originalFrame, blurRadius.toInt())
        return blurred
    }

    private fun applyBoxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        // Very simple box blur - in production use RenderScript
        val output = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Horizontal pass
        val temp = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0

                for (dx in -radius..radius) {
                    val px = (x + dx).coerceIn(0, width - 1)
                    val idx = y * width + px
                    val pixel = pixels[idx]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }

                temp[y * width + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0

                for (dy in -radius..radius) {
                    val py = (y + dy).coerceIn(0, height - 1)
                    val idx = py * width + x
                    val pixel = temp[idx]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }

                pixels[y * width + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    companion object {
        /**
         * Preset backgrounds for quick selection.
         */
        fun getPresetBackgrounds(): List<Pair<String, Background>> {
            return listOf(
                "Transparent" to Background.Transparent,
                "White" to Background.Color(Color.WHITE),
                "Black" to Background.Color(Color.BLACK),
                "Gray" to Background.Color(Color.GRAY),
                "Blue Sky" to Background.Gradient(
                    Color.parseColor("#87CEEB"),
                    Color.parseColor("#E0F6FF"),
                    Background.GradientOrientation.VERTICAL
                ),
                "Sunset" to Background.Gradient(
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#4ECDC4"),
                    Background.GradientOrientation.VERTICAL
                ),
                "Purple Dream" to Background.Gradient(
                    Color.parseColor("#667eea"),
                    Color.parseColor("#764ba2"),
                    Background.GradientOrientation.VERTICAL
                )
            )
        }
    }
}
