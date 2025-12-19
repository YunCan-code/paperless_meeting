package com.example.paperlessmeeting.ui.screens.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    meetingId: Int, // Just for context if needed
    attachmentId: Int,
    downloadUrl: String,
    fileName: String,
    navController: NavController,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(downloadUrl) {
        viewModel.loadDocument(downloadUrl, fileName)
    }

    val uiState by viewModel.uiState.collectAsState()
    var showOverlay by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            AnimatedVisibility(
                visible = showOverlay,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                TopAppBar(
                    title = { 
                        Text(
                            fileName, 
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showOverlay) innerPadding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReaderUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ReaderUiState.Ready -> {
                    val pagerState = rememberPagerState(pageCount = { state.pageCount })

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val widthFn = constraints.maxWidth
                        val heightFn = constraints.maxHeight

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showOverlay = !showOverlay }
                        ) { pageIndex ->
                            PdfPage(
                                pageIndex = pageIndex,
                                viewModel = viewModel,
                                width = widthFn,
                                height = heightFn
                            )
                        }
                    }

                    // Overlay Bottom Bar (Page Indicator)
                    AnimatedVisibility(
                        visible = showOverlay,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                         enter = slideInVertically { it } + fadeIn(),
                         exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${state.pageCount}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun PdfPage(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    width: Int,
    height: Int
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        val bmp = withContext(Dispatchers.Default) {
             viewModel.renderPage(pageIndex, width, height)
        }
        value = bmp
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmapState.value != null) {
            Image(
                bitmap = bitmapState.value!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(color = Color.Gray)
        }
    }
}
