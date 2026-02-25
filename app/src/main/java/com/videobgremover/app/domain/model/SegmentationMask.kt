package com.videobgremover.app.domain.model

/**
 * Confidence mask returned by the segmentation model.
 */
data class SegmentationMask(
    val values: FloatArray,
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "Mask width must be > 0" }
        require(height > 0) { "Mask height must be > 0" }
        require(values.size >= width * height) {
            "Mask values size (${values.size}) is smaller than width*height (${width * height})"
        }
    }
}
