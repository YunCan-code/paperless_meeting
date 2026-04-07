package com.example.paperlessmeeting.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.data.repository.DocumentThumbnailRepository
import com.github.barteksc.pdfviewer.PDFView
import com.shockwave.pdfium.PdfDocument.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// --- Design System: Reader Backgrounds ---
private val StandardRootBackground = Color(0xFFF4F5F7)
private val StandardPanelSurface = Color.White.copy(alpha = 0.96f)
private val StandardText = Color(0xFF2C2C2C)
private val StandardMutedText = Color(0xFF5F6368)
private val StandardDivider = Color(0x1F5F6368)
private val StandardSheetContainer = Color(0xFFF7F8FA)
private val StandardProgressTrack = Color(0xFFE6E9EE)

private val PaperRootBackground = Color(0xFFF4ECDE)
private val PaperPanelSurface = Color(0xFFF3E7D4).copy(alpha = 0.98f)
private val PaperText = Color(0xFF3D3428)
private val PaperMutedText = Color(0xFF7B6A58)
private val PaperDivider = Color(0x1F7B6A58)
private val PaperSheetContainer = Color(0xFFF1E7D7)
private val PaperPdfBackground = Color(0xFFF7EEDC)
private val PaperPageTint = Color(0x30E7D5B4)
private val PaperProgressTrack = Color(0xFFE4D8C6)
private val ReaderControlCapsuleShape = RoundedCornerShape(50)

private enum class ReadingDisplayMode {
    Standard,
    Paper
}

private data class ReaderChromePalette(
    val rootBackground: Color,
    val panelSurface: Color,
    val panelText: Color,
    val panelMutedText: Color,
    val divider: Color,
    val sheetContainer: Color,
    val pdfBackground: Color,
    val progressTrack: Color
)

private fun readerChromePalette(mode: ReadingDisplayMode): ReaderChromePalette = when (mode) {
    ReadingDisplayMode.Standard -> ReaderChromePalette(
        rootBackground = StandardRootBackground,
        panelSurface = StandardPanelSurface,
        panelText = StandardText,
        panelMutedText = StandardMutedText,
        divider = StandardDivider,
        sheetContainer = StandardSheetContainer,
        pdfBackground = Color.White,
        progressTrack = StandardProgressTrack
    )
    ReadingDisplayMode.Paper -> ReaderChromePalette(
        rootBackground = PaperRootBackground,
        panelSurface = PaperPanelSurface,
        panelText = PaperText,
        panelMutedText = PaperMutedText,
        divider = PaperDivider,
        sheetContainer = PaperSheetContainer,
        pdfBackground = PaperPdfBackground,
        progressTrack = PaperProgressTrack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    meetingId: Int,
    attachmentId: Int,
    downloadUrl: String,
    fileName: String,
    initialPage: Int = 0,
    navController: NavController,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(downloadUrl) {
        viewModel.loadDocument(downloadUrl, fileName)
    }
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val pageAnnotations by viewModel.pageAnnotations.collectAsState()
    
    // Sync States
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isPresenterSyncing by viewModel.isPresenterSyncing.collectAsState()
    val isSyncActive by viewModel.isSyncActive.collectAsState()
    val oneShotJumpPage by viewModel.oneShotJumpPage.collectAsState()
    
    // Static Role (No need to collect flow if simple val, but flow safer if role changes dynamically)
    // Actually VM exposes it as val property? No, let's make it a state or just access VM property?
    // VM code was: val isPresenter = ...
    // Since it's init-time determined, direct access is fine, but remember triggers recomposition if passed.
    val isPresenter = viewModel.isPresenter
    val toastEvent by viewModel.toastEvent.collectAsState()
    
    // Init Sync Context
    LaunchedEffect(meetingId, attachmentId, downloadUrl) {
         viewModel.initSync(meetingId, attachmentId, downloadUrl)
    }
    
    // Handle Toast
    LaunchedEffect(toastEvent) {
        toastEvent?.let {
            // android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToastEvent()
        }
    }

    // --- State Management ---
    var showOverlay by remember { mutableStateOf(true) }
    var readingDisplayMode by remember { mutableStateOf(ReadingDisplayMode.Standard) }
    val isPaperBackground = readingDisplayMode == ReadingDisplayMode.Paper
    val chromePalette = remember(readingDisplayMode) { readerChromePalette(readingDisplayMode) }
    
    // Inline Annotation Mode
    var isAnnotating by remember { mutableStateOf(false) }
    var annotPageOffsetX by remember { mutableStateOf(0f) }
    var annotPageOffsetY by remember { mutableStateOf(0f) }
    var annotPageWidth by remember { mutableStateOf(0f) }
    var annotPageHeight by remember { mutableStateOf(0f) }
    
    var isHorizontalScroll by remember { mutableStateOf(false) } // Default Vertical
    
    var currentPage by remember { mutableIntStateOf(initialPage) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isProgrammaticScroll by remember { mutableStateOf(true) } // Prevent scroll fighting
    var hasPendingAnnotationChanges by remember { mutableStateOf(false) }
    var showDiscardAnnotationDialog by remember { mutableStateOf(false) }
    val isAnnotationViewportReady by remember(annotPageWidth, annotPageHeight) {
        derivedStateOf { annotPageWidth > 0f && annotPageHeight > 0f }
    }

    LaunchedEffect(currentPage) {
        annotPageOffsetX = 0f
        annotPageOffsetY = 0f
        annotPageWidth = 0f
        annotPageHeight = 0f
    }

    // Handle Sync Jumps
    LaunchedEffect(oneShotJumpPage) {
        oneShotJumpPage?.let { targetPage ->
            isProgrammaticScroll = true
            currentPage = targetPage
            viewModel.consumeJumpEvent()
        }
    }

    // TOC & Thumbnails
    var tocList by remember { mutableStateOf<List<Pair<Int, Bookmark>>>(emptyList()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showThumbnailSheet by remember { mutableStateOf(false) }

    // Save Progress Effect
    LaunchedEffect(currentPage, totalPages, uiState) {
        if (totalPages > 0 && uiState is ReaderUiState.Ready) {
            viewModel.saveReadingProgress(
                uniqueId = downloadUrl,
                fileName = fileName,
                page = currentPage,
                total = totalPages,
                localPath = (uiState as ReaderUiState.Ready).file.absolutePath
            )
        }
    }
    val sheetState = rememberModalBottomSheetState()

    // Utils
    val cleanTitle = remember(fileName) { fileName.substringBeforeLast(".") } // Removing Extension
    
    // PDF View Reference for capturing state
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

    fun closeThumbnailSheet() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            showThumbnailSheet = false
        }
    }

    fun exitAnnotationMode(forceDiscard: Boolean = false) {
        if (hasPendingAnnotationChanges && !forceDiscard) {
            showDiscardAnnotationDialog = true
            return
        }

        showDiscardAnnotationDialog = false
        hasPendingAnnotationChanges = false
        isAnnotating = false
        pdfViewRef?.invalidate()
    }

    fun handleReaderBack() {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showThumbnailSheet -> closeThumbnailSheet()
            isAnnotating -> exitAnnotationMode()
            else -> navController.popBackStack()
        }
    }

    // Keep system bars independent from the reader overlay to avoid PDF relayout
    // when the user taps to show or hide in-app controls.
    LaunchedEffect(isAnnotating) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 退出时保存最后阅读位置
            if (totalPages > 0) {
                viewModel.saveReadingProgress(
                    uniqueId = downloadUrl,
                    fileName = fileName,
                    page = currentPage,
                    total = totalPages,
                    localPath = (uiState as? ReaderUiState.Ready)?.file?.absolutePath
                )
            }

            // 恢复系统栏显示
            val window = (context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        handleReaderBack()
    }

    // --- UI Structure ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen, // Only allow closing via gesture, prevent opening conflict
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = chromePalette.sheetContainer,
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text(
                        "目录大纲",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = chromePalette.panelText,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    HorizontalDivider(color = chromePalette.divider)
                    if (tocList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无目录", color = chromePalette.panelMutedText)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(tocList) { (depth, bookmark) ->
                                NavigationDrawerItem(
                                    label = {
                                        Row {
                                            Spacer(modifier = Modifier.width((depth * 16).dp))
                                            Text(
                                                bookmark.title, 
                                                maxLines = 1, 
                                                overflow = TextOverflow.Ellipsis, 
                                                color = chromePalette.panelText.copy(alpha = 0.84f)
                                            )
                                        }
                                    },
                                    selected = false,
                                    onClick = {
                                        isProgrammaticScroll = true
                                        currentPage = bookmark.pageIdx.toInt()
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        // Root Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chromePalette.rootBackground)
        ) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center), 
                        color = chromePalette.panelText
                    )
                }
                is ReaderUiState.Downloading -> {
                    // 下载进度 UI - 带返回按钮
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 顶部栏
                        Surface(
                            color = chromePalette.panelSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = ::handleReaderBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = chromePalette.panelText.copy(alpha = 0.82f))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "下载中...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = chromePalette.panelText,
                                    maxLines = 1
                                )
                            }
                        }
                        
                        // 下载进度内容
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "正在下载文件",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = chromePalette.panelText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = chromePalette.panelMutedText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                if (state.progress >= 0f) {
                                    // 已知进度 - 显示百分比和进度条
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = chromePalette.progressTrack
                                    )
                                } else {
                                    // 未知进度 - 显示无限循环动画
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "文件大小未知...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = chromePalette.panelMutedText
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                    text = "返回后下载将在后台继续",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = chromePalette.panelMutedText
                                )
                            }
                        }
                    }
                }
                is ReaderUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 错误图标
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 错误标题
                        Text(
                            text = if (state.canRetry) "下载失败" else "加载错误",
                            color = chromePalette.panelText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 错误详情
                        Text(
                            text = state.message,
                            color = chromePalette.panelMutedText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // 按钮组
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 返回按钮
                            TextButton(onClick = ::handleReaderBack) {
                                Text("返回", color = chromePalette.panelMutedText)
                            }

                            // 重试按钮
                            if (state.canRetry) {
                                Button(
                                    onClick = { viewModel.retryDownload() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("重试")
                                }
                            }
                        }
                    }
                }
                is ReaderUiState.Ready -> {
                    // Load saved annotations when document is ready
                    LaunchedEffect(state.file) {
                        viewModel.loadAnnotations(state.file)
                    }

                    // Reading mode is always rendered as the base layer
                    Box(modifier = Modifier
                        .fillMaxSize()
                    ) {
                        // ====== MODE A: Reading Mode ======
                        // 1. PDF Content
                        PDFViewerContent(
                            file = state.file,
                            defaultStartPage = state.initialPage,
                            pdfBackgroundColor = chromePalette.pdfBackground,
                            isHorizontalScroll = isHorizontalScroll,
                            currentPage = currentPage,
                            isProgrammaticScroll = isProgrammaticScroll,
                            pageAnnotations = pageAnnotations,
                            isAnnotating = isAnnotating,
                            annotatingPage = currentPage,
                            onPageRenderInfo = { x, y, w, h ->
                                annotPageOffsetX = x
                                annotPageOffsetY = y
                                annotPageWidth = w
                                annotPageHeight = h
                            },
                            onPdfViewReady = { pdfViewRef = it },
                            onPageChange = { page, count ->
                                isProgrammaticScroll = false // User scrolled
                                currentPage = page
                                totalPages = count

                                // Presenter Sync Logic
                                if (isPresenterSyncing) {
                                    viewModel.onPresenterPageChanged(page)
                                }
                            },
                            onPdfLoaded = { count ->
                                totalPages = count
                            },
                            onTap = {
                                if (!isAnnotating) showOverlay = !showOverlay
                            },
                            onLoadToc = { list -> tocList = list },
                            modifier = Modifier.fillMaxSize()
                        )

                        AnimatedVisibility(
                            visible = isPaperBackground,
                            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 140))
                        ) {
                            PaperPageTintOverlay()
                        }

                        // 2. Floating Top Bar (hidden during annotation)
                        AnimatedVisibility(
                            visible = showOverlay && !isAnnotating,
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            MinimalistTopBar(
                                title = cleanTitle,
                                palette = chromePalette,
                                isPresenter = isPresenter,
                                isPresenterSyncing = isPresenterSyncing,
                                isFollowing = isFollowing,
                                isSyncActive = isSyncActive,
                                onBackClick = ::handleReaderBack,
                                onPresenterToggle = { viewModel.togglePresenterSync(!isPresenterSyncing) },
                                onFollowToggle = { viewModel.toggleFollow(!isFollowing) }
                            )
                        }

                        // 3. Floating Bottom Capsule (hidden during annotation)
                        AnimatedVisibility(
                            visible = showOverlay && !isAnnotating,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                        ) {
                            FloatingControlCapsule(
                                currentPage = currentPage,
                                totalPages = totalPages,
                                isEditing = false, // Always false here as we aren't in edit mode
                                isAnnotationEnabled = isAnnotationViewportReady,
                                palette = chromePalette,
                                isPaperBackground = isPaperBackground,
                                onTocClick = { scope.launch { drawerState.open() } }, // Open TOC
                                onGridClick = { showThumbnailSheet = true },
                                onPenClick = {
                                    if (isAnnotationViewportReady) {
                                        hasPendingAnnotationChanges = false
                                        showDiscardAnnotationDialog = false
                                        isAnnotating = true
                                        showOverlay = true
                                    } else {
                                        pdfViewRef?.invalidate()
                                    }
                                },
                                onSettingsClick = {
                                    readingDisplayMode = if (isPaperBackground) {
                                        ReadingDisplayMode.Standard
                                    } else {
                                        ReadingDisplayMode.Paper
                                    }
                                }
                            )
                        }

                        // 5. Thumbnails Bottom Sheet
                        if (showThumbnailSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { closeThumbnailSheet() },
                                sheetState = sheetState,
                                containerColor = chromePalette.sheetContainer
                            ) {
                               PdfThumbnailGrid(
                                   file = state.file,
                                   palette = chromePalette,
                                   currentPage = currentPage,
                                   onPageClick = { page ->
                                       isProgrammaticScroll = true
                                       currentPage = page
                                       closeThumbnailSheet()
                                   }
                               ) 
                            }
                        }

                        // 6. Inline Annotation Overlay
                        AnimatedVisibility(
                            visible = isAnnotating,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            InlineAnnotationOverlay(
                                pageIndex = currentPage,
                                initialStrokes = pageAnnotations[currentPage] ?: emptyList(),
                                pageRenderWidth = annotPageWidth,
                                pageRenderHeight = annotPageHeight,
                                pageOffsetX = annotPageOffsetX,
                                pageOffsetY = annotPageOffsetY,
                                onCancel = {
                                    exitAnnotationMode()
                                },
                                onSave = { newStrokes ->
                                    viewModel.updatePageAnnotations(currentPage, newStrokes)
                                    viewModel.saveAnnotations(state.file)
                                    hasPendingAnnotationChanges = false
                                    exitAnnotationMode(forceDiscard = true)
                                },
                                onDirtyChange = { hasPendingAnnotationChanges = it }
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }

    if (showDiscardAnnotationDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardAnnotationDialog = false },
            title = { Text("退出标注") },
            text = { Text("当前标注尚未保存，退出后本页未保存内容将丢失。") },
            confirmButton = {
                TextButton(onClick = { exitAnnotationMode(forceDiscard = true) }) {
                    Text("仍要退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAnnotationDialog = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}

// =========================================================================================
//                              SUBCOMPONENTS
// =========================================================================================

@Composable
private fun PaperPageTintOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperPageTint)
    )
}

@Composable
fun PDFViewerContent(
    file: File,
    defaultStartPage: Int = 0,
    pdfBackgroundColor: Color,
    isHorizontalScroll: Boolean,
    currentPage: Int,
    isProgrammaticScroll: Boolean,
    pageAnnotations: Map<Int, List<AnnotationStroke>>,
    isAnnotating: Boolean = false,
    annotatingPage: Int = 0,
    onPageRenderInfo: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onPdfViewReady: (PDFView) -> Unit,
    onPageChange: (Int, Int) -> Unit,
    onPdfLoaded: (Int) -> Unit,
    onTap: () -> Unit,
    onLoadToc: (List<Pair<Int, Bookmark>>) -> Unit,
    modifier: Modifier
) {
    val currentAnnotations by rememberUpdatedState(pageAnnotations)
    val currentPageState by rememberUpdatedState(currentPage)
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val currentOnPdfLoaded by rememberUpdatedState(onPdfLoaded)
    val currentOnLoadToc by rememberUpdatedState(onLoadToc)
    val currentIsAnnotating by rememberUpdatedState(isAnnotating)
    val currentAnnotatingPage by rememberUpdatedState(annotatingPage)
    val currentOnPageRenderInfo by rememberUpdatedState(onPageRenderInfo)

    // Local ref for page jumping
    var localPdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    var isPdfLoaded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // -------------------------------------------------------------------------
        // LAYER 1: PDF View (Bottom) - Renders PDF content + Saved Annotations
        // -------------------------------------------------------------------------
        AndroidView(
            factory = { context ->
                PDFView(context, null).apply {
                    localPdfViewRef = this
                    onPdfViewReady(this)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(pdfBackgroundColor.toArgb())
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pdfView ->
                pdfView.setBackgroundColor(pdfBackgroundColor.toArgb())
                // Avoid reloading the PDF when only the reading display mode changes.
                val configKey = file.absolutePath

                if (pdfView.tag != configKey) {
                    isPdfLoaded = false // Reset load state on new file
                    pdfView.tag = configKey
                    pdfView.fromFile(file)
                        .defaultPage(defaultStartPage)
                        .enableSwipe(true)
                        .swipeHorizontal(isHorizontalScroll)
                        .pageSnap(false)
                        .autoSpacing(false)
                        .pageFling(true)
                        .fitEachPage(false)
                        .nightMode(false)
                        .enableAnnotationRendering(true)
                        .enableAntialiasing(true)
                        .spacing(10)
                        .onPageChange { page, count ->
                             if (page != currentPageState) {
                                 currentOnPageChange(page, count)
                             }
                        }
                        .onLoad { nbPages ->
                             isPdfLoaded = true // Mark as loaded
                             currentOnLoadToc(flattenBookmarks(pdfView.tableOfContents))
                             // Ensure totalPages is always initialized on load
                             currentOnPdfLoaded(nbPages)
                        }
                        .onDraw { canvas, pageWidth, pageHeight, pageIdx ->
                             // Report page render dimensions for inline annotation overlay
                             if (pageIdx == currentPageState) {
                                 @Suppress("DEPRECATION")
                                 val matrix = canvas.matrix
                                 val values = FloatArray(9)
                                 matrix.getValues(values)
                                 currentOnPageRenderInfo(
                                     values[Matrix.MTRANS_X],
                                     values[Matrix.MTRANS_Y],
                                     pageWidth,
                                     pageHeight
                                 )
                             }

                             // Skip rendering strokes on the annotating page
                             // (the overlay Canvas handles it to avoid double-drawing)
                             if (currentIsAnnotating && pageIdx == currentAnnotatingPage) return@onDraw

                             currentAnnotations[pageIdx]?.forEach { stroke ->
                                 val paint = Paint().apply {
                                     style = Paint.Style.STROKE
                                     strokeWidth = stroke.strokeWidth
                                     strokeJoin = Paint.Join.ROUND
                                     isAntiAlias = true
                                     if (stroke.isHighlighter) {
                                         color = stroke.color
                                         alpha = 90
                                         strokeCap = Paint.Cap.SQUARE
                                         xfermode = android.graphics.PorterDuffXfermode(
                                             android.graphics.PorterDuff.Mode.MULTIPLY
                                         )
                                     } else {
                                         color = stroke.color
                                         strokeCap = Paint.Cap.ROUND
                                     }
                                 }
                                 val path = Path()
                                 if (stroke.points.isNotEmpty()) {
                                     val start = stroke.points[0]
                                     path.moveTo(start.x * pageWidth, start.y * pageHeight)
                                     for (i in 1 until stroke.points.size) {
                                         val p = stroke.points[i]
                                         path.lineTo(p.x * pageWidth, p.y * pageHeight)
                                     }
                                     canvas.drawPath(path, paint)
                                 }
                             }
                        }
                        .onTap { 
                            onTap()
                            true 
                        }
                        .load()
                }
                
                // Force invalidate to redraw annotations if they changed (even if configKey didnt change)
                pdfView.invalidate()
            }
        )

        // Sync Jump Logic
        LaunchedEffect(currentPage, isProgrammaticScroll, isPdfLoaded) {
            // Only jump if PDF is fully loaded to prevent race conditions or ignored calls
            if (isPdfLoaded && isProgrammaticScroll && localPdfViewRef != null) {
                localPdfViewRef?.jumpTo(currentPage, false)
            }
        }
    }
}

@Composable
private fun MinimalistTopBar(
    title: String,
    palette: ReaderChromePalette,
    isPresenter: Boolean,
    isPresenterSyncing: Boolean,
    isFollowing: Boolean,
    isSyncActive: Boolean, // Attendee: Is sync available?
    onBackClick: () -> Unit,
    onPresenterToggle: () -> Unit,
    onFollowToggle: () -> Unit
) {
    val textColor = palette.panelText
    val backgroundColor = palette.panelSurface

    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textColor.copy(alpha = 0.8f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal
                ),
                color = textColor,
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // Sync Controls
            if (isPresenter) {
                // Presenter UI
                TextButton(
                    onClick = onPresenterToggle,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isPresenterSyncing) Color(0xFFFF5252) else palette.panelMutedText
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast, 
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPresenterSyncing) "结束同屏" else "发起同屏")
                }
            } else {
                // Attendee UI
                // Pulse Animation if Sync Available and Not Following
                 val infiniteTransition = rememberInfiniteTransition()
                 val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isSyncActive && !isFollowing) 0.5f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                
                TextButton(
                    onClick = onFollowToggle,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isFollowing) Color(0xFF4CAF50) else palette.panelMutedText
                    ),
                    modifier = Modifier.alpha(if(isSyncActive && !isFollowing) pulseAlpha else 1f)
                ) {
                     Icon(
                        imageVector = if(isFollowing) Icons.Default.Link else Icons.Default.LinkOff, 
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isFollowing) "退出跟随" else "跟随阅读")
                }
            }
        }
    }
}

@Composable
private fun FloatingControlCapsule(
    currentPage: Int,
    totalPages: Int,
    isEditing: Boolean,
    isAnnotationEnabled: Boolean,
    palette: ReaderChromePalette,
    isPaperBackground: Boolean,
    onTocClick: () -> Unit,
    onGridClick: () -> Unit,
    onPenClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Surface(
            color = palette.panelSurface,
            shape = ReaderControlCapsuleShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .height(64.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = ReaderControlCapsuleShape,
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                    clip = false
                )
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left: Progress
                Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Medium
                    ),
                    color = palette.panelMutedText,
                    modifier = Modifier.padding(end = 16.dp)
                )

                // Vertical Divider
                VerticalDivider(modifier = Modifier.height(20.dp), color = palette.divider)

                // Right: Icons Container
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    // 1. TOC (List)
                    IconButton(onClick = onTocClick) {
                        Icon(Icons.Default.Menu, "TOC", tint = palette.panelMutedText)
                    }

                    // 2. Thumbnails (Grid)
                    IconButton(onClick = onGridClick) {
                        Icon(Icons.Default.Apps, "Thumbnails", tint = palette.panelMutedText)
                    }

                    // 3. Annotation (Pen)
                    IconButton(
                        onClick = onPenClick,
                        enabled = isAnnotationEnabled
                    ) {
                        val tint = if (isEditing) MaterialTheme.colorScheme.primary else palette.panelMutedText
                        Icon(
                            Icons.Default.Edit,
                            "Annotate",
                            tint = if (isAnnotationEnabled) tint else palette.panelMutedText.copy(alpha = 0.35f)
                        )
                    }

                    // 4. Reading Background
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "切换阅读背景",
                            tint = if (isPaperBackground) MaterialTheme.colorScheme.primary else palette.panelMutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnailGrid(
    file: File,
    palette: ReaderChromePalette,
    currentPage: Int,
    onPageClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { DocumentThumbnailRepository.getInstance(context) }
    val rendererHolder = remember(file) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor to PdfRenderer(descriptor)
        } catch (e: Exception) {
            null
        }
    }

    val descriptor = rendererHolder?.first ?: return
    val renderer = rendererHolder.second

    DisposableEffect(file) {
        onDispose {
            renderer.close()
            descriptor.close()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(renderer.pageCount) { pageIndex ->
             PdfPageThumbnail(
                 filePath = file.absolutePath,
                 repository = repository,
                 renderer = renderer,
                 pageIndex = pageIndex,
                 palette = palette,
                 isSelected = pageIndex == currentPage,
                 onClick = { onPageClick(pageIndex) }
             )
        }
    }
}

@Composable
private fun PdfPageThumbnail(
    filePath: String,
    repository: DocumentThumbnailRepository,
    renderer: PdfRenderer,
    pageIndex: Int,
    palette: ReaderChromePalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val targetWidthPx = with(density) { 120.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, filePath, pageIndex, targetWidthPx, repository, renderer) {
        value = repository.getPdfPageThumbnail(
            filePath = filePath,
            pageIndex = pageIndex,
            widthPx = targetWidthPx,
            sharedRenderer = renderer
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .border(
                    width = if(isSelected) 3.dp else 0.dp, 
                    color = if(isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, 
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(2.dp)
        ) {
            if(bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(), 
                    contentDescription = null, 
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
                )
            } else {
                Box(modifier = Modifier.height(140.dp).fillMaxWidth().background(Color.LightGray))
            }
        }
        Text(
            "${pageIndex + 1}", 
            style = MaterialTheme.typography.labelSmall, 
            color = palette.panelMutedText
        )
    }
}

// Helper
fun flattenBookmarks(bookmarks: List<Bookmark>, depth: Int = 0): List<Pair<Int, Bookmark>> {
    val result = mutableListOf<Pair<Int, Bookmark>>()
    for (bookmark in bookmarks) {
        result.add(depth to bookmark)
        if (bookmark.hasChildren()) {
            result.addAll(flattenBookmarks(bookmark.children, depth + 1))
        }
    }
    return result
}
