package com.videobgremover.app.core.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.videobgremover.app.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tracks subjects (people) across video frames using face detection.
 *
 * Features:
 * - Detects all faces in frame
 * - Identifies the primary subject (closest to center, largest, or user-selected)
 * - Maintains subject ID across frames
 * - Provides subject bounding box for focused processing
 */
class SubjectTracker(private val context: Context) {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    // Tracking state
    private var primarySubjectId: Int? = null
    private var lastPrimaryFace: FaceData? = null
    private var frameCounter = 0
    private var subjectLostFrames = 0

    data class FaceData(
        val id: Int,  // Internal tracking ID
        val boundingBox: Rect,
        val confidence: Float,
        val centerX: Float,
        val centerY: Float,
        val area: Int
    )

    data class TrackingResult(
        val primarySubject: FaceData?,
        val allSubjects: List<FaceData>,
        val subjectChanged: Boolean,
        val isPrimarySubjectLost: Boolean
    )

    /**
     * Process a frame and track subjects.
     *
     * @param bitmap Current frame
     * @param width Frame width
     * @param height Frame height
     * @param forceReselect Force re-selection of primary subject
     * @return Tracking result with primary subject info
     */
    suspend fun track(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        forceReselect: Boolean = false
    ): TrackingResult = withContext(Dispatchers.Default) {
        try {
            frameCounter++

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(inputImage).await()

            if (faces.isEmpty()) {
                subjectLostFrames++
                return@withContext TrackingResult(
                    primarySubject = lastPrimaryFace,
                    allSubjects = emptyList(),
                    subjectChanged = false,
                    isPrimarySubjectLost = subjectLostFrames > 10
                )
            }

            // Convert faces to our data class
            val faceDataList = faces.mapIndexed { index, face ->
                FaceData(
                    id = index,
                    boundingBox = face.boundingBox,
                    confidence = face.trackingId?.toFloat() ?: 0f,
                    centerX = (face.boundingBox.left + face.boundingBox.right) / 2f,
                    centerY = (face.boundingBox.top + face.boundingBox.bottom) / 2f,
                    area = face.boundingBox.width() * face.boundingBox.height()
                )
            }

            // Select or update primary subject
            val currentPrimary = if (forceReselect || primarySubjectId == null) {
                selectPrimarySubject(faceDataList, width, height)
            } else {
                trackExistingSubject(faceDataList)
            }

            val subjectChanged = currentPrimary?.id != lastPrimaryFace?.id
            primarySubjectId = currentPrimary?.id
            lastPrimaryFace = currentPrimary
            subjectLostFrames = 0

            TrackingResult(
                primarySubject = currentPrimary,
                allSubjects = faceDataList,
                subjectChanged = subjectChanged,
                isPrimarySubjectLost = false
            )

        } catch (e: Exception) {
            Logger.e("Subject tracking failed", e)
            TrackingResult(
                primarySubject = lastPrimaryFace,
                allSubjects = emptyList(),
                subjectChanged = false,
                isPrimarySubjectLost = false
            )
        }
    }

    /**
     * Select the primary subject from detected faces.
     * Prioritizes: center position > size > confidence
     */
    private fun selectPrimarySubject(
        faces: List<FaceData>,
        frameWidth: Int,
        frameHeight: Int
    ): FaceData? {
        if (faces.isEmpty()) return null
        if (faces.size == 1) return faces.first()

        val centerX = frameWidth / 2f
        val centerY = frameHeight / 2f

        // Score each face based on:
        // - Distance from center (closer is better)
        // - Size (larger is better)
        val scoredFaces = faces.map { face ->
            val distanceFromCenter = hypot(
                face.centerX - centerX,
                face.centerY - centerY
            )
            val maxDistance = hypot(frameWidth.toFloat(), frameHeight.toFloat())
            val normalizedDistance = distanceFromCenter / maxDistance

            val maxArea = frameWidth * frameHeight
            val normalizedSize = face.area.toFloat() / maxArea

            // Score: prefer centered, then large
            val score = (1 - normalizedDistance) * 0.6f + normalizedSize * 0.4f

            face to score
        }

        return scoredFaces.maxByOrNull { it.second }?.first
    }

    /**
     * Track existing primary subject across frames.
     * Uses position proximity to maintain identity.
     */
    private fun trackExistingSubject(faces: List<FaceData>): FaceData? {
        val lastFace = lastPrimaryFace ?: return selectPrimarySubject(faces, 0, 0)

        // Find closest face to previous position
        val closestFace = faces.minByOrNull { face ->
            val dx = face.centerX - lastFace.centerX
            val dy = face.centerY - lastFace.centerY
            dx * dx + dy * dy
        }

        // Only switch if the closest face is reasonably close
        return if (closestFace != null) {
            val distance = hypot(
                closestFace.centerX - lastFace.centerX,
                closestFace.centerY - lastFace.centerY
            )
            // Allow switch if within 30% of frame size
            if (distance < 0.3f * 1920) { // Assuming 1080p, adjust as needed
                closestFace
            } else {
                lastFace
            }
        } else {
            lastFace
        }
    }

    /**
     * Get bounding box for primary subject with padding.
     */
    fun getSubjectBounds(
        primarySubject: FaceData?,
        frameWidth: Int,
        frameHeight: Int,
        paddingPercent: Float = 0.3f
    ): Rect? {
        primarySubject ?: return null

        val box = primarySubject.boundingBox
        val paddingX = (box.width() * paddingPercent).toInt()
        val paddingY = (box.height() * paddingPercent).toInt()

        return Rect(
            maxOf(0, box.left - paddingX),
            maxOf(0, box.top - (paddingY * 2)), // More padding above for head
            minOf(frameWidth, box.right + paddingX),
            minOf(frameHeight, box.bottom + paddingY)
        )
    }

    /**
     * Check if a point (x, y) is within the primary subject.
     */
    fun isPointInSubject(
        x: Float,
        y: Float,
        primarySubject: FaceData?,
        tolerance: Float = 1.5f
    ): Boolean {
        primarySubject ?: return true // If no tracking, assume all is subject

        val box = primarySubject.boundingBox
        val expandedBox = Rect(
            (box.left * tolerance).toInt(),
            (box.top * tolerance).toInt(),
            (box.right * tolerance).toInt(),
            (box.bottom * tolerance).toInt()
        )

        return expandedBox.contains(x.toInt(), y.toInt())
    }

    /**
     * Reset tracking state.
     */
    fun reset() {
        primarySubjectId = null
        lastPrimaryFace = null
        frameCounter = 0
        subjectLostFrames = 0
    }

    /**
     * Release resources.
     */
    fun close() {
        faceDetector.close()
    }
}
