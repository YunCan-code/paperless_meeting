package com.example.paperlessmeeting.ui.screens.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoteDetailViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    private val socketManager: com.example.paperlessmeeting.data.remote.SocketManager
) : ViewModel() {

    data class VoteDetailUiState(
        val isLoading: Boolean = true,
        val vote: Vote? = null,
        val hasVoted: Boolean = false,
        val selectedOptionIds: Set<Int> = emptySet(),
        val voteResult: VoteResult? = null,
        val error: String? = null,
        val timeLeft: Int = 0,    // Real-time countdown for voting duration
        val waitLeft: Int = 0     // Real-time countdown for start wait
    )

    private val _uiState = MutableStateFlow(VoteDetailUiState())
    val uiState = _uiState.asStateFlow()
    
    // Timer job
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        // Listen for vote updates (e.g. real-time result changes if we want to show them)
        viewModelScope.launch {
            socketManager.voteUpdateEvent.collect { update ->
                val currentVote = _uiState.value.vote
                if (currentVote != null && update.vote_id == currentVote.id) {
                    // Update results dynamically
                     val result = repository.getVoteResult(currentVote.id)
                     _uiState.update { it.copy(voteResult = result) }
                }
            }
        }

        // Listen for vote end
        viewModelScope.launch {
             socketManager.voteEndEvent.collect { endInfo ->
                 val currentVote = _uiState.value.vote
                 // The event payload might be just ID or object, check usage
                 // Assuming endInfo matches current vote
                 if (currentVote != null) {
                     loadVote(currentVote.id) // Reload to get final status and results
                 }
             }
        }
    }

    fun loadVote(voteId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = userPreferences.getUserId()
                val validUserId = if (userId != -1) userId else null

                // 1. Get Vote Details
                val vote = repository.getVote(voteId, validUserId)
                if (vote == null) {
                    _uiState.update { it.copy(isLoading = false, error = "投票不存在") }
                    return@launch
                }

                // 2. Fetch Result if closed
                var result: VoteResult? = null
                if (vote.status == "closed" || vote.user_voted) {
                     result = repository.getVoteResult(voteId)
                }
                
                // Initialize timers
                // Parse started_at for accurate sync
                val startedAtStr = vote.started_at
                var startedAt: java.time.LocalDateTime? = null
                if (startedAtStr != null) {
                    try {
                        startedAt = java.time.LocalDateTime.parse(startedAtStr)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // If we failed to parse, fallback to returned seconds
                // But generally we should have startedAt if active/active-wait
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        vote = vote,
                        voteResult = result,
                        hasVoted = vote.status == "closed" || vote.user_voted
                    ) 
                }
                
                startTimer(startedAt, vote.duration_seconds)
                
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    private fun startTimer(startedAt: java.time.LocalDateTime?, durationSeconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                // Synchronization logic:
                // If we have startedAt, calc diff from NOW
                // active status logic in Backend:
                // started_at = creation + 10s (Wait period)
                // So:
                // If NOW < startedAt: Waiting. waitLeft = startedAt - NOW
                // If NOW >= startedAt: Voting. timeLeft = duration - (NOW - startedAt)
                
                var newWait = 0
                var newTime = 0
                
                if (startedAt != null) {
                    val now = java.time.LocalDateTime.now()
                    val diffSeconds = java.time.temporal.ChronoUnit.SECONDS.between(now, startedAt)
                    
                    if (diffSeconds > 0) {
                        // Waiting to start
                        newWait = diffSeconds.toInt()
                        newTime = durationSeconds // Full duration remains
                    } else {
                        // Started
                        newWait = 0
                        val elapsed = -diffSeconds // diff is negative
                        newTime = (durationSeconds - elapsed).toInt().coerceAtLeast(0)
                    }
                }
                
                _uiState.update { state ->
                    // Auto-refresh if time is up and we think it's still active
                    if (state.vote?.status == "active" && newTime == 0 && newWait == 0 && (state.timeLeft > 0 || state.waitLeft > 0)) {
                         // Just hit zero, refresh to check if closed
                         // loadVote(state.vote.id) // Optional: heavy refresh?
                    }
                    state.copy(waitLeft = newWait, timeLeft = newTime)
                }
                
                kotlinx.coroutines.delay(500) // update twice a second for smoothness
            }
        }
    }

    fun toggleOption(optionId: Int) {
        val current = _uiState.value
        val vote = current.vote ?: return
        
        // Block if waiting or closed or voted
        if (current.waitLeft > 0) return 
        if (vote.status != "active" || current.hasVoted) return

        val newSelection = if (vote.is_multiple) {
            val currentIds = current.selectedOptionIds
            if (currentIds.contains(optionId)) {
                currentIds - optionId
            } else {
                if (currentIds.size < vote.max_selections) {
                    currentIds + optionId
                } else {
                    currentIds // Reached max
                }
            }
        } else {
            setOf(optionId)
        }
        _uiState.update { it.copy(selectedOptionIds = newSelection) }
    }

    fun submitVote() {
        val current = _uiState.value
        val vote = current.vote ?: return
        
        // Double check wait time
        if (current.waitLeft > 0) {
             _uiState.update { it.copy(error = "投票尚未开始，请稍候...") }
             return
        }
        
        val optionIds = current.selectedOptionIds.toList()
        
        if (optionIds.isEmpty()) return

        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId == -1) {
                    _uiState.update { it.copy(error = "用户未登录") }
                    return@launch
                }
                
                repository.submitVote(vote.id, userId, optionIds)
                
                // Success
                // Fetch latest result
                 val result = repository.getVoteResult(vote.id)
                 
                _uiState.update { 
                    it.copy(
                        hasVoted = true,
                        voteResult = result,
                        error = null 
                    ) 
                }
            } catch (e: Exception) {
                // If 400 and says "Already voted", we should handle it
                if (e.message?.contains("已投过") == true) {
                     // Fetch result and set hasVoted
                     val result = repository.getVoteResult(vote.id)
                     _uiState.update { 
                        it.copy(
                            hasVoted = true,
                            voteResult = result,
                            error = "您已参与过投票" // Show as info?
                        ) 
                    }
                } else {
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
