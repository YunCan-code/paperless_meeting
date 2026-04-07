package com.example.paperlessmeeting.ui.screens.vote

import com.example.paperlessmeeting.MainDispatcherRule
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.MeetingChangedData
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.data.repository.MeetingRepository
import io.mockk.coEvery
import io.mockk.verify
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
class VoteListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MeetingRepository>()
    private val userPreferences = mockk<UserPreferences>()
    private val socketManager = mockk<SocketManager>()
    private val appSettingsState = mockk<AppSettingsState>()

    private val voteStateChangeEvent = MutableSharedFlow<Vote>()
    private val meetingChangedEvent = MutableSharedFlow<MeetingChangedData>()
    private val connectionState = MutableSharedFlow<Boolean>()

    @Test
    fun `meeting_changed 后会重载今日会议并订阅新会议房间`() = runTest {
        val today = java.time.LocalDate.now().toString()

        every { userPreferences.getUserId() } returns 11
        every { appSettingsState.getSocketBaseUrl() } returns "https://example.com"
        every { socketManager.voteStateChangeEvent } returns voteStateChangeEvent
        every { socketManager.meetingChangedEvent } returns meetingChangedEvent
        every { socketManager.connectionState } returns connectionState
        every { socketManager.connect(any()) } just runs
        every { socketManager.joinMeeting(any()) } just runs

        coEvery { repository.getVoteHistory(11, 0, 50) } returns emptyList()
        coEvery {
            repository.getMeetings(
                skip = 0,
                limit = 50,
                sort = "desc",
                startDate = today,
                endDate = today,
                userId = 11
            )
        } returns listOf(testMeeting(1, "2026-04-07 09:00:00")) andThen listOf(
            testMeeting(1, "2026-04-07 09:00:00"),
            testMeeting(2, "2026-04-07 10:00:00")
        )
        coEvery { repository.getVoteList(1) } returns emptyList()
        coEvery { repository.getVoteList(2) } returns listOf(testVote(20, 2))
        coEvery { repository.getVote(20, 11) } returns testVote(20, 2)

        val viewModel = VoteListViewModel(
            repository = repository,
            userPreferences = userPreferences,
            socketManager = socketManager,
            appSettingsState = appSettingsState
        )

        advanceUntilIdle()

        meetingChangedEvent.emit(MeetingChangedData(action = "created", meeting_id = 2))
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.currentDisplayVote?.id)
        verify(atLeast = 1) { socketManager.joinMeeting(2) }
    }

    private fun testMeeting(id: Int, startTime: String): Meeting {
        return Meeting(
            id = id,
            title = "会议$id",
            meetingTypeId = 1,
            startTime = startTime,
            endTime = "2026-04-07 11:00:00",
            location = "A$id",
            host = null
        )
    }

    private fun testVote(id: Int, meetingId: Int): Vote {
        return Vote(
            id = id,
            meeting_id = meetingId,
            title = "投票$id",
            description = "说明",
            is_multiple = false,
            is_anonymous = false,
            max_selections = 1,
            duration_seconds = 60,
            status = "draft",
            started_at = "2026-04-07T09:00:00",
            created_at = "2026-04-07T08:50:00",
            options = emptyList(),
            remaining_seconds = 0,
            wait_seconds = 0,
            selected_option_ids = emptyList(),
            user_voted = false
        )
    }
}
