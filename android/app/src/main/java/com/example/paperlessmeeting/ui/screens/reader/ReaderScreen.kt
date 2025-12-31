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
    isProgrammaticScroll: Boolean, // New Parameter
    currentStrokeColor: Int,
    annotations: List<AnnotationLine>,
    onPageChange: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onAnnotationAdded: (AnnotationLine) -> Unit,
    onLoadToc: (List<Pair<Int, Bookmark>>) -> Unit,
    modifier: Modifier
) {
    // Shared State for PDF View Ref
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    // Temporary points for current stroke
    val currentPoints = remember { mutableStateListOf<PointFCompat>() }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                PDFView(context, null).apply {
                    pdfViewRef = this
                    // Minimalist default styling
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null) // Hardware Acceleration
                    setBackgroundColor(if(isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pdfView ->
                // Configuration Key Logic to prevent unnecessary reloads
                val configKey = "${file.absolutePath}_${isNightMode}_${isHorizontalScroll}_${isAnnotationMode}"
                
                if (pdfView.tag != configKey) {
                    pdfView.tag = configKey
                    pdfView.fromFile(file)
                        .defaultPage(currentPage)
                        .enableSwipe(!isAnnotationMode)
                        .swipeHorizontal(isHorizontalScroll)
                        .pageSnap(false) // Disable snap for smooth scrolling
                        .autoSpacing(false) // Disable auto spacing to prevent muddy scroll
                        .pageFling(true)
                        .fitEachPage(false) // Allow free scrolling
                        .nightMode(isNightMode)
                        .enableAnnotationRendering(true)
                        .enableAntialiasing(true) // Ensure text clarity as requested
                        .spacing(10)
                        .onPageChange { page, count -> 
                             if (page != currentPage) {
                                 onPageChange(page, count) 
                             }
                        }
                        .onLoad { nbPages ->
                             onLoadToc(flattenBookmarks(pdfView.tableOfContents))
                             onPageChange(pdfView.currentPage, nbPages)
                        }
                        .onDraw { canvas, pageWidth, pageHeight, pageIdx ->
                             // ... (Drawing logic remains same, implicit here or preserved)
                             // Optimization: Moved Paint/Path allocs out where possible, or use simplified logic
                             // 1. Draw Saved Annotations
                             annotations.forEach { line ->
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
                             // 2. Draw Live Stroke
                             if (isAnnotationMode && currentPoints.isNotEmpty() && currentPage == pageIdx) {
                                  val paint = Paint().apply {
                                      color = currentStrokeColor
                                      strokeWidth = 5f
                                      style = Paint.Style.STROKE
                                      isAntiAlias = true
                                      strokeCap = Paint.Cap.ROUND
                                      strokeJoin = Paint.Join.ROUND
                                  }
                                  val path = Path()
                                  val start = currentPoints[0]
                                  path.moveTo(start.x * pageWidth, start.y * pageHeight)
                                  for (i in 1 until currentPoints.size) {
                                      val p = currentPoints[i]
                                      path.lineTo(p.x * pageWidth, p.y * pageHeight)
                                  }
                                  canvas.drawPath(path, paint)
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

        // Sync Jump Logic - Moved via LaunchedEffect to avoid 'update' loop conflicts
        // Sync Jump Logic - Only jump if the change was programmatic (TOC, Slider, etc.)
        LaunchedEffect(currentPage, isProgrammaticScroll) {
             pdfViewRef?.let { v ->
                 if (isProgrammaticScroll && v.currentPage != currentPage) {
                     v.jumpTo(currentPage)
                 }
             }
        }



        // Overlay for Drawing Input
        if (isAnnotationMode) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        val v = pdfViewRef ?: return@pointerInteropFilter false
                        val viewWidth = v.width.toFloat()
                        val viewHeight = v.height.toFloat()
                        val pageSize = v.getPageSize(currentPage)
                        
                        // Only draw if page size is valid
                        if (pageSize != null && pageSize.width > 0) {
                             val pageW = pageSize.width.toFloat()
                             val pageH = pageSize.height.toFloat()
                             
                             // Calculate Aspect Fit Projection
                             val viewRatio = viewWidth / viewHeight
                             val pageRatio = pageW / pageH
                             var renderedW = viewWidth
                             var renderedH = viewHeight
                             var offsetX = 0f
                             var offsetY = 0f

                             if (pageRatio > viewRatio) {
                                  renderedH = viewWidth / pageRatio
                                  offsetY = (viewHeight - renderedH) / 2f
                             } else {
                                  renderedW = viewHeight * pageRatio
                                  offsetX = (viewWidth - renderedW) / 2f
                             }

                             // Normalize Touch
                             val localX = event.x - offsetX
                             val localY = event.y - offsetY
                             val xRatio = (localX / renderedW).coerceIn(0f, 1f)
                             val yRatio = (localY / renderedH).coerceIn(0f, 1f)

                             when (event.action) {
                                 MotionEvent.ACTION_DOWN -> {
                                     currentPoints.clear()
                                     currentPoints.add(PointFCompat(xRatio, yRatio))
                                     v.invalidate()
                                     true
                                 }
                                 MotionEvent.ACTION_MOVE -> {
                                     currentPoints.add(PointFCompat(xRatio, yRatio))
                                     v.invalidate()
                                     true
                                 }
                                 MotionEvent.ACTION_UP -> {
                                     currentPoints.add(PointFCompat(xRatio, yRatio))
                                     val newLine = AnnotationLine(
                                         pageIndex = currentPage,
                                         points = currentPoints.toList(),
                                         color = currentStrokeColor,
                                         strokeWidth = 5f
                                     )
                                     onAnnotationAdded(newLine)
                                     currentPoints.clear()
                                     v.invalidate()
                                     true
                                 }
                                 else -> true
                             }
                        } else false
                    }
             )
        }
    }
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
