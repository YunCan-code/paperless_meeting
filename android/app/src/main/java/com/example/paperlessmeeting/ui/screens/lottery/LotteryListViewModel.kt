package com.example.paperlessmeeting.ui.screens.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryHistoryResponse
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LotteryListUiState(
    val activeLotteries: List<Meeting> = emptyList(), // Meetings with potential active lotteries (simplified for now to just meetings)
    val historyLotteries: List<LotteryHistoryResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LotteryListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LotteryListUiState())
    val uiState: StateFlow<LotteryListUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 1. Get Active Meetings (for "Active" tab)
                // We'll trust the repository's logic for today's meetings or "active" meetings
                val todayStr = java.time.LocalDate.now().toString()
                val meetings = repository.getMeetings(
                    limit = 20, 
                    startDate = todayStr, 
                    endDate = todayStr
                )
                
                // 2. Get History (for "History" tab)
                val userId = userPreferences.getUserId()
                val history = repository.getUserLotteryHistory(userId)
                
                _uiState.value = _uiState.value.copy(
                    activeLotteries = meetings,
                    historyLotteries = history,
                    isLoading = false
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
}
