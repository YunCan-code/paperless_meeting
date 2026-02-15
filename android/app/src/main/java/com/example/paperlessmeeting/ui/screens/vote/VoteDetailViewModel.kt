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
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences
) : ViewModel() {

    data class VoteDetailUiState(
        val isLoading: Boolean = true,
        val vote: Vote? = null,
        val hasVoted: Boolean = false,
        val selectedOptionIds: Set<Int> = emptySet(),
        val voteResult: VoteResult? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(VoteDetailUiState())
    val uiState = _uiState.asStateFlow()

    fun loadVote(voteId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Get Vote Details
                val vote = repository.getVote(voteId)
                if (vote == null) {
                    _uiState.update { it.copy(isLoading = false, error = "投票不存在") }
                    return@launch
                }

                // 2. Fetch Result if closed
                var result: VoteResult? = null
                if (vote.status == "closed") {
                     result = repository.getVoteResult(voteId)
                }
                
                // TODO: Check if user has voted (API limitation: can't check easily yet without erroring on submit)
                // For now, assume false unless we have local persistence or API update.
                // However, if we are in "History", likely we want to show results?
                // If it's active history, we might want to check.
                // Current hack: Try to submit? No. 
                // We will rely on user memory or error message for now.
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        vote = vote,
                        voteResult = result,
                        // If it's closed, we assume interaction is done.
                        hasVoted = vote.status == "closed" 
                    ) 
                }
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleOption(optionId: Int) {
        val current = _uiState.value
        val vote = current.vote ?: return
        
        // If closed or voted, disable
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
}
