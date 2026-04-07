package com.example.paperlessmeeting.ui.screens.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val expandedHistoryMeetingIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val actionInProgress: Boolean = false,
    val error: String? = null,
    val currentActionError: String? = null
)

@HiltViewModel
class LotteryListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager,
    private val appSettingsState: AppSettingsState
) : ViewModel() {

    private val _uiState = MutableStateFlow(LotteryListUiState())
    val uiState: StateFlow<LotteryListUiState> = _uiState.asStateFlow()

    private val _winnerAnnouncement = MutableSharedFlow<WinnerAnnouncementData>(extraBufferCapacity = 1)
    val winnerAnnouncement: SharedFlow<WinnerAnnouncementData> = _winnerAnnouncement.asSharedFlow()

    private var currentUserId: Int? = null
    private var listenersStarted = false
    private var subscribedMeetingId: Int? = null
    private var lastVisibleRefreshAtMs: Long = 0L
    private val visibleRefreshThrottleMs: Long = 2_000L

    init {
        initSocketListener()
        loadData()
    }

    fun loadData() {
        loadData(showLoading = true)
    }

    private fun loadData(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = null)
            }
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
                bindCurrentDisplayMeeting(displayItem?.meeting?.id)

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
                val hasContent = _uiState.value.currentDisplayMeeting != null || _uiState.value.historyLotteries.isNotEmpty()
                _uiState.value = if (!showLoading && hasContent) {
                    _uiState.value.copy(isLoading = false)
                } else {
                    _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadData()
    }

    fun refreshOnVisible() {
        val now = Instant.now().toEpochMilli()
        if (now - lastVisibleRefreshAtMs < visibleRefreshThrottleMs) return
        lastVisibleRefreshAtMs = now
        val hasContent = _uiState.value.currentDisplayMeeting != null || _uiState.value.historyLotteries.isNotEmpty()
        loadData(showLoading = !hasContent)
    }

    fun toggleHistoryMeeting(meetingId: Int) {
        val expanded = _uiState.value.expandedHistoryMeetingIds.toMutableSet()
        if (!expanded.add(meetingId)) {
            expanded.remove(meetingId)
        }
        _uiState.value = _uiState.value.copy(expandedHistoryMeetingIds = expanded)
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
            historyLotteries = mergeSessionIntoHistory(_uiState.value.historyLotteries, session),
            actionInProgress = false,
            currentActionError = null
        )
    }

    private fun initSocketListener() {
        if (listenersStarted) return
        listenersStarted = true
        socketManager.connect(appSettingsState.getSocketBaseUrl())

        viewModelScope.launch {
            socketManager.lotterySessionEvent.collect { session ->
                if (session.meeting_id == _uiState.value.currentDisplayMeeting?.id) {
                    handleRealtimeSessionUpdate(session)
                }
            }
        }

        viewModelScope.launch {
            socketManager.lotteryErrorEvent.collect { errorMessage ->
                _uiState.value = _uiState.value.copy(currentActionError = errorMessage)
            }
        }

        viewModelScope.launch {
            socketManager.meetingChangedEvent.collect {
                loadData(showLoading = false)
            }
        }
    }

    private fun bindCurrentDisplayMeeting(meetingId: Int?) {
        if (meetingId == null) {
            subscribedMeetingId?.let(socketManager::leaveMeeting)
            subscribedMeetingId = null
            return
        }
        socketManager.connect(appSettingsState.getSocketBaseUrl())
        if (subscribedMeetingId != null && subscribedMeetingId != meetingId) {
            socketManager.leaveMeeting(subscribedMeetingId!!)
        }
        if (subscribedMeetingId != meetingId) {
            socketManager.joinMeeting(meetingId)
            subscribedMeetingId = meetingId
        }
    }

    private fun handleRealtimeSessionUpdate(session: LotterySession) {
        val previousSession = _uiState.value.currentDisplaySession
        val mergedSession = session.mergePublicSessionUpdate(resolveCurrentUserId(), previousSession)
        if (shouldAnnounceWinner(previousSession, mergedSession)) {
            val roundLabel = mergedSession.current_round?.roundOrderLabel() ?: "本轮"
            _winnerAnnouncement.tryEmit(
                WinnerAnnouncementData(
                    title = "${roundLabel}中签提醒",
                    message = "你已进入本轮中签名单。"
                )
            )
        }
        updateCurrentDisplaySession(mergedSession)
    }

    private fun shouldAnnounceWinner(previousState: LotterySession?, newState: LotterySession): Boolean {
        if (previousState?.session_status != "rolling") return false
        if (newState.session_status !in setOf("result", "completed")) return false
        val currentUserIdValue = resolveCurrentUserId() ?: return false
        return newState.winners.any { winnerMatchesUser(it, currentUserIdValue) }
    }

    private fun resolveCurrentUserId(): Int? {
        return currentUserId ?: userPreferences.getUserId().takeIf { it > 0 }
    }

    private fun winnerMatchesUser(winner: LotteryWinner, userId: Int): Boolean {
        return winner.user_id == userId || winner.id == userId
    }

    private fun mergeSessionIntoHistory(
        historyList: List<LotteryHistoryResponse>,
        session: LotterySession
    ): List<LotteryHistoryResponse> {
        val meeting = _uiState.value.currentDisplayMeeting
        val finishedRounds = session.finishedRounds()
        if (meeting == null || finishedRounds.isEmpty()) return historyList

        val updatedHistory = LotteryHistoryResponse(
            meeting_id = session.meeting_id,
            meeting_title = meeting.title,
            rounds = finishedRounds
        )
        val withoutCurrent = historyList.filterNot { it.meeting_id == session.meeting_id }
        return listOf(updatedHistory) + withoutCurrent
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

    override fun onCleared() {
        super.onCleared()
        subscribedMeetingId?.let(socketManager::leaveMeeting)
    }
}
