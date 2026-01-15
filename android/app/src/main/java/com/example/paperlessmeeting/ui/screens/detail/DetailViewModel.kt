package com.example.paperlessmeeting.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteResult
import com.example.paperlessmeeting.domain.model.VoteSubmitRequest
import com.example.paperlessmeeting.data.remote.VoteUpdateData
import com.example.paperlessmeeting.data.remote.VoteEndData
import kotlinx.coroutines.flow.collectLatest
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val socketManager: SocketManager,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val meetingId: String? = savedStateHandle["meetingId"]
    
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    // Voting State
    private val _currentVote = MutableStateFlow<Vote?>(null)
    val currentVote: StateFlow<Vote?> = _currentVote.asStateFlow()

    private val _voteResult = MutableStateFlow<VoteResult?>(null)
    val voteResult: StateFlow<VoteResult?> = _voteResult.asStateFlow()

    private val _hasVoted = MutableStateFlow(false)
    val hasVoted: StateFlow<Boolean> = _hasVoted.asStateFlow()

    private val _showVoteSheet = MutableStateFlow(false)
    val showVoteSheet: StateFlow<Boolean> = _showVoteSheet.asStateFlow()

    // Server URL needed for Socket connection (assuming base URL logic or configurable)
    // For now, hardcode or retrieve from a config. 
    // Ideally this should come from a Repository or Config provider.
    private val serverUrl = "http://10.0.2.2:8000" // Emulator localhost

    init {
        loadMeeting()
        observeSocketEvents()
        checkActiveVote()
    }

    private fun loadMeeting() {
        viewModelScope.launch {
            try {
                val id = meetingId?.toIntOrNull()
                if (id == null) {
                    _uiState.value = DetailUiState.Error("Invalid Meeting ID")
                    return@launch
                }
                val meeting = repository.getMeetingById(id)
                if (meeting != null) {
                    _uiState.value = DetailUiState.Success(meeting)
                    
                    // Connect Socket when meeting is loaded
                    socketManager.connect(serverUrl)
                    socketManager.joinMeeting(id)
                } else {
                    _uiState.value = DetailUiState.Error("Meeting not found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = DetailUiState.Error("Error: ${e.message}")
            }
        }
    }

    private fun checkActiveVote() {
        val id = meetingId?.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                // Assuming apiService is available via repository, or need to add it to Repo
                // If repository doesn't expose it, we might need to update Repository first.
                // Let's check repository... (Wait, I only updated ApiService, not Repository)
                // We'll update Repository in next step or use direct call if injected (but structure demands Repo)
                // TEMPORARY: using repository.apiService if public, or assuming updated Repo.
                // Since I cannot update Repo in this turn easily without seeing it, I will assume 
                // I need to update Repository first. But let's write the VM logic and then update Repo.
                val vote = repository.getActiveVote(id)
                if (vote != null) {
                    _currentVote.value = vote
                    _showVoteSheet.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            launch {
                socketManager.voteStartEvent.collectLatest { vote ->
                    _currentVote.value = vote
                    _voteResult.value = null
                    _hasVoted.value = false
                    _showVoteSheet.value = true
                }
            }
            launch {
                socketManager.voteUpdateEvent.collectLatest { data ->
                    // Real-time updates if sheet is open?
                    // Currently UI shows result only after vote end or if voted.
                    // We can update result if we have it
                    if (_voteResult.value != null && _voteResult.value?.vote_id == data.vote_id) {
                         // Update partial result logic if needed, but usually we wait for end or user vote
                         // If we want real-time bar chart update:
                         _voteResult.value = _voteResult.value?.copy(results = data.results)
                    }
                }
            }
            launch {
                socketManager.voteEndEvent.collectLatest { data ->
                     if (_currentVote.value?.id == data.vote_id) {
                         _voteResult.value = VoteResult(
                             vote_id = data.vote_id,
                             title = data.title,
                             total_voters = data.total_voters,
                             results = data.results
                         )
                         _showVoteSheet.value = true // Ensure sheet is up to show result
                     }
                }
            }
        }
    }

    fun submitVote(optionIds: List<Int>) {
        val voteId = _currentVote.value?.id ?: return
        // Need user ID. Assuming saved in Prefs. 
        // For simplicity let's assume Repository can handle or pass a dummy ID for now if Auth not fully wired
        // Actually UserPreferences is available.
        // Let's pass to repository
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId != -1) {
                    repository.submitVote(voteId, userId, optionIds)
                    _hasVoted.value = true
                }
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            }
        }
    }

    fun dismissVoteSheet() {
        _showVoteSheet.value = false
    }

    fun openVoteSheet() {
        if (_currentVote.value != null) {
            _showVoteSheet.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.disconnect()
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val meeting: Meeting) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
