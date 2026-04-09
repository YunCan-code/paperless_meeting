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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

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
        val currentDisplayVoteResult: VoteResult? = null,
        val currentDisplayVoteResultError: String? = null,
        val myVoteHistory: List<Vote> = emptyList(),
        val expandedHistoryVoteId: Int? = null,
        val historyVoteResults: Map<Int, VoteResult> = emptyMap(),
        val loadingHistoryResultIds: Set<Int> = emptySet(),
        val historyResultErrors: Map<Int, String> = emptyMap(),
        val selectedCurrentVoteOptionIds: Set<Int> = emptySet(),
        val currentVoteSubmitting: Boolean = false,
        val currentVoteActionError: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(VoteListUiState())
    val uiState = _uiState.asStateFlow()

    private var currentVoteTimerJob: Job? = null
    private var trackedMeetingIds: Set<Int> = emptySet()
    private var lastVisibleRefreshAtMs: Long = 0L
    private val visibleRefreshThrottleMs: Long = 2_000L

    init {
        loadData()
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.voteStateChangeEvent.collect {
                loadData(showLoading = false)
            }
        }

        viewModelScope.launch {
            socketManager.meetingChangedEvent.collect {
                loadData(showLoading = false)
            }
        }

        viewModelScope.launch {
            socketManager.connectionState.collect { connected ->
                if (connected) {
                    trackedMeetingIds.forEach(socketManager::joinMeeting)
                    loadData(showLoading = false)
                }
            }
        }
    }

    fun loadData() {
        loadData(showLoading = true)
    }

    private fun loadData(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }
            try {
                val previousState = _uiState.value
                val previousDisplayVote = previousState.currentDisplayVote
                val userId = userPreferences.getUserId()
                if (userId <= 0) {
                    stopCurrentVoteTimer()
                    trackedMeetingIds = emptySet()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentDisplayVote = null,
                            currentDisplayVoteResult = null,
                            currentDisplayVoteResultError = null,
                            myVoteHistory = emptyList(),
                            expandedHistoryVoteId = null,
                            historyVoteResults = emptyMap(),
                            loadingHistoryResultIds = emptySet(),
                            historyResultErrors = emptyMap(),
                            selectedCurrentVoteOptionIds = emptySet(),
                            currentVoteSubmitting = false,
                            currentVoteActionError = null,
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
                val preservedExpandedId = previousState.expandedHistoryVoteId?.takeIf { it in historyIds }
                val currentVoteCandidate = resolveCurrentDisplayVote(todayVotes)
                val currentDisplayVote = preserveCurrentVoteState(
                    refreshedVote = currentVoteCandidate?.let { candidate ->
                        repository.getVote(candidate.id, userId) ?: candidate
                    },
                    previousVote = previousDisplayVote
                )
                val currentDisplayVoteResult = if (currentDisplayVote?.status == "closed") {
                    try {
                        repository.getVoteResult(currentDisplayVote.id)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                val currentDisplayVoteResultError = if (
                    currentDisplayVote?.status == "closed" && currentDisplayVoteResult == null
                ) {
                    "暂时无法获取投票结果"
                } else {
                    null
                }
                val previousVoteId = previousDisplayVote?.id
                val allowedOptionIds = currentDisplayVote?.options?.map { it.id }?.toSet().orEmpty()
                val preservedSelectedOptionIds = if (
                    currentDisplayVote != null &&
                    currentDisplayVote.id == previousVoteId &&
                    currentDisplayVote.status == "active" &&
                    !currentDisplayVote.user_voted
                ) {
                    previousState.selectedCurrentVoteOptionIds.filterTo(linkedSetOf()) { optionId ->
                        allowedOptionIds.contains(optionId)
                    }
                } else {
                    emptySet()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentDisplayVote = currentDisplayVote,
                        currentDisplayVoteResult = currentDisplayVoteResult,
                        currentDisplayVoteResultError = currentDisplayVoteResultError,
                        myVoteHistory = myVoteHistory,
                        expandedHistoryVoteId = preservedExpandedId,
                        historyVoteResults = it.historyVoteResults.filterKeys { voteId -> historyIds.contains(voteId) },
                        loadingHistoryResultIds = it.loadingHistoryResultIds.filterTo(linkedSetOf()) { voteId ->
                            historyIds.contains(voteId)
                        },
                        historyResultErrors = it.historyResultErrors.filterKeys { voteId -> historyIds.contains(voteId) },
                        selectedCurrentVoteOptionIds = preservedSelectedOptionIds,
                        currentVoteSubmitting = false,
                        currentVoteActionError = null,
                        error = null
                    )
                }

                startCurrentVoteTimer(currentDisplayVote)

                preservedExpandedId?.let { voteId ->
                    fetchHistoryVoteResult(voteId, forceRefresh = true)
                }
            } catch (e: Exception) {
                stopCurrentVoteTimer()
                val hasContent = _uiState.value.currentDisplayVote != null || _uiState.value.myVoteHistory.isNotEmpty()
                _uiState.update {
                    if (!showLoading && hasContent) {
                        it.copy(isLoading = false)
                    } else {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "加载投票数据失败"
                        )
                    }
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
        val hasContent = _uiState.value.currentDisplayVote != null || _uiState.value.myVoteHistory.isNotEmpty()
        loadData(showLoading = !hasContent)
    }

    fun toggleCurrentVoteOption(optionId: Int) {
        val currentState = _uiState.value
        val vote = currentState.currentDisplayVote ?: return
        if (vote.status != "active" || vote.user_voted) return

        val updatedSelection = if (vote.is_multiple) {
            val currentIds = currentState.selectedCurrentVoteOptionIds
            when {
                currentIds.contains(optionId) -> currentIds - optionId
                currentIds.size < vote.max_selections -> currentIds + optionId
                else -> {
                    _uiState.update {
                        it.copy(currentVoteActionError = "最多只能选择 ${vote.max_selections} 项")
                    }
                    return
                }
            }
        } else {
            setOf(optionId)
        }

        _uiState.update {
            it.copy(
                selectedCurrentVoteOptionIds = updatedSelection,
                currentVoteActionError = null
            )
        }
    }

    fun submitCurrentVote() {
        val currentState = _uiState.value
        val vote = currentState.currentDisplayVote ?: return
        if (vote.status != "active" || vote.user_voted) return

        val optionIds = currentState.selectedCurrentVoteOptionIds.toList()
        if (optionIds.isEmpty()) {
            _uiState.update { it.copy(currentVoteActionError = "请至少选择一个选项") }
            return
        }
        if (vote.is_multiple && optionIds.size > vote.max_selections) {
            _uiState.update { it.copy(currentVoteActionError = "最多只能选择 ${vote.max_selections} 项") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(currentVoteSubmitting = true, currentVoteActionError = null)
            }
            try {
                val userId = userPreferences.getUserId()
                if (userId <= 0) {
                    _uiState.update {
                        it.copy(
                            currentVoteSubmitting = false,
                            currentVoteActionError = "用户未登录"
                        )
                    }
                    return@launch
                }

                repository.submitVote(vote.id, userId, optionIds)
                _uiState.update {
                    val optimisticVote = it.currentDisplayVote?.copy(
                        user_voted = true,
                        selected_option_ids = optionIds
                    )
                    it.copy(
                        currentDisplayVote = optimisticVote,
                        selectedCurrentVoteOptionIds = optionIds.toSet(),
                        currentVoteSubmitting = false,
                        currentVoteActionError = null
                    )
                }
                loadData(showLoading = false)
            } catch (e: Exception) {
                val errorMessage = extractErrorMessage(e)
                _uiState.update {
                    it.copy(
                        currentVoteSubmitting = false,
                        currentVoteActionError = errorMessage
                    )
                }
                if (errorMessage.contains("已参与") || errorMessage.contains("已投过")) {
                    loadData(showLoading = false)
                }
            }
        }
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
            .filter { it.status in setOf("draft", "countdown", "active", "closed") }
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
            "closed" -> 0
            else -> 0
        }
    }

    private fun preserveCurrentVoteState(
        refreshedVote: Vote?,
        previousVote: Vote?
    ): Vote? {
        if (refreshedVote == null) return null
        if (previousVote == null || previousVote.id != refreshedVote.id) return refreshedVote

        return when {
            previousVote.user_voted &&
                previousVote.selected_option_ids.isNotEmpty() &&
                refreshedVote.status == "active" &&
                (!refreshedVote.user_voted || refreshedVote.selected_option_ids.isEmpty()) -> {
                refreshedVote.copy(
                    user_voted = true,
                    selected_option_ids = previousVote.selected_option_ids
                )
            }

            refreshedVote.user_voted &&
                refreshedVote.selected_option_ids.isEmpty() &&
                previousVote.selected_option_ids.isNotEmpty() -> {
                refreshedVote.copy(selected_option_ids = previousVote.selected_option_ids)
            }

            else -> refreshedVote
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
                        loadData(showLoading = false)
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
                        loadData(showLoading = false)
                        break
                    }
                    continue
                }

                loadData(showLoading = false)
                break
            }
        }
    }

    private fun stopCurrentVoteTimer() {
        currentVoteTimerJob?.cancel()
        currentVoteTimerJob = null
    }

    private fun extractErrorMessage(exception: Exception): String {
        if (exception is HttpException) {
            val body = exception.response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                try {
                    return JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
                        ?: "请求失败：HTTP ${exception.code()}"
                } catch (_: Exception) {
                    return body
                }
            }
            return "请求失败：HTTP ${exception.code()}"
        }
        return exception.message ?: "操作失败，请稍后重试"
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
