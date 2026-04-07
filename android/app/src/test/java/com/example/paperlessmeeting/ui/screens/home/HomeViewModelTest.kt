package com.example.paperlessmeeting.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import com.example.paperlessmeeting.MainDispatcherRule
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.MeetingChangedData
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MeetingRepository>()
    private val appSettingsState = mockk<AppSettingsState>()
    private val userPreferences = mockk<UserPreferences>()
    private val socketManager = mockk<SocketManager>()

    private val meetingChangedEvent = MutableSharedFlow<MeetingChangedData>()

    @Test
    fun `meeting_changed 后会刷新列表并发出详情刷新信号`() = runTest {
        val refreshSignals = mutableListOf<Unit>()

        every { appSettingsState.getSocketBaseUrl() } returns "https://example.com"
        every { userPreferences.getUserId() } returns 11
        every { socketManager.connect(any()) } just runs
        every { socketManager.meetingChangedEvent } returns meetingChangedEvent

        coEvery {
            repository.getMeetings(
                skip = any(),
                limit = any(),
                sort = any(),
                startDate = any(),
                endDate = any(),
                userId = 11
            )
        } returns listOf(testMeeting(7)) andThen listOf(testMeeting(7), testMeeting(8))

        val viewModel = HomeViewModel(
            repository = repository,
            appSettingsState = appSettingsState,
            userPreferences = userPreferences,
            socketManager = socketManager,
            savedStateHandle = SavedStateHandle()
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.detailRefreshSignal.collect { refreshSignals += it }
        }

        viewModel.selectMeeting(7)
        advanceUntilIdle()

        meetingChangedEvent.emit(MeetingChangedData(action = "created", meeting_id = 8))
        advanceUntilIdle()

        coVerify(exactly = 2) {
            repository.getMeetings(
                skip = 0,
                limit = 20,
                sort = "desc",
                startDate = null,
                endDate = null,
                userId = 11
            )
        }
        assertEquals(2, refreshSignals.size)
    }

    private fun testMeeting(id: Int): Meeting {
        return Meeting(
            id = id,
            title = "会议$id",
            meetingTypeId = 1,
            startTime = "2026-04-07 09:00:00",
            endTime = "2026-04-07 10:00:00",
            location = "A$id",
            host = null
        )
    }
}
