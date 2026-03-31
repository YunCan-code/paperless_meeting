package com.example.paperlessmeeting.ui.screens.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.MeetingStatus
import com.example.paperlessmeeting.domain.model.Vote
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoteListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager,
    private val appSettingsState: AppSettingsState
) : ViewModel() {

    data class VoteListUiState(
        val isLoading: Boolean = false,
        val todayMeetings: List<Meeting> = emptyList(),
        val selectedMeetingId: Int? = null,
        val selectedMeeting: Meeting? = null,
        val selectedMeetingActiveVotes: List<Vote> = emptyList(),
        val selectedMeetingHistoryVotes: List<Vote> = emptyList(),
        val globalHistoryVotes: List<Vote> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(VoteListUiState())
    val uiState = _uiState.asStateFlow()

    private var votesByMeetingId: Map<Int, List<Vote>> = emptyMap()

    init {
        loadData()
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            socketManager.voteStartEvent.collect {
                loadData()
            }
        }

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
                val userId = userPreferences.getUserId()
                if (userId == -1) {
                    _uiState.update { it.copy(isLoading = false, error = "用户未登录") }
                    return@launch
                }

                val todayStr = java.time.LocalDate.now().toString()
                val todayMeetings = repository.getMeetings(
                    limit = 20,
                    startDate = todayStr,
                    endDate = todayStr
                )

                val sortedMeetings = todayMeetings.sortedWith(
                    compareBy<Meeting>(
                        { meetingStatusRank(it.getUiStatus()) },
                        { parseMeetingDateTime(it.startTime) ?: LocalDateTime.MAX }
                    )
                )

                votesByMeetingId = sortedMeetings.associate { meeting ->
                    meeting.id to repository.getVoteList(meeting.id)
                }

                val globalHistoryVotes = repository.getVoteHistory(userId, 0, 50)
                    .sortedByDescending { parseMeetingDateTime(it.started_at ?: it.created_at) ?: LocalDateTime.MIN }

                val selectedMeetingId = resolveSelectedMeetingId(
                    currentSelectedId = _uiState.value.selectedMeetingId,
                    meetings = sortedMeetings,
                    voteMap = votesByMeetingId
                )

                val selectedMeeting = sortedMeetings.find { it.id == selectedMeetingId }
                val selectedMeetingVotes = votesByMeetingId[selectedMeetingId].orEmpty()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        todayMeetings = sortedMeetings,
                        selectedMeetingId = selectedMeetingId,
                        selectedMeeting = selectedMeeting,
                        selectedMeetingActiveVotes = resolveMeetingActiveVotes(selectedMeetingVotes),
                        selectedMeetingHistoryVotes = resolveMeetingHistoryVotes(selectedMeetingVotes),
                        globalHistoryVotes = globalHistoryVotes,
                        error = null
                    )
                }

                joinMeetingRooms(sortedMeetings)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载投票数据失败"
                    )
                }
            }
        }
    }

    fun selectMeeting(meetingId: Int) {
        val selectedMeeting = _uiState.value.todayMeetings.find { it.id == meetingId } ?: return
        val votes = votesByMeetingId[meetingId].orEmpty()
        _uiState.update {
            it.copy(
                selectedMeetingId = meetingId,
                selectedMeeting = selectedMeeting,
                selectedMeetingActiveVotes = resolveMeetingActiveVotes(votes),
                selectedMeetingHistoryVotes = resolveMeetingHistoryVotes(votes)
            )
        }
    }

    fun refresh() {
        loadData()
    }

    private fun joinMeetingRooms(meetings: List<Meeting>) {
        try {
            socketManager.connect(appSettingsState.getSocketBaseUrl())
            meetings.forEach { meeting ->
                socketManager.joinMeeting(meeting.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveSelectedMeetingId(
        currentSelectedId: Int?,
        meetings: List<Meeting>,
        voteMap: Map<Int, List<Vote>>
    ): Int? {
        if (meetings.isEmpty()) return null
        if (currentSelectedId != null && meetings.any { it.id == currentSelectedId }) {
            return currentSelectedId
        }

        meetings
            .filter { it.getUiStatus() == MeetingStatus.Ongoing }
            .minByOrNull { parseMeetingDateTime(it.startTime) ?: LocalDateTime.MAX }
            ?.let { return it.id }

        meetings
            .filter { it.getUiStatus() == MeetingStatus.Upcoming }
            .minByOrNull { parseMeetingDateTime(it.startTime) ?: LocalDateTime.MAX }
            ?.let { return it.id }

        meetings
            .filter { voteMap[it.id].orEmpty().isNotEmpty() }
            .minByOrNull { parseMeetingDateTime(it.startTime) ?: LocalDateTime.MAX }
            ?.let { return it.id }

        return meetings.minByOrNull { parseMeetingDateTime(it.startTime) ?: LocalDateTime.MAX }?.id
    }

    private fun resolveMeetingActiveVotes(votes: List<Vote>): List<Vote> {
        return votes
            .filter { it.status == "active" }
            .sortedWith(
                compareBy<Vote> { if ((it.wait_seconds ?: 0) > 0) 0 else 1 }
                    .thenByDescending { parseMeetingDateTime(it.started_at ?: it.created_at) ?: LocalDateTime.MIN }
            )
    }

    private fun resolveMeetingHistoryVotes(votes: List<Vote>): List<Vote> {
        return votes
            .filter { it.status == "closed" }
            .sortedByDescending { parseMeetingDateTime(it.started_at ?: it.created_at) ?: LocalDateTime.MIN }
    }

    private fun meetingStatusRank(status: MeetingStatus): Int {
        return when (status) {
            MeetingStatus.Ongoing -> 0
            MeetingStatus.Upcoming -> 1
            MeetingStatus.Finished -> 2
            MeetingStatus.Draft -> 3
        }
    }

    private fun parseMeetingDateTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value.replace(" ", "T"))
        } catch (_: Exception) {
            null
        }
    }
}
