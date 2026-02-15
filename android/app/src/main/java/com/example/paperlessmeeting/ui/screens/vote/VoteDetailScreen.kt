package com.example.paperlessmeeting.ui.screens.vote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteOption
import com.example.paperlessmeeting.domain.model.VoteResult

// 定义统一的配色（建议放入 Theme.kt）
private val PrimaryBlue = Color(0xFF1976D2)
private val BackgroundGray = Color(0xFFF5F7FA)
private val SuccessGreen = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteDetailScreen(
    navController: NavController,
    voteId: Int,
    viewModel: VoteDetailViewModel = hiltViewModel()
) {
    // 假设 ViewModel 提供了 uiState
    val uiState by viewModel.uiState.collectAsState()
    
    // 初始化加载
    LaunchedEffect(voteId) {
        viewModel.loadVote(voteId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("投票详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            // 底部固定操作栏，仅在进行中且未投票时显示提交按钮
            if (uiState.vote?.status == "active" && !uiState.hasVoted) {
                Surface(
                    shadowElevation = 8.dp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { viewModel.submitVote() },
                            enabled = uiState.selectedOptionIds.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("提交投票", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
             Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        } else {
            uiState.vote?.let { vote ->
                VoteContent(
                    vote = vote,
                    hasVoted = uiState.hasVoted,
                    selectedOptions = uiState.selectedOptionIds,
                    voteResult = uiState.voteResult,
                    onOptionSelected = viewModel::toggleOption,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun VoteContent(
    vote: Vote,
    hasVoted: Boolean,
    selectedOptions: Set<Int>,
    voteResult: VoteResult?,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 头部信息卡片
        item {
            VoteHeaderCard(vote, hasVoted)
        }

        // 2. 状态/结果分割线
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = if (voteResult != null) "投票结果" else "请选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                if (vote.is_multiple && voteResult == null) {
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("多选 · 最多${vote.max_selections}项") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = PrimaryBlue.copy(alpha = 0.1f),
                            labelColor = PrimaryBlue
                        ),
                        border = null
                    )
                }
            }
        }

        // 3. 选项列表 或 结果列表
        if (voteResult != null) {
            // 显示结果条
            items(voteResult.results.sortedByDescending { it.count }) { resultItem ->
                VoteResultItem(resultItem, voteResult.total_voters)
            }
        } else {
            // 显示投票选项
            items(vote.options) { option ->
                VoteOptionItem(
                    option = option,
                    isSelected = selectedOptions.contains(option.id),
                    enabled = vote.status == "active" && !hasVoted,
                    isMultiple = vote.is_multiple,
                    onClick = { onOptionSelected(option.id) }
                )
            }
        }
        
        // 底部留白，防止被 BottomBar 遮挡
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun VoteHeaderCard(vote: Vote, hasVoted: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 状态标签
                val (statusText, statusColor, statusBg) = when {
                    vote.status == "closed" -> Triple("已结束", Color.Gray, Color.Gray.copy(0.1f))
                    hasVoted -> Triple("已参与", SuccessGreen, SuccessGreen.copy(0.1f))
                    else -> Triple("进行中", PrimaryBlue, PrimaryBlue.copy(0.1f))
                }
                
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // 可以在这里放倒计时组件 (参考原 BottomSheet 的 CountdownBadge)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = vote.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (!vote.description.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = vote.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun VoteOptionItem(
    option: VoteOption,
    isSelected: Boolean,
    enabled: Boolean,
    isMultiple: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) PrimaryBlue else Color.Transparent
    val backgroundColor = if (isSelected) PrimaryBlue.copy(alpha = 0.05f) else Color.White
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if(isSelected) BorderStroke(1.5.dp, borderColor) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 自定义 RadioButton 或 Checkbox 样式
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) PrimaryBlue else Color.LightGray,
                        shape = if (isMultiple) RoundedCornerShape(4.dp) else CircleShape
                    )
                    .background(
                        color = if (isSelected) PrimaryBlue else Color.Transparent,
                        shape = if (isMultiple) RoundedCornerShape(4.dp) else CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Text(
                text = option.content,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) PrimaryBlue else Color.DarkGray
            )
        }
    }
}

@Composable
fun VoteResultItem(result: com.example.paperlessmeeting.domain.model.VoteOptionResult, totalVoters: Int) {
    // 这里可以直接复用你之前的 ResultView 逻辑，或者将其简化为进度条形式
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(result.content, fontWeight = FontWeight.Medium)
            Text(
                "${result.count}票", 
                color = PrimaryBlue, 
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { result.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = PrimaryBlue,
            trackColor = Color(0xFFE0E0E0),
        )
    }
}
