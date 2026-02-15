package com.example.paperlessmeeting.ui.screens.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Vote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoteListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences
) : ViewModel() {

    data class VoteListUiState(
        val isLoading: Boolean = false,
        val activeVotes: List<Vote> = emptyList(),
        val historyVotes: List<Vote> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(VoteListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Get User ID
                val userId = userPreferences.getUserId()
                if (userId == -1) {
                    _uiState.update { it.copy(isLoading = false, error = "用户未登录") }
                    return@launch
                }

                // 2. Load Active Votes (from today's meetings)
                // Logic similar to DashboardViewModel but focused on list
                val todayStr = java.time.LocalDate.now().toString()
                val todayMeetings = repository.getMeetings(
                    limit = 20, 
                    startDate = todayStr, 
                    endDate = todayStr
                )
                
                val allActiveVotes = mutableListOf<Vote>()
                for (meeting in todayMeetings) {
                    try {
                        val votes = repository.getVoteList(meeting.id)
                        allActiveVotes.addAll(votes.filter { it.status != "draft" })
                    } catch (e: Exception) {
                        continue
                    }
                }

                // 3. Load History Votes
                val history = repository.getVoteHistory(userId, 0, 50)

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        activeVotes = allActiveVotes,
                        historyVotes = history
                    ) 
                }
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun refresh() {
        loadData()
    }
}
