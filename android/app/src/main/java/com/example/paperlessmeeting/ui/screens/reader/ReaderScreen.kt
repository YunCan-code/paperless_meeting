package com.example.paperlessmeeting.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    meetingId: Int, // Context parameters
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
    
    // Page state for UI display
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }

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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                is ReaderUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ReaderUiState.Ready -> {
                    AndroidView(
                         factory = { context ->
                             PDFView(context, null).apply {
                                 // Basic initialization
                             }
                         },
                         modifier = Modifier.fillMaxSize(),
                         update = { pdfView ->
                             pdfView.fromFile(state.file)
                                 .defaultPage(currentPage)
                                 .enableSwipe(true)
                                 .swipeHorizontal(true)
                                 .enableDoubletap(true)
                                 .onPageChange { page: Int, pageCount: Int ->
                                     currentPage = page
                                     totalPages = pageCount
                                 }
                                 .onTap { e -> 
                                     showOverlay = !showOverlay
                                     true 
                                 }
                                 .scrollHandle(DefaultScrollHandle(pdfView.context))
                                 .load()
                         }
                    )

                    // Overlay Bottom Bar
                    AnimatedVisibility(
                        visible = showOverlay,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                         enter = slideInVertically { it } + fadeIn(),
                         exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Text(
                            text = "${currentPage + 1} / $totalPages",
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
