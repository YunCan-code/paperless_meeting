package com.example.paperlessmeeting.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.ui.components.GlassyTopBar
import com.example.paperlessmeeting.ui.components.MeetingCard

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    HomeContent(
        uiState = uiState,
        onMeetingClick = { meetingId ->
            navController.navigate("detail/$meetingId")
        }
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onMeetingClick: (Int) -> Unit
) {
    Scaffold(
        topBar = { GlassyTopBar(title = "会议列表") },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is HomeUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.meetings) { meeting ->
                            MeetingCard(
                                meeting = meeting,
                                onClick = { onMeetingClick(meeting.id) }
                            )
                        }
                    }
                }
                is HomeUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    com.example.paperlessmeeting.ui.theme.PaperlessMeetingTheme {
        HomeContent(
            uiState = HomeUiState.Success(
                listOf(
                    com.example.paperlessmeeting.domain.model.Meeting(
                        id = 1,
                        title = "演示会议",
                        meetingTypeId = 1, // Weekly
                        status = "ongoing",
                        startTime = "09:00",
                        endTime = "10:00",
                        location = "会议室A",
                        host = "演示者"
                    )
                )
            ),
            onMeetingClick = {}
        )
    }
}
