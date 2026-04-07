package com.example.paperlessmeeting.ui.screens.lottery

import com.example.paperlessmeeting.MainDispatcherRule
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.MeetingChangedData
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.Meeting
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LotteryListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MeetingRepository>()
    private val userPreferences = mockk<UserPreferences>()
    private val socketManager = mockk<SocketManager>()
    private val appSettingsState = mockk<AppSettingsState>()

    private val lotterySessionEvent = MutableSharedFlow<LotterySession>()
    private val lotteryErrorEvent = MutableSharedFlow<String>()
    private val meetingChangedEvent = MutableSharedFlow<MeetingChangedData>()

    @Test
    fun `meeting_changed 后会重载当前展示会议并绑定新房间`() = runTest {
        val today = java.time.LocalDate.now().toString()

        every { userPreferences.getUserId() } returns 11
        every { appSettingsState.getSocketBaseUrl() } returns "https://example.com"
        every { socketManager.connect(any()) } just runs
        every { socketManager.joinMeeting(any()) } just runs
        every { socketManager.leaveMeeting(any()) } just runs
        every { socketManager.lotterySessionEvent } returns lotterySessionEvent
        every { socketManager.lotteryErrorEvent } returns lotteryErrorEvent
        every { socketManager.meetingChangedEvent } returns meetingChangedEvent

        coEvery {
            repository.getMeetings(
                skip = 0,
                limit = 20,
                sort = "desc",
                startDate = today,
                endDate = today,
                userId = null
            )
        } returns listOf(testMeeting(1, "2026-04-07 09:00:00")) andThen listOf(
            testMeeting(1, "2026-04-07 09:00:00"),
            testMeeting(2, "2026-04-07 10:00:00")
        )
        coEvery {
            repository.getMeetings(
                skip = 0,
                limit = 20,
                sort = "desc",
                startDate = null,
                endDate = null,
                userId = null
            )
        } returns listOf(testMeeting(1, "2026-04-07 09:00:00")) andThen listOf(
            testMeeting(1, "2026-04-07 09:00:00"),
            testMeeting(2, "2026-04-07 10:00:00")
        )
        coEvery { repository.getLotterySession(1, 11) } returns testSession(1, "idle", null)
        coEvery {
            repository.getLotterySession(2, 11)
        } returns testSession(
            2,
            "collecting",
            LotteryRound(id = 202, title = "第二轮", count = 1, sort_order = 2, status = "draft")
        )
        coEvery { repository.getLotteryHistory(1) } returns null
        coEvery { repository.getLotteryHistory(2) } returns null

        val viewModel = LotteryListViewModel(
            repository = repository,
            userPreferences = userPreferences,
            socketManager = socketManager,
            appSettingsState = appSettingsState
        )

        advanceUntilIdle()

        meetingChangedEvent.emit(MeetingChangedData(action = "created", meeting_id = 2))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.currentDisplayMeeting?.id)
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

    private fun testSession(
        meetingId: Int,
        status: String,
        currentRound: LotteryRound?
    ): LotterySession {
        return LotterySession(
            meeting_id = meetingId,
            session_status = status,
            current_round_id = currentRound?.id,
            current_round = currentRound,
            rounds = listOfNotNull(currentRound)
        )
    }
}
