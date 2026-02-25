package com.videobgremover.app.core.processing

import com.videobgremover.app.core.Logger
import java.util.LinkedList

/**
 * Removes speckles, spots, and stains from segmentation masks.
 *
 * Common artifacts in video segmentation:
 * - Isolated white spots in background (false positives)
 * - Isolated black holes in subject (false negatives)
 * - Flickering pixels due to noise
 * - Small disconnected regions
 *
 * This processor uses connected component analysis to:
 * 1. Remove small isolated foreground spots (noise)
 * 2. Fill small holes inside the subject
 * 3. Keep only the largest connected component (main subject)
 */
class SpeckleRemover(
    private val minComponentSize: Int = 50,      // Minimum pixels to keep a component
    private val maxHoleSize: Int = 100,          // Maximum hole size to fill
    private val removeIslands: Boolean = true,   // Remove isolated spots
    private val fillHoles: Boolean = true        // Fill holes in subject
) {

    /**
     * Process mask to remove speckles and spots.
     *
     * @param mask Binary mask (0.0 or 1.0 values)
     * @param width Mask width
     * @param height Mask height
     * @return Cleaned mask
     */
    fun process(mask: FloatArray, width: Int, height: Int): FloatArray {
        if (!removeIslands && !fillHoles) {
            return mask
        }

        var result = mask.copyOf()

        // Step 1: Remove small isolated foreground components (spots in background)
        if (removeIslands) {
            result = removeSmallComponents(result, width, height, isForeground = true)
        }

        // Step 2: Fill small holes inside subject (black spots in white area)
        if (fillHoles) {
            result = fillSmallHoles(result, width, height)
        }

        // Step 3: Optional - keep only largest component (main subject)
        result = keepLargestComponent(result, width, height)

        return result
    }

    /**
     * Remove small connected components.
     */
    private fun removeSmallComponents(
        mask: FloatArray,
        width: Int,
        height: Int,
        isForeground: Boolean
    ): FloatArray {
        val visited = BooleanArray(mask.size)
        val result = mask.copyOf()
        val targetValue = if (isForeground) 1.0f else 0.0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!visited[idx] && mask[idx] == targetValue) {
                    val component = floodFill(mask, visited, x, y, width, height, targetValue)

                    // If component is too small, remove it
                    if (component.size < minComponentSize) {
                        component.forEach { (cx, cy) ->
                            result[cy * width + cx] = if (isForeground) 0.0f else 1.0f
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Fill small holes inside the subject.
     */
    private fun fillSmallHoles(mask: FloatArray, width: Int, height: Int): FloatArray {
        // Invert mask to find holes (background components inside subject)
        val inverted = FloatArray(mask.size) { i ->
            if (mask[i] == 1.0f) 0.0f else 1.0f
        }

        val visited = BooleanArray(mask.size)
        val result = mask.copyOf()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!visited[idx] && inverted[idx] == 1.0f) {
                    val component = floodFill(inverted, visited, x, y, width, height, 1.0f)

                    // Check if this is a hole (not connected to image border)
                    val isHole = !isConnectedToBorder(component, width, height)

                    // If it's a small hole, fill it
                    if (isHole && component.size < maxHoleSize) {
                        component.forEach { (cx, cy) ->
                            result[cy * width + cx] = 1.0f
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Keep only the largest connected component.
     */
    private fun keepLargestComponent(mask: FloatArray, width: Int, height: Int): FloatArray {
        val visited = BooleanArray(mask.size)
        var largestComponent: List<Pair<Int, Int>> = emptyList()
        var largestSize = 0

        // Find all foreground components
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!visited[idx] && mask[idx] == 1.0f) {
                    val component = floodFill(mask, visited, x, y, width, height, 1.0f)

                    if (component.size > largestSize) {
                        largestSize = component.size
                        largestComponent = component
                    }
                }
            }
        }

        // If no components found or largest is too small, return original
        if (largestSize < minComponentSize) {
            return mask
        }

        // Create new mask with only the largest component
        val result = FloatArray(mask.size) { 0.0f }
        largestComponent.forEach { (x, y) ->
            result[y * width + x] = 1.0f
        }

        return result
    }

    /**
     * Flood fill to find connected component.
     */
    private fun floodFill(
        mask: FloatArray,
        visited: BooleanArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        targetValue: Float
    ): List<Pair<Int, Int>> {
        val component = mutableListOf<Pair<Int, Int>>()
        val queue = LinkedList<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited[startY * width + startX] = true

        val directions = listOf(
            0 to -1,  // Up
            0 to 1,   // Down
            -1 to 0,  // Left
            1 to 0,   // Right
            -1 to -1, // Diagonal up-left
            1 to -1,  // Diagonal up-right
            -1 to 1,  // Diagonal down-left
            1 to 1    // Diagonal down-right
        )

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            component.add(x to y)

            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                val nIdx = ny * width + nx

                if (nx in 0 until width &&
                    ny in 0 until height &&
                    !visited[nIdx] &&
                    mask[nIdx] == targetValue
                ) {
                    visited[nIdx] = true
                    queue.add(nx to ny)
                }
            }
        }

        return component
    }

    /**
     * Check if component is connected to image border.
     */
    private fun isConnectedToBorder(
        component: List<Pair<Int, Int>>,
        width: Int,
        height: Int
    ): Boolean {
        return component.any { (x, y) ->
            x == 0 || x == width - 1 || y == 0 || y == height - 1
        }
    }

    /**
     * Quick noise reduction using median filter on small windows.
     * Faster than connected components for minor noise.
     */
    fun applyMedianFilter(mask: FloatArray, width: Int, height: Int, radius: Int = 1): FloatArray {
        val result = mask.copyOf()

        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                val idx = y * width + x

                // Collect neighbors
                val neighbors = mutableListOf<Float>()
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        neighbors.add(mask[(y + dy) * width + (x + dx)])
                    }
                }

                // Take median
                neighbors.sort()
                result[idx] = neighbors[neighbors.size / 2]
            }
        }

        return result
    }

    companion object {
        /**
         * Preset configurations for different noise levels.
         */
        fun mild(): SpeckleRemover = SpeckleRemover(
            minComponentSize = 25,
            maxHoleSize = 50,
            removeIslands = true,
            fillHoles = true
        )

        fun aggressive(): SpeckleRemover = SpeckleRemover(
            minComponentSize = 100,
            maxHoleSize = 200,
            removeIslands = true,
            fillHoles = true
        )

        fun minimal(): SpeckleRemover = SpeckleRemover(
            minComponentSize = 10,
            maxHoleSize = 25,
            removeIslands = true,
            fillHoles = false
        )
    }
}
