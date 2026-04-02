package com.example.paperlessmeeting.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.example.paperlessmeeting.MainDispatcherRule
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.MeetingChangedData
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.remote.VoteStartData
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.utils.Resource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MeetingRepository>()
    private val socketManager = mockk<SocketManager>()
    private val userPreferences = mockk<UserPreferences>()
    private val appSettingsState = mockk<AppSettingsState>()

    private val voteStartEvent = MutableSharedFlow<VoteStartData>()
    private val voteStateChangeEvent = MutableSharedFlow<Vote>()
    private val voteUpdateEvent = MutableSharedFlow<com.example.paperlessmeeting.data.remote.VoteUpdateData>()
    private val voteEndEvent = MutableSharedFlow<com.example.paperlessmeeting.data.remote.VoteEndData>()
    private val meetingChangedEvent = MutableSharedFlow<MeetingChangedData>()
    private val connectionState = MutableSharedFlow<Boolean>()

    @Test
    fun `静默刷新被取消时不发送失败提示且保留当前详情`() = runTest {
        val meeting = testMeeting(id = 7)
        val actionMessages = mutableListOf<String>()

        stubCommonDependencies()
        coEvery { repository.getMeetingById(7, 11) } returns Resource.Success(meeting) andThenThrows
            CancellationException("the coroutine scope left composition")

        val viewModel = DetailViewModel(
            repository = repository,
            socketManager = socketManager,
            userPreferences = userPreferences,
            appSettingsState = appSettingsState,
            savedStateHandle = SavedStateHandle(mapOf("meetingId" to "7"))
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.actionMessage.collect { actionMessages.add(it) }
        }

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is DetailUiState.Success)

        meetingChangedEvent.emit(MeetingChangedData(action = "updated", meeting_id = 7))
        advanceUntilIdle()

        assertTrue(actionMessages.isEmpty())
        assertTrue(viewModel.uiState.value is DetailUiState.Success)
        assertEquals(7, (viewModel.uiState.value as DetailUiState.Success).meeting.id)
        assertFalse(viewModel.isRefreshing.value)
    }

    private fun stubCommonDependencies() {
        every { userPreferences.getUserId() } returns 11
        every { appSettingsState.getSocketBaseUrl() } returns "https://example.com"
        every { appSettingsState.getStaticBaseUrl() } returns "https://example.com/static/"

        every { socketManager.voteStartEvent } returns voteStartEvent
        every { socketManager.voteStateChangeEvent } returns voteStateChangeEvent
        every { socketManager.voteUpdateEvent } returns voteUpdateEvent
        every { socketManager.voteEndEvent } returns voteEndEvent
        every { socketManager.meetingChangedEvent } returns meetingChangedEvent
        every { socketManager.connectionState } returns connectionState
        every { socketManager.connect(any()) } just runs
        every { socketManager.joinMeeting(any()) } just runs

        coEvery { repository.getActiveVote(7) } returns null
    }

    private fun testMeeting(id: Int): Meeting {
        return Meeting(
            id = id,
            title = "Weekly Review",
            meetingTypeId = 1,
            startTime = "2026-03-27 09:00:00",
            endTime = "2026-03-27 10:00:00",
            location = "A100",
            host = "Host"
        )
    }
}
