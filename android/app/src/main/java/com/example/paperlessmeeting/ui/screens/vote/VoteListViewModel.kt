package com.example.paperlessmeeting.ui.screens.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class VoteListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager,
    private val appSettingsState: AppSettingsState
) : ViewModel() {

    data class VoteListUiState(
        val isLoading: Boolean = false,
        val currentDisplayVote: Vote? = null,
        val myVoteHistory: List<Vote> = emptyList(),
        val expandedHistoryVoteId: Int? = null,
        val historyVoteResults: Map<Int, VoteResult> = emptyMap(),
        val loadingHistoryResultIds: Set<Int> = emptySet(),
        val historyResultErrors: Map<Int, String> = emptyMap(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(VoteListUiState())
    val uiState = _uiState.asStateFlow()

    private var currentVoteTimerJob: Job? = null
    private var trackedMeetingIds: Set<Int> = emptySet()

    init {
        loadData()
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.voteStateChangeEvent.collect {
                loadData()
            }
        }

        viewModelScope.launch {
            socketManager.connectionState.collect { connected ->
                if (connected) {
                    trackedMeetingIds.forEach(socketManager::joinMeeting)
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = userPreferences.getUserId()
                if (userId <= 0) {
                    stopCurrentVoteTimer()
                    trackedMeetingIds = emptySet()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentDisplayVote = null,
                            myVoteHistory = emptyList(),
                            expandedHistoryVoteId = null,
                            historyVoteResults = emptyMap(),
                            loadingHistoryResultIds = emptySet(),
                            historyResultErrors = emptyMap(),
                            error = "用户未登录"
                        )
                    }
                    return@launch
                }

                val myVoteHistory = repository.getVoteHistory(userId, 0, 50)
                    .sortedByDescending { parseVoteDateTime(it.started_at ?: it.created_at) ?: LocalDateTime.MIN }
                val votedIds = myVoteHistory.map { it.id }.toSet()
                val todayVotes = loadTodayVotes(userId).map { vote ->
                    if (vote.id in votedIds) vote.copy(user_voted = true) else vote
                }
                val historyIds = myVoteHistory.map { it.id }.toSet()
                val preservedExpandedId = _uiState.value.expandedHistoryVoteId?.takeIf { it in historyIds }
                val currentDisplayVote = resolveCurrentDisplayVote(todayVotes)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentDisplayVote = currentDisplayVote,
                        myVoteHistory = myVoteHistory,
                        expandedHistoryVoteId = preservedExpandedId,
                        historyVoteResults = it.historyVoteResults.filterKeys(historyIds::contains),
                        loadingHistoryResultIds = it.loadingHistoryResultIds.filterTo(linkedSetOf(), historyIds::contains),
                        historyResultErrors = it.historyResultErrors.filterKeys(historyIds::contains),
                        error = null
                    )
                }

                startCurrentVoteTimer(currentDisplayVote)

                preservedExpandedId?.let { voteId ->
                    fetchHistoryVoteResult(voteId, forceRefresh = true)
                }
            } catch (e: Exception) {
                stopCurrentVoteTimer()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载投票数据失败"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadData()
    }

    fun toggleHistoryVote(voteId: Int) {
        val currentExpandedId = _uiState.value.expandedHistoryVoteId
        if (currentExpandedId == voteId) {
            _uiState.update { it.copy(expandedHistoryVoteId = null) }
            return
        }

        _uiState.update {
            it.copy(
                expandedHistoryVoteId = voteId,
                historyResultErrors = it.historyResultErrors - voteId
            )
        }
        fetchHistoryVoteResult(voteId)
    }

    fun retryHistoryVoteResult(voteId: Int) {
        fetchHistoryVoteResult(voteId, forceRefresh = true)
    }

    private suspend fun loadTodayVotes(userId: Int): List<Vote> {
        val todayStr = LocalDate.now().toString()
        val todayMeetings = repository.getMeetings(
            limit = 50,
            startDate = todayStr,
            endDate = todayStr,
            userId = userId
        )

        joinMeetingRooms(todayMeetings)

        return todayMeetings.flatMap { meeting ->
            repository.getVoteList(meeting.id)
        }
    }

    private fun joinMeetingRooms(meetings: List<Meeting>) {
        trackedMeetingIds = meetings.map { it.id }.toSet()
        try {
            socketManager.connect(appSettingsState.getSocketBaseUrl())
            trackedMeetingIds.forEach(socketManager::joinMeeting)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveCurrentDisplayVote(votes: List<Vote>): Vote? {
        return votes
            .filter { it.status in setOf("draft", "countdown", "active") }
            .sortedWith(
                compareByDescending<Vote> { voteStatusPriority(it.status) }
                    .thenByDescending { parseVoteDateTime(it.started_at ?: it.created_at) ?: LocalDateTime.MIN }
            )
            .firstOrNull()
    }

    private fun voteStatusPriority(status: String): Int {
        return when (status) {
            "active" -> 3
            "countdown" -> 2
            "draft" -> 1
            else -> 0
        }
    }

    private fun startCurrentVoteTimer(vote: Vote?) {
        stopCurrentVoteTimer()
        if (vote == null || vote.status !in setOf("countdown", "active")) {
            return
        }

        currentVoteTimerJob = viewModelScope.launch {
            val voteId = vote.id
            var waitLeft = (vote.wait_seconds ?: 0).coerceAtLeast(0)
            var remainingLeft = (vote.remaining_seconds ?: 0).coerceAtLeast(0)

            while (isActive) {
                kotlinx.coroutines.delay(1000)

                if (waitLeft > 0) {
                    waitLeft -= 1
                    _uiState.update { state ->
                        val currentVote = state.currentDisplayVote
                        if (currentVote?.id != voteId) {
                            state
                        } else {
                            state.copy(
                                currentDisplayVote = currentVote.copy(wait_seconds = waitLeft, remaining_seconds = 0)
                            )
                        }
                    }
                    if (waitLeft == 0) {
                        loadData()
                        break
                    }
                    continue
                }

                if (remainingLeft > 0) {
                    remainingLeft -= 1
                    _uiState.update { state ->
                        val currentVote = state.currentDisplayVote
                        if (currentVote?.id != voteId) {
                            state
                        } else {
                            state.copy(
                                currentDisplayVote = currentVote.copy(wait_seconds = 0, remaining_seconds = remainingLeft)
                            )
                        }
                    }
                    if (remainingLeft == 0) {
                        loadData()
                        break
                    }
                    continue
                }

                loadData()
                break
            }
        }
    }

    private fun stopCurrentVoteTimer() {
        currentVoteTimerJob?.cancel()
        currentVoteTimerJob = null
    }

    private fun fetchHistoryVoteResult(voteId: Int, forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (!forceRefresh && (voteId in state.historyVoteResults || voteId in state.loadingHistoryResultIds)) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingHistoryResultIds = it.loadingHistoryResultIds + voteId,
                    historyResultErrors = it.historyResultErrors - voteId
                )
            }

            val result = try {
                repository.getVoteResult(voteId)
            } catch (e: Exception) {
                null
            }

            _uiState.update {
                val nextLoading = it.loadingHistoryResultIds - voteId
                if (result != null) {
                    it.copy(
                        loadingHistoryResultIds = nextLoading,
                        historyVoteResults = it.historyVoteResults + (voteId to result),
                        historyResultErrors = it.historyResultErrors - voteId
                    )
                } else {
                    it.copy(
                        loadingHistoryResultIds = nextLoading,
                        historyResultErrors = it.historyResultErrors + (voteId to "暂时无法获取投票结果")
                    )
                }
            }
        }
    }

    private fun parseVoteDateTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value.replace(" ", "T"))
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCurrentVoteTimer()
    }
}
