package com.example.paperlessmeeting.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import androidx.compose.animation.*
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
import com.example.paperlessmeeting.ui.screens.reader.AnnotationLine
import com.example.paperlessmeeting.ui.screens.reader.PointFCompat
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
    navController: NavController,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(downloadUrl) {
        viewModel.loadDocument(downloadUrl, fileName)
    }

    val uiState by viewModel.uiState.collectAsState()
    val annotations by viewModel.annotations.collectAsState()

    // --- State Management ---
    var showOverlay by remember { mutableStateOf(true) }
    var isNightMode by remember { mutableStateOf(false) }
    var isAnnotationMode by remember { mutableStateOf(false) }
    var currentStrokeColor by remember { mutableIntStateOf(android.graphics.Color.RED) }
    var isHorizontalScroll by remember { mutableStateOf(false) } // Default Vertical
    
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isProgrammaticScroll by remember { mutableStateOf(true) } // Prevent scroll fighting

    // TOC & Thumbnails
    var tocList by remember { mutableStateOf<List<Pair<Int, Bookmark>>>(emptyList()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showThumbnailSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Utils
    val cleanTitle = remember(fileName) { fileName.substringBeforeLast(".") } // Removing Extension
    val context = LocalContext.current

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
                is ReaderUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("无法加载文档", color = InkText)
                        Text(state.message, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                    }
                }
                is ReaderUiState.Ready -> {
                    // 1. PDF Content
                    PDFViewerContent(
                        file = state.file,
                        isNightMode = isNightMode,
                        isHorizontalScroll = isHorizontalScroll,
                        isAnnotationMode = isAnnotationMode,
                        currentPage = currentPage,
                        isProgrammaticScroll = isProgrammaticScroll,
                        currentStrokeColor = currentStrokeColor,
                        annotations = annotations,
                        onPageChange = { page, count ->
                            isProgrammaticScroll = false // User scrolled
                            currentPage = page
                            totalPages = count
                        },
                        onTap = {
                            if (!isAnnotationMode) showOverlay = !showOverlay
                        },
                        onAnnotationAdded = { viewModel.addAnnotation(it) },
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
                            onBackClick = { navController.popBackStack() }
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
                            isAnnotationMode = isAnnotationMode,
                            isNightMode = isNightMode,
                            onTocClick = { scope.launch { drawerState.open() } }, // Open TOC
                            onGridClick = { showThumbnailSheet = true },
                            onPenClick = { isAnnotationMode = !isAnnotationMode },
                            onSettingsClick = { isNightMode = !isNightMode }
                        )
                    }

                    // 4. Annotation Palette (Top Right, vertical)
                    if (isAnnotationMode && showOverlay) {
                        AnnotationPalette(
                            currentStrokeColor = currentStrokeColor,
                            onColorChange = { currentStrokeColor = it },
                            onUndo = { viewModel.undoAnnotation() },
                            onSave = { viewModel.saveAnnotations(state.file) },
                            onClose = { 
                                isAnnotationMode = false
                                viewModel.saveAnnotations(state.file)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 90.dp, end = 24.dp)
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
    isAnnotationMode: Boolean,
    currentPage: Int,
    isProgrammaticScroll: Boolean, 
    currentStrokeColor: Int,
    annotations: List<AnnotationLine>, // Coordinates in PDF Page % (0-1)
    onPageChange: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onAnnotationAdded: (AnnotationLine) -> Unit,
    onLoadToc: (List<Pair<Int, Bookmark>>) -> Unit,
    modifier: Modifier
) {
    // Shared State for PDF View Ref
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    
    // Fix Stale Closure: Always access latest annotations in onDraw
    val currentAnnotations by rememberUpdatedState(annotations)
    
    // LAYER 2: Real-time Drawing State (Screen Coordinates)
    val currentStrokePath = remember { mutableStateListOf<Pair<Float, Float>>() }

    Box(modifier = modifier) {
        // -------------------------------------------------------------------------
        // LAYER 1: PDF View (Bottom) - Renders PDF content + Saved Annotations
        // -------------------------------------------------------------------------
        AndroidView(
            factory = { context ->
                PDFView(context, null).apply {
                    pdfViewRef = this
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(if(isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pdfView ->
                // Fix Flicker: Only reload if file/nightmode changes.
                // Switching annotation mode should NOT trigger reload.
                val configKey = "${file.absolutePath}_${isNightMode}" 
                
                // Note: We handle scroll direction changes separately if needed, 
                // but usually that requires a full reload. 
                // For now, minimal keys to prevent flicker.
                
                if (pdfView.tag != configKey) {
                    pdfView.tag = configKey
                    pdfView.fromFile(file)
                        .defaultPage(currentPage)
                        .enableSwipe(true) // Always enable swipe in View (we intercept touch above if needed)
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
                             onLoadToc(flattenBookmarks(pdfView.tableOfContents))
                             // Do not force jump on load to preserve state if just styling changed
                        }
                        .onDraw { canvas, pageWidth, pageHeight, pageIdx ->
                             // Render Saved Annotations (PDF Coordinates -> View Coordinates)
                             // Logic: The points are 0-1 relative to page.
                             // Canvas here is already transformed to the page's local space.
                             currentAnnotations.forEach { line ->
                                 if (line.pageIndex == pageIdx) {
                                     val paint = Paint().apply {
                                         color = line.color
                                         strokeWidth = line.strokeWidth
                                         style = Paint.Style.STROKE
                                         isAntiAlias = true
                                         strokeCap = Paint.Cap.ROUND
                                         strokeJoin = Paint.Join.ROUND
                                     }
                                     val path = Path()
                                     if (line.points.isNotEmpty()) {
                                         val start = line.points[0]
                                         path.moveTo(start.x * pageWidth, start.y * pageHeight)
                                         for (i in 1 until line.points.size) {
                                             val p = line.points[i]
                                             path.lineTo(p.x * pageWidth, p.y * pageHeight)
                                         }
                                         canvas.drawPath(path, paint)
                                     }
                                 }
                             }
                        }
                        .onTap { 
                            onTap()
                            true 
                        }
                        .load()
                }
            }
        )

        // Sync Jump Logic
        LaunchedEffect(currentPage, isProgrammaticScroll) {
             pdfViewRef?.let { v ->
                 if (isProgrammaticScroll && v.currentPage != currentPage) {
                     v.jumpTo(currentPage)
                 }
             }
        }

        // -------------------------------------------------------------------------
        // LAYER 2: Gesture Interceptor & Real-time Canvas (Top)
        // -------------------------------------------------------------------------
        if (isAnnotationMode) {
             // 1. Transparent Canvas for Drawing
             androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStrokePath.clear()
                                currentStrokePath.add(offset.x to offset.y)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStrokePath.add(change.position.x to change.position.y)
                            },
                            onDragEnd = {
                                // Convert Screen Coords -> PDF Page Coords
                                pdfViewRef?.let { v ->
                                    // Robustly determine which page received the stroke
                                    // Use the first point to identify the target page
                                    val startX = currentStrokePath.firstOrNull()?.first ?: 0f
                                    val startY = currentStrokePath.firstOrNull()?.second ?: 0f
                                    
                                    val pageResult = findPageAndMapPoint(v, startX, startY)
                                    
                                    if (pageResult != null) {
                                        val (targetPageIdx, _) = pageResult
                                        
                                        // Map all points to this target page
                                        val mappedPoints = currentStrokePath.mapNotNull { (sx, sy) ->
                                             // We force map to the identified page to keep the stroke continuous
                                             convertScreenPointToPdfPoint(v, sx, sy, targetPageIdx)
                                        }
                                        
                                        if (mappedPoints.isNotEmpty()) {
                                            val newLine = AnnotationLine(
                                                pageIndex = targetPageIdx,
                                                points = mappedPoints,
                                                color = currentStrokeColor,
                                                strokeWidth = 5f
                                            )
                                            onAnnotationAdded(newLine)
                                            currentStrokePath.clear()
                                            v.invalidate()
                                        }
                                    }
                                }
                            }
                        )
                    }
             ) {
                 // Draw the temporary stroke (Screen Coordinates)
                 if (currentStrokePath.isNotEmpty()) {
                     val path = androidx.compose.ui.graphics.Path().apply {
                         moveTo(currentStrokePath.first().first, currentStrokePath.first().second)
                         for (i in 1 until currentStrokePath.size) {
                             lineTo(currentStrokePath[i].first, currentStrokePath[i].second)
                         }
                     }
                     drawPath(
                         path = path,
                         color = Color(currentStrokeColor), // Compose Color
                         style = androidx.compose.ui.graphics.drawscope.Stroke(
                             width = 5.dp.toPx(),
                             cap = androidx.compose.ui.graphics.StrokeCap.Round,
                             join = androidx.compose.ui.graphics.StrokeJoin.Round
                         )
                     )
                 }
             }
        }
    }
}

// Result Wrapper
data class PageMapResult(val pageIndex: Int, val point: PointFCompat)

// Helper: Find which page is at the screen coordinate and map the point
fun findPageAndMapPoint(view: PDFView, screenX: Float, screenY: Float): PageMapResult? {
    val zoom = view.zoom
    val currentYOffset = view.currentYOffset // Typically negative
    val spacingPx = 10f * zoom // Spacing scales with zoom? usually yes in this lib.
    
    // Global Y in the Document View (from top of Page 0)
    // view (0,0) is at (currentXOffset, currentYOffset) relative to Document (0,0)
    // So ScreenY = DocY * Zoom + currentYOffset
    // DocY * Zoom = ScreenY - currentYOffset
    // We work in "Zoomed Document Space" (View Pixels relative to Doc Top)
    val touchYInDoc = screenY - currentYOffset
    
    var accumulatedHeight = 0f
    
    // Optimization: Check visible range instead of 0..count
    // But safely, 0..count is fine for <100 pages.
    for (i in 0 until view.pageCount) {
        val pageSize = view.getPageSize(i) ?: continue
        val pageH = pageSize.height.toFloat() * zoom
        val pageW = pageSize.width.toFloat() * zoom
        
        // Check if Y falls within this page
        // Page Interval: [accumulatedHeight, accumulatedHeight + pageH]
        if (touchYInDoc >= accumulatedHeight && touchYInDoc <= accumulatedHeight + pageH) {
             // Found the page!
             // Map X and Y
             val offsetX = (screenX - view.currentXOffset) // Global X in View Pixels
             // Note: For X, we assume single column, or we just normalize relative to page width
             
             val localY = touchYInDoc - accumulatedHeight
             
             return PageMapResult(
                 pageIndex = i,
                 point = PointFCompat(
                     x = (offsetX / pageW).coerceIn(0f, 1f),
                     y = (localY / pageH).coerceIn(0f, 1f)
                 )
             )
        }
        
        accumulatedHeight += pageH + spacingPx
    }
    return null
}

// Helper: Convert Screen (View) Coordinates to PDF Page Normalized Coordinates (0-1)
// Heavily used for mapping the rest of the stroke once page is found
fun convertScreenPointToPdfPoint(
    view: PDFView, 
    screenX: Float, 
    screenY: Float, 
    pageIndex: Int
): PointFCompat? {
    val zoom = view.zoom
    val currentYOffset = view.currentYOffset
    val spacingPx = 10f * zoom
    
    // Recalculate Offset for this specific page
    // (Inefficient but robust state-less approach)
    var accumulatedHeight = 0f
    for (i in 0 until pageIndex) {
        val size = view.getPageSize(i)
        if (size != null) {
            accumulatedHeight += (size.height.toFloat() * zoom) + spacingPx
        }
    }
    
    val touchYInDoc = screenY - currentYOffset
    val pageH = (view.getPageSize(pageIndex)?.height ?: 0).toFloat() * zoom
    val pageW = (view.getPageSize(pageIndex)?.width ?: 0).toFloat() * zoom
    
    if (pageW <= 0f || pageH <= 0f) return null
    
    val localY = touchYInDoc - accumulatedHeight
    val offsetX = screenX - view.currentXOffset
    
    return PointFCompat(
        x = (offsetX / pageW).coerceIn(0f, 1f),
        y = (localY / pageH).coerceIn(0f, 1f)
    )
}

@Composable
fun MinimalistTopBar(
    title: String,
    isNightMode: Boolean,
    onBackClick: () -> Unit
) {
    val textColor = if(isNightMode) Color.White else InkText
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
    }
}

@Composable
fun FloatingControlCapsule(
    currentPage: Int,
    totalPages: Int,
    isAnnotationMode: Boolean,
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
            .widthIn(min = 300.dp, max = 400.dp)
            .shadow(16.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                IconButton(onClick = onPenClick) {
                    val tint = if(isAnnotationMode) MaterialTheme.colorScheme.primary else IconGrey
                    Icon(Icons.Default.Edit, "Annotate", tint = tint)
                }

                // 4. Night Mode / Settings
                IconButton(onClick = onSettingsClick) {
                    // Using a simple Moon/Sun logic indicator could be better, but sticking to icons
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
fun AnnotationPalette(
    currentStrokeColor: Int,
    onColorChange: (Int) -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.width(56.dp),
        shadowElevation = 6.dp,
        color = FloatingSurface,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Color Pickers
            val colors = listOf(Color.Red, Color.Blue, Color.Black)
            colors.forEach { color ->
                val androidColor = color.toArgb()
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color, CircleShape)
                        .clickable { onColorChange(androidColor) }
                        .border(
                            if (currentStrokeColor == androidColor) 2.dp else 0.dp,
                            IconGrey,
                            CircleShape
                        )
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.LightGray)

            // Actions
            IconButton(onClick = onUndo) {
                Icon(Icons.Default.Refresh, "Undo", tint = IconGrey)
            }
            
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, "Done", tint = MaterialTheme.colorScheme.primary)
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
