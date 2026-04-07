package com.example.paperlessmeeting.ui.screens.media

import com.example.paperlessmeeting.MainDispatcherRule
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.data.remote.MediaChangedData
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.domain.model.MediaItem
import com.example.paperlessmeeting.domain.model.MediaItemPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = mockk<ApiService>()
    private val appSettingsState = mockk<AppSettingsState>()
    private val socketManager = mockk<SocketManager>()

    private val mediaChangedEvent = MutableSharedFlow<MediaChangedData>()

    @Test
    fun `收到 media_changed 后会刷新当前目录`() = runTest {
        every { appSettingsState.getSocketBaseUrl() } returns "https://example.com"
        every { socketManager.connect(any()) } just runs
        every { socketManager.mediaChangedEvent } returns mediaChangedEvent

        coEvery {
            api.getMediaItems(
                parentId = null,
                kind = null,
                visibleOnAndroid = true,
                skip = 0,
                limit = 40
            )
        } returns MediaItemPage(
            items = listOf(testMediaItem(1)),
            total = 1,
            skip = 0,
            limit = 40
        ) andThen MediaItemPage(
            items = listOf(testMediaItem(1), testMediaItem(2)),
            total = 2,
            skip = 0,
            limit = 40
        )

        val viewModel = MediaViewModel(
            api = api,
            appSettingsState = appSettingsState,
            socketManager = socketManager
        )

        advanceUntilIdle()

        mediaChangedEvent.emit(
            MediaChangedData(
                action = "created",
                item_id = 2,
                parent_id = null,
                previous_parent_id = null,
                kind = "image",
                visible_on_android = true
            )
        )
        advanceUntilIdle()

        coVerify(exactly = 2) {
            api.getMediaItems(
                parentId = null,
                kind = null,
                visibleOnAndroid = true,
                skip = 0,
                limit = 40
            )
        }
        assertEquals(2, viewModel.uiState.value.items.size)
    }

    private fun testMediaItem(id: Int): MediaItem {
        return MediaItem(
            id = id,
            kind = "image",
            title = "图片$id",
            parentId = null,
            extension = "JPG",
            visibleOnAndroid = true
        )
    }
}
