package com.example.paperlessmeeting.ui.screens.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LotteryMeetingCardItem(
    val meeting: Meeting,
    val session: LotterySession? = null
)

data class LotteryListUiState(
    val currentDisplayMeeting: Meeting? = null,
    val currentDisplaySession: LotterySession? = null,
    val currentDisplayResultRound: LotteryRound? = null,
    val historyLotteries: List<LotteryHistoryResponse> = emptyList(),
    val isLoading: Boolean = false,
    val actionInProgress: Boolean = false,
    val error: String? = null,
    val currentActionError: String? = null
)

@HiltViewModel
class LotteryListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LotteryListUiState())
    val uiState: StateFlow<LotteryListUiState> = _uiState.asStateFlow()

    private var currentUserId: Int? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val todayStr = java.time.LocalDate.now().toString()
                currentUserId = userPreferences.getUserId().takeIf { it > 0 }

                val todayMeetings = repository.getMeetings(
                    limit = 20,
                    startDate = todayStr,
                    endDate = todayStr
                )

                val todayItems = loadSessionsForMeetings(todayMeetings, currentUserId)

                val recentMeetings = repository.getMeetings(
                    limit = 20,
                    sort = "desc"
                ).distinctBy { it.id }

                val history = loadHistoryForMeetings(recentMeetings)
                val displayItem = selectCurrentDisplayMeeting(todayItems)
                val displayHistory = history.firstOrNull { it.meeting_id == displayItem?.meeting?.id }

                _uiState.value = _uiState.value.copy(
                    currentDisplayMeeting = displayItem?.meeting,
                    currentDisplaySession = displayItem?.session,
                    currentDisplayResultRound = resolveCurrentDisplayResultRound(displayItem?.session, displayHistory),
                    historyLotteries = history,
                    isLoading = false,
                    currentActionError = null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun refresh() {
        loadData()
    }

    fun joinCurrentDisplayLottery() {
        val meetingId = _uiState.value.currentDisplayMeeting?.id ?: return
        val userId = currentUserId ?: userPreferences.getUserId().takeIf { it > 0 } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = true, currentActionError = null)
            val session = repository.joinLotteryPool(meetingId, userId)
            if (session == null) {
                _uiState.value = _uiState.value.copy(
                    actionInProgress = false,
                    currentActionError = "加入抽签池失败"
                )
            } else {
                updateCurrentDisplaySession(session)
            }
        }
    }

    fun quitCurrentDisplayLottery() {
        val meetingId = _uiState.value.currentDisplayMeeting?.id ?: return
        val userId = currentUserId ?: userPreferences.getUserId().takeIf { it > 0 } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = true, currentActionError = null)
            val session = repository.quitLotteryPool(meetingId, userId)
            if (session == null) {
                _uiState.value = _uiState.value.copy(
                    actionInProgress = false,
                    currentActionError = "退出抽签池失败"
                )
            } else {
                updateCurrentDisplaySession(session)
            }
        }
    }

    private fun updateCurrentDisplaySession(session: LotterySession) {
        val history = _uiState.value.historyLotteries.firstOrNull { it.meeting_id == session.meeting_id }
        _uiState.value = _uiState.value.copy(
            currentDisplaySession = session,
            currentDisplayResultRound = resolveCurrentDisplayResultRound(session, history),
            actionInProgress = false,
            currentActionError = null
        )
    }

    private suspend fun loadSessionsForMeetings(
        meetings: List<Meeting>,
        userId: Int?
    ): List<LotteryMeetingCardItem> = coroutineScope {
        meetings.map { meeting ->
            async {
                val session = try {
                    repository.getLotterySession(meeting.id, userId)
                } catch (_: Exception) {
                    null
                }
                LotteryMeetingCardItem(meeting = meeting, session = session)
            }
        }.awaitAll()
    }

    private suspend fun loadHistoryForMeetings(meetings: List<Meeting>): List<LotteryHistoryResponse> = coroutineScope {
        meetings.map { meeting ->
            async {
                repository.getLotteryHistory(meeting.id)?.takeIf { history ->
                    history.rounds.any { round -> round.status == "finished" || round.winners.isNotEmpty() }
                }?.let { history ->
                    history.copy(
                        rounds = history.rounds.sortedWith(
                            compareBy<LotteryRound> { if (it.sort_order > 0) 0 else 1 }
                                .thenBy { it.sort_order }
                                .thenBy { it.id }
                        )
                    )
                }
            }
        }.awaitAll().filterNotNull()
    }
}
