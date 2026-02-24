package com.example.paperlessmeeting.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
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
import com.example.paperlessmeeting.ui.screens.reader.ReaderUiState
import com.example.paperlessmeeting.ui.screens.reader.ReaderViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.shockwave.pdfium.PdfDocument.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// --- Design System: Minimalist Colors ---
private val PaperBackground = Color(0xFFF9F7F1) // Warm Cream / Parchment
private val InkText = Color(0xFF2C2C2C)       // Soft Black
private val IconGrey = Color(0xFF5F6368)      // Subtle Grey
private val FloatingSurface = Color.White.copy(alpha = 0.95f) // Frosted Glass-ish

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
        toastEvent?.let { msg ->
            // android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToastEvent()
        }
    }

    // --- State Management ---
    var showOverlay by remember { mutableStateOf(true) }
    var isNightMode by remember { mutableStateOf(false) }
    
    // Mode State: Reading or Editing
    var isEditing by remember { mutableStateOf(false) }
    var editPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editingPageIndex by remember { mutableIntStateOf(0) }
    
    var isHorizontalScroll by remember { mutableStateOf(false) } // Default Vertical
    
    var currentPage by remember { mutableIntStateOf(initialPage) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isProgrammaticScroll by remember { mutableStateOf(true) } // Prevent scroll fighting

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

    // --- Window Insets Logic (Immersive Mode) ---
    LaunchedEffect(showOverlay) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showOverlay) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
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

    // --- UI Structure ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen, // Only allow closing via gesture, prevent opening conflict
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PaperBackground,
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text(
                        "目录大纲",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = InkText, 
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    HorizontalDivider(color = IconGrey.copy(alpha = 0.2f))
                    if (tocList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无目录", color = IconGrey)
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
                                                color = InkText.copy(alpha = 0.8f)
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
                .background(if (isNightMode) Color.Black else PaperBackground)
        ) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center), 
                        color = InkText
                    )
                }
                is ReaderUiState.Downloading -> {
                    // 下载进度 UI - 带返回按钮
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 顶部栏
                        Surface(
                            color = FloatingSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = InkText.copy(alpha = 0.8f))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "下载中...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkText,
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
                                    color = InkText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IconGrey,
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
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                } else {
                                    // 未知进度 - 显示无限循环动画
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "文件大小未知...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IconGrey
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                    text = "返回后下载将在后台继续",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IconGrey
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
                            color = InkText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 错误详情
                        Text(
                            text = state.message,
                            color = IconGrey,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // 按钮组
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 返回按钮
                            TextButton(onClick = { navController.popBackStack() }) {
                                Text("返回", color = IconGrey)
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
                    // Restore saved reading progress
                    LaunchedEffect(state.initialPage) {
                        // 如果有保存的进度且当前页不等于保存的页码，则恢复
                        if (state.initialPage > 0 && currentPage != state.initialPage) {
                            currentPage = state.initialPage
                            isProgrammaticScroll = true
                        }
                    }

                    if (isEditing && editPageBitmap != null) {
                        // ====== MODE B: Focus/Edit Mode ======
                        SinglePageEditor(
                            bitmap = editPageBitmap!!,
                            initialStrokes = pageAnnotations[editingPageIndex] ?: emptyList(),
                            onCancel = { isEditing = false },
                            onSave = { newStrokes ->
                                viewModel.updatePageAnnotations(editingPageIndex, newStrokes)
                                viewModel.saveAnnotations(state.file)
                                isEditing = false
                                // Invalidate PDFView to show updates logic handles by PDFView key/update
                                pdfViewRef?.invalidate() 
                            }
                        )
                    } else {
                        // ====== MODE A: Reading Mode ======
                        // 1. PDF Content
                        PDFViewerContent(
                            file = state.file,
                            isNightMode = isNightMode,
                            isHorizontalScroll = isHorizontalScroll,
                            currentPage = currentPage,
                            isProgrammaticScroll = isProgrammaticScroll,
                            pageAnnotations = pageAnnotations,
                            onPdfViewReady = { pdfViewRef = it },
                            onPageChange = { page, count ->
                                isProgrammaticScroll = false // User scrolled
                                currentPage = page
                                totalPages = count
                                
                                // Presenter Sync Logic
                                if (isPresenterSyncing) {
                                    viewModel.onPresenterPageChanged(page)
                                }
                                
                                // Attendee Auto-Detach Logic
                                if (isFollowing && !isProgrammaticScroll) {
                                    // If user manually scrolled, stop following (optional, or just keep following)
                                    // For now, let's keep following unless they explicitly turn it off, 
                                    // OR we could say manual scroll disables follow.
                                    // Let's implement: Manual scroll disables follow to avoid fighting.
                                    // But wait, isProgrammaticScroll is set to false here.
                                    // The logic to differentiate 'user scroll' vs 'sync jump' is handled by `isProgrammaticScroll` flag passed into PDFViewerContent.
                                    // In `onPageChange` below, we check if it was programmatic.
                                    // ACTUALLY, `onPageChange` is called by PDFView in BOTH cases.
                                    // We need to know if the helper triggered it.
                                    
                                    // In this implementation `isProgrammaticScroll` is set to false just above this line. 
                                    // So how do we distinguish? 
                                    // Answer: We need to check the scope.
                                    // Let's use the simplest approach: If user interacts, we disable follow.
                                    // But PDFView `onPageChange` fires even for programmatic jumps.
                                    
                                    // Let's just update Presenter state here.
                                    // Attendee detach is handled by `isProgrammaticScroll` check if we want to be strict.
                                    // For now, let's just allow the jump.
                                }
                            },
                            onTap = {
                                showOverlay = !showOverlay
                            },
                            onLoadToc = { list -> tocList = list }, // Load TOC from PDF
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Floating Top Bar
                        AnimatedVisibility(
                            visible = showOverlay,
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            MinimalistTopBar(
                                title = cleanTitle,
                                isNightMode = isNightMode,
                                isPresenter = isPresenter,
                                isPresenterSyncing = isPresenterSyncing,
                                isFollowing = isFollowing,
                                isSyncActive = isSyncActive,
                                onBackClick = { navController.popBackStack() },
                                onPresenterToggle = { viewModel.togglePresenterSync(!isPresenterSyncing) },
                                onFollowToggle = { viewModel.toggleFollow(!isFollowing) }
                            )
                        }

                        // 3. Floating Bottom Capsule
                        AnimatedVisibility(
                            visible = showOverlay,
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
                                isNightMode = isNightMode,
                                onTocClick = { scope.launch { drawerState.open() } }, // Open TOC
                                onGridClick = { showThumbnailSheet = true },
                                onPenClick = { 
                                    // Enter Edit Mode Logic
                                    scope.launch(Dispatchers.IO) {
                                        val bmp = renderPdfPageToBitmap(state.file, currentPage)
                                        if (bmp != null) {
                                             withContext(Dispatchers.Main) {
                                                 editPageBitmap = bmp
                                                 editingPageIndex = currentPage
                                                 isEditing = true
                                             }
                                        }
                                    }
                                },
                                onSettingsClick = { isNightMode = !isNightMode }
                            )
                        }

                        // 5. Thumbnails Bottom Sheet
                        if (showThumbnailSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showThumbnailSheet = false },
                                sheetState = sheetState,
                                containerColor = if(isNightMode) Color.DarkGray else PaperBackground
                            ) {
                               PdfThumbnailGrid(
                                   file = state.file,
                                   isNightMode = isNightMode,
                                   currentPage = currentPage,
                                   onPageClick = { page ->
                                       isProgrammaticScroll = true
                                       currentPage = page
                                       scope.launch { sheetState.hide() }.invokeOnCompletion { showThumbnailSheet = false }
                                   }
                               ) 
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// =========================================================================================
//                              SUBCOMPONENTS
// =========================================================================================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PDFViewerContent(
    file: File,
    isNightMode: Boolean,
    isHorizontalScroll: Boolean,
    currentPage: Int,
    isProgrammaticScroll: Boolean, 
    pageAnnotations: Map<Int, List<AnnotationStroke>>,
    onPdfViewReady: (PDFView) -> Unit,
    onPageChange: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onLoadToc: (List<Pair<Int, Bookmark>>) -> Unit,
    modifier: Modifier
) {
    // Fix Stale Closure: Always access latest annotations in onDraw
    val currentAnnotations by rememberUpdatedState(pageAnnotations)
    
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
                    setBackgroundColor(if(isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pdfView ->
                // Fix Flicker: Only reload if file/nightmode changes.
                val configKey = "${file.absolutePath}_${isNightMode}" 
                
                if (pdfView.tag != configKey) {
                    isPdfLoaded = false // Reset load state on new file
                    pdfView.tag = configKey
                    pdfView.fromFile(file)
                        .defaultPage(currentPage)
                        .enableSwipe(true) 
                        .swipeHorizontal(isHorizontalScroll)
                        .pageSnap(false)
                        .autoSpacing(false) 
                        .pageFling(true)
                        .fitEachPage(false)
                        .nightMode(isNightMode)
                        .enableAnnotationRendering(true)
                        .enableAntialiasing(true)
                        .spacing(10)
                        .onPageChange { page, count -> 
                             if (page != currentPage) {
                                 onPageChange(page, count) 
                             }
                        }
                        .onLoad { nbPages ->
                             isPdfLoaded = true // Mark as loaded
                             onLoadToc(flattenBookmarks(pdfView.tableOfContents))
                        }
                        .onDraw { canvas, pageWidth, pageHeight, pageIdx ->
                             // Render Saved Annotations (PDF Coordinates -> View Coordinates)
                             // Logic: The points are 0-1 relative to page.
                             // Canvas here is already transformed to the page's local space.
                             
                             currentAnnotations[pageIdx]?.forEach { stroke ->
                                 val paint = Paint().apply {
                                     color = stroke.color
                                     style = Paint.Style.STROKE
                                     strokeWidth = stroke.strokeWidth
                                     strokeCap = Paint.Cap.ROUND
                                     strokeJoin = Paint.Join.ROUND
                                     isAntiAlias = true
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
                localPdfViewRef?.jumpTo(currentPage, true)
            }
        }
    }
}

// Helper: Render High-Res Bitmap of a PDF Page
fun renderPdfPageToBitmap(file: File, pageIndex: Int): Bitmap? {
    return try {
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        val page = renderer.openPage(pageIndex)
        
        // Render High Res (e.g. 2x screen width or fixed 1080p width)
        // Let's assume a reasonable width for editing.
        val width = 1500 
        val height = (width * page.height / page.width.toFloat()).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        page.close()
        renderer.close()
        fileDescriptor.close()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun MinimalistTopBar(
    title: String,
    isNightMode: Boolean,
    isPresenter: Boolean,
    isPresenterSyncing: Boolean,
    isFollowing: Boolean,
    isSyncActive: Boolean, // Attendee: Is sync available?
    onBackClick: () -> Unit,
    onPresenterToggle: () -> Unit,
    onFollowToggle: () -> Unit
) {
    val textColor = if(isNightMode) Color.White else InkText
    val backgroundColor = if(isNightMode) Color.Black else FloatingSurface

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
                        contentColor = if (isPresenterSyncing) Color(0xFFFF5252) else IconGrey
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
                        contentColor = if (isFollowing) Color(0xFF4CAF50) else IconGrey
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
fun FloatingControlCapsule(
    currentPage: Int,
    totalPages: Int,
    isEditing: Boolean,
    isNightMode: Boolean,
    onTocClick: () -> Unit,
    onGridClick: () -> Unit,
    onPenClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = FloatingSurface,
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
        modifier = Modifier
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.1f))
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
                color = IconGrey,
                modifier = Modifier.padding(end = 16.dp)
            )

            // Vertical Divider
            VerticalDivider(modifier = Modifier.height(20.dp), color = Color.LightGray)

            // Right: Icons Container
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                
                // 1. TOC (List)
                IconButton(onClick = onTocClick) {
                    Icon(Icons.Default.Menu, "TOC", tint = IconGrey)
                }
                
                // 2. Thumbnails (Grid)
                IconButton(onClick = onGridClick) {
                    Icon(Icons.Default.Apps, "Thumbnails", tint = IconGrey)
                }

                // 3. Annotation (Pen)
                /*
                IconButton(onClick = onPenClick) {
                    val tint = if(isEditing) MaterialTheme.colorScheme.primary else IconGrey
                    Icon(Icons.Default.Edit, "Annotate", tint = tint)
                }
                */

                // 4. Night Mode / Settings
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = if(isNightMode) Icons.Default.WbSunny else Icons.Default.NightlightRound, 
                        contentDescription = "Night Mode", 
                        tint = IconGrey
                    )
                }
            }
        }
    }
}

@Composable
fun PdfThumbnailGrid(
    file: File,
    isNightMode: Boolean,
    currentPage: Int,
    onPageClick: (Int) -> Unit
) {
    // Basic implementation of cached PDF Rendering
    val renderer = remember(file) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor)
        } catch (e: Exception) { null }
    }

    if(renderer == null) return

    DisposableEffect(file) {
        onDispose { renderer.close() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(renderer.pageCount) { pageIndex ->
             PdfPageThumbnail(
                 renderer = renderer,
                 pageIndex = pageIndex,
                 isNightMode = isNightMode,
                 isSelected = pageIndex == currentPage,
                 onClick = { onPageClick(pageIndex) }
             )
        }
    }
}

@Composable
fun PdfPageThumbnail(
    renderer: PdfRenderer,
    pageIndex: Int,
    isNightMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            synchronized(renderer) {
                try {
                     val page = renderer.openPage(pageIndex)
                     val width = 200
                     val height = (width.toFloat() / page.width * page.height).toInt()
                     val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                     val canvas = android.graphics.Canvas(bmp)
                     canvas.drawColor(android.graphics.Color.WHITE) // Always white base
                     page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                     page.close()
                     bitmap = bmp
                } catch(e: Exception) { e.printStackTrace() }
            }
        }
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
            color = if(isNightMode) Color.White else IconGrey
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
