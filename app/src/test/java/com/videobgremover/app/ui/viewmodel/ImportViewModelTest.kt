package com.videobgremover.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.videobgremover.app.data.extractor.VideoMetadataExtractor
import com.videobgremover.app.domain.model.VideoMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var extractor: VideoMetadataExtractor
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        extractor = mockk(relaxed = true)
        viewModel = ImportViewModel(extractor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.uiState.first()

        assertFalse(state.isLoading)
        assertNull(state.videoMetadata)
        assertNull(state.thumbnail)
        assertNull(state.error)
        assertFalse(state.hasPermission)
    }

    @Test
    fun `onPermissionResult updates permission state`() = runTest {
        viewModel.onPermissionResult(true)

        val state = viewModel.uiState.first()
        assertTrue(state.hasPermission)
    }

    @Test
    fun `onVideoSelected with invalid video sets error`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { extractor.isValidVideo(uri) } returns false

        viewModel.onVideoSelected(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("Selected file is not a valid video", state.error)
    }

    @Test
    fun `onVideoSelected with valid video extracts metadata`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        val metadata = VideoMetadata(
            uri = "test",
            name = "test.mp4",
            durationMs = 10000,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            codec = "h264"
        )

        coEvery { extractor.isValidVideo(uri) } returns true
        coEvery { extractor.extract(uri) } returns Result.success(metadata)
        coEvery {
            extractor.extractThumbnail(uri, width = any(), height = any())
        } returns Result.failure(Exception("No thumbnail"))

        viewModel.onVideoSelected(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertFalse(state.isLoading)
        assertNotNull(state.videoMetadata)
        assertEquals(metadata, state.videoMetadata)

        coVerify { extractor.extract(uri) }
    }

    @Test
    fun `clearError removes error from state`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { extractor.isValidVideo(uri) } returns false

        viewModel.onVideoSelected(uri)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.first().error)

        viewModel.clearError()

        assertNull(viewModel.uiState.first().error)
    }

    @Test
    fun `clearSelection resets state but keeps permission`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        val metadata = VideoMetadata(
            uri = "test",
            name = "test.mp4",
            durationMs = 10000,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            codec = "h264"
        )

        coEvery { extractor.isValidVideo(uri) } returns true
        coEvery { extractor.extract(uri) } returns Result.success(metadata)
        coEvery {
            extractor.extractThumbnail(uri, width = any(), height = any())
        } returns Result.failure(Exception("No thumbnail"))

        viewModel.onPermissionResult(true)
        viewModel.onVideoSelected(uri)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.first().videoMetadata)
        assertTrue(viewModel.uiState.first().hasPermission)

        viewModel.clearSelection()

        val state = viewModel.uiState.first()
        assertNull(state.videoMetadata)
        assertNull(state.thumbnail)
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertTrue(state.hasPermission) // Permission should be preserved
    }
}
