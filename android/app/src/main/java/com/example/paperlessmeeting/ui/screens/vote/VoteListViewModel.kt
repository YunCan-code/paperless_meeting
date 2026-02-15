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
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    private val socketManager: com.example.paperlessmeeting.data.remote.SocketManager
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
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.voteStartEvent.collect { vote ->
                // Refresh list when new vote starts
                loadData()
            }
        }
        
        // Also listen for end events to auto-refresh list (e.g. to move it to history or update status)
        viewModelScope.launch {
            socketManager.voteEndEvent.collect {
                loadData()
            }
        }
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
                val now = java.time.LocalDateTime.now()

                for (meeting in todayMeetings) {
                    try {
                        val votes = repository.getVoteList(meeting.id)
                        val filtered = votes.filter { vote ->
                            when (vote.status) {
                                "active" -> true
                                "closed" -> {
                                    // Keep closed votes for 10 minutes after expected end time
                                    // expected_end = started_at + duration
                                    if (vote.started_at != null) {
                                        try {
                                            // Backend uses isoformat(), e.g. "2023-10-27T10:00:00.123456"
                                            val startedAt = java.time.LocalDateTime.parse(vote.started_at)
                                            val duration = vote.duration_seconds
                                            val endTime = startedAt.plusSeconds(duration.toLong())
                                            val hideTime = endTime.plusMinutes(10)
                                            now.isBefore(hideTime)
                                        } catch (e: Exception) {
                                            false 
                                        }
                                    } else {
                                        false
                                    }
                                }
                                else -> false // "draft" not shown
                            }
                        }
                        allActiveVotes.addAll(filtered)
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
                
                // 4. Connect Socket and Join Rooms
                joinMeetingRooms(todayMeetings)
                
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    private fun joinMeetingRooms(meetings: List<com.example.paperlessmeeting.domain.model.Meeting>) {
        try {
            // Establish connection (idempotent if already connected)
            // Note: Ideally this URL should come from a centralized config
            socketManager.connect("https://coso.top")
            
            meetings.forEach { meeting ->
                socketManager.joinMeeting(meeting.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun refresh() {
        loadData()
    }
}
