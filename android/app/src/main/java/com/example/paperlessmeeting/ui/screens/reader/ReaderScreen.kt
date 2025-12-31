package com.example.paperlessmeeting.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.toArgb
import com.example.paperlessmeeting.ui.screens.reader.AnnotationLine
import com.example.paperlessmeeting.ui.screens.reader.PointFCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.ui.screens.reader.ReaderViewModel
import com.example.paperlessmeeting.ui.screens.reader.ReaderUiState
import com.github.barteksc.pdfviewer.PDFView
import com.shockwave.pdfium.PdfDocument.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showOverlay by remember { mutableStateOf(true) }
    var isNightMode by remember { mutableStateOf(false) }
    var isAnnotationMode by remember { mutableStateOf(false) }
    var currentStrokeColor by remember { mutableIntStateOf(android.graphics.Color.RED) }
    var isHorizontalScroll by remember { mutableStateOf(false) } // Default to Vertical
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }




        var currentPage by remember { mutableIntStateOf(0) }
        var totalPages by remember { mutableIntStateOf(0) }

        // TOC State
        var tocList by remember { mutableStateOf<List<Pair<Int, Bookmark>>>(emptyList()) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Thumbnails State
        var showThumbnailSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        // Full Screen Logic
        val context = LocalContext.current
        LaunchedEffect(showOverlay) {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (showOverlay) {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // Restore on exit
        DisposableEffect(Unit) {
            onDispose {
                val window = (context as? Activity)?.window
                if (window != null) {
                    val insetsController =
                        WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // TOC Drawer
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isAnnotationMode, // Disable drawer swipe when drawing
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        "目录大纲",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    HorizontalDivider()
                    if (tocList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn {
                            items(tocList) { (depth, bookmark) ->
                                NavigationDrawerItem(
                                    label = {
                                        Row {
                                            Spacer(modifier = Modifier.width((depth * 16).dp))
                                            Text(
                                                bookmark.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    selected = false,
                                    onClick = {
                                        currentPage = bookmark.pageIdx.toInt()
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (isNightMode) Color.Black else Color.White,
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
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                // TOC Button
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = "TOC",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            if (showOverlay) innerPadding else androidx.compose.foundation.layout.PaddingValues(
                                0.dp
                            )
                        )
                        .background(if (isNightMode) Color.Black else Color.White)
                ) {
                    when (val state = uiState) {
                        is ReaderUiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        is ReaderUiState.Error -> {
                            Text(
                                text = "无法加载文档: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        is ReaderUiState.Ready -> {

                            // Drawing State
                            val currentPoints = remember { mutableStateListOf<PointFCompat>() }

                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { context ->
                                        PDFView(context, null).apply {
                                            pdfViewRef = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { pdfView ->
                                        // Optimization: Only reload if configuration changes
                                        // Construct a config key string
                                        val configKey =
                                            "${state.file.absolutePath}_${isNightMode}_${isHorizontalScroll}_${isAnnotationMode}"

                                        if (pdfView.tag != configKey) {
                                            pdfView.tag = configKey

                                            pdfView.fromFile(state.file)
                                                .defaultPage(currentPage)
                                                .enableSwipe(!isAnnotationMode)
                                                .swipeHorizontal(isHorizontalScroll)
                                                .pageSnap(true)
                                                .autoSpacing(true)
                                                .pageFling(true)
                                                .fitEachPage(true)
                                                .nightMode(isNightMode)
                                                .enableAnnotationRendering(true)
                                                .onPageChange { page: Int, pageCount: Int ->
                                                    if (currentPage != page) {
                                                        currentPage = page
                                                    }
                                                    totalPages = pageCount
                                                }
                                                .onLoad { nbPages ->
                                                    totalPages = nbPages
                                                    val rawToc = pdfView.tableOfContents
                                                    tocList = flattenBookmarks(rawToc)
                                                    viewModel.loadAnnotations(state.file)
                                                }
                                                .onDraw { canvas, pageWidth, pageHeight, pageIdx ->
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
                                                            line.points.forEachIndexed { index, point ->
                                                                val x = point.x * pageWidth
                                                                val y = point.y * pageHeight
                                                                if (index == 0) path.moveTo(
                                                                    x,
                                                                    y
                                                                ) else path.lineTo(x, y)
                                                            }
                                                            canvas.drawPath(path, paint)
                                                        }
                                                    }

                                                    // 2. Draw Current Stroke (Real-time feedback)
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
                                                        currentPoints.forEachIndexed { index, point ->
                                                            val x = point.x * pageWidth
                                                            val y = point.y * pageHeight
                                                            if (index == 0) path.moveTo(
                                                                x,
                                                                y
                                                            ) else path.lineTo(x, y)
                                                        }
                                                        canvas.drawPath(path, paint)
                                                    }
                                                }
                                                .onTap { e ->
                                                    if (!isAnnotationMode) {
                                                        showOverlay = !showOverlay
                                                    }
                                                    true
                                                }
                                                .load()
                                        }

                                        // Jump logic independent of reload
                                        if (pdfView.currentPage != currentPage) {
                                            pdfView.jumpTo(currentPage)
                                        }

                                    }
                                )

                                // Touch Overlay for Drawing
                                if (isAnnotationMode) {
                                    @OptIn(ExperimentalComposeUiApi::class)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInteropFilter { event ->
                                                val v = pdfViewRef ?: return@pointerInteropFilter false
                                                val viewWidth = v.width.toFloat()
                                                val viewHeight = v.height.toFloat()

                                                // Get current page size to determine displayed rect
                                                // This assumes 'pageSnap(true)' and 'fitEachPage(true)' where the page is centered.
                                                val pageSize = v.getPageSize(currentPage)
                                                if (pageSize != null) {
                                                    val pageW = pageSize.width.toFloat()
                                                    val pageH = pageSize.height.toFloat()

                                                    if (pageW > 0 && pageH > 0) {
                                                        // Calculate displayed dimensions preserving aspect ratio
                                                        val viewRatio = viewWidth / viewHeight
                                                        val pageRatio = pageW / pageH

                                                        var renderedW = viewWidth
                                                        var renderedH = viewHeight
                                                        var offsetX = 0f
                                                        var offsetY = 0f

                                                        if (pageRatio > viewRatio) {
                                                            // Page is wider than view: Fit Width
                                                            renderedW = viewWidth
                                                            renderedH = viewWidth / pageRatio
                                                            offsetY = (viewHeight - renderedH) / 2f
                                                        } else {
                                                            // Page is taller than view: Fit Height
                                                            renderedH = viewHeight
                                                            renderedW = viewHeight * pageRatio
                                                            offsetX = (viewWidth - renderedW) / 2f
                                                        }

                                                        // Normalize event coordinates relative to the PAGE rect
                                                        val localX = event.x - offsetX
                                                        val localY = event.y - offsetY

                                                        // Ratios 0..1 relative to page content
                                                        val xRatio =
                                                            (localX / renderedW).coerceIn(0f, 1f)
                                                        val yRatio =
                                                            (localY / renderedH).coerceIn(0f, 1f)

                                                        when (event.action) {
                                                            MotionEvent.ACTION_DOWN -> {
                                                                currentPoints.clear()
                                                                currentPoints.add(
                                                                    PointFCompat(
                                                                        xRatio,
                                                                        yRatio
                                                                    )
                                                                )
                                                                v.invalidate() // Redraw to show live stroke
                                                                true
                                                            }

                                                            MotionEvent.ACTION_MOVE -> {
                                                                currentPoints.add(
                                                                    PointFCompat(
                                                                        xRatio,
                                                                        yRatio
                                                                    )
                                                                )
                                                                v.invalidate()
                                                                true
                                                            }

                                                            MotionEvent.ACTION_UP -> {
                                                                currentPoints.add(
                                                                    PointFCompat(
                                                                        xRatio,
                                                                        yRatio
                                                                    )
                                                                )
                                                                val newLine = AnnotationLine(
                                                                    pageIndex = currentPage,
                                                                    points = currentPoints.toList(),
                                                                    color = currentStrokeColor,
                                                                    strokeWidth = 5f
                                                                )
                                                                viewModel.addAnnotation(newLine)
                                                                currentPoints.clear()
                                                                v.invalidate()
                                                                true
                                                            }

                                                            else -> true
                                                        }
                                                    } else false
                                                } else false
                                            }
                                    )
                                }

                                // Annotation Tools Overlay
                                if (isAnnotationMode && showOverlay) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 80.dp) // Below TopAppBar
                                            .background(
                                                Color.White,
                                                shape = MaterialTheme.shapes.medium
                                            )
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Colors
                                        val colors = listOf(Color.Red, Color.Blue, Color.Black)
                                        colors.forEach { color ->
                                            val androidColor = android.graphics.Color.parseColor(
                                                "#" + Integer.toHexString(color.toArgb())
                                                    .substring(2)
                                            ) // Simple hack or just use logic
                                            // Better:
                                            val curInt = android.graphics.Color.argb(
                                                (color.alpha * 255).toInt(),
                                                (color.red * 255).toInt(),
                                                (color.green * 255).toInt(),
                                                (color.blue * 255).toInt()
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        color,
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                    )
                                                    .clickable { currentStrokeColor = curInt }
                                                    .then(
                                                        if (currentStrokeColor == curInt) Modifier.border(
                                                            2.dp,
                                                            Color.Gray,
                                                            androidx.compose.foundation.shape.CircleShape
                                                        ) else Modifier
                                                    )
                                            )
                                        }

                                        // Undo
                                        IconButton(onClick = { viewModel.undoAnnotation() }) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Undo"
                                            ) // ArrowBack or Refresh as Undo proxy
                                        }

                                        // Save (Persistence) - Actually implementation saves periodically, but button gives reassurance
                                        IconButton(onClick = {
                                            viewModel.saveAnnotations(state.file)
                                            // Toast?
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Save")
                                        }

                                        // Close
                                        IconButton(onClick = {
                                            isAnnotationMode = false
                                            viewModel.saveAnnotations(state.file)
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Exit")
                                        }
                                    }
                                }
                            }


                            // Overlay Bottom Bar
                            AnimatedVisibility(
                                visible = showOverlay,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${currentPage + 1} / $totalPages",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.width(60.dp)
                                        )

                                        Slider(
                                            value = currentPage.toFloat(),
                                            onValueChange = { currentPage = it.toInt() },
                                            valueRange = 0f..maxOf(0f, (totalPages - 1).toFloat()),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.White,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.Gray
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))


                                        // Annotation Mode Button
                                        IconButton(onClick = {
                                            isAnnotationMode = !isAnnotationMode
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Annotate",
                                                tint = if (isAnnotationMode) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Scroll Direction Toggle
                                        IconButton(onClick = {
                                            isHorizontalScroll = !isHorizontalScroll
                                        }) {
                                            Icon(
                                                if (isHorizontalScroll) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Scroll Direction",
                                                tint = Color.White
                                            )
                                        }

                                        // Thumbnail Grid Button
                                        IconButton(onClick = { showThumbnailSheet = true }) {
                                            Icon(
                                                Icons.Default.List,
                                                contentDescription = "Thumbnails",
                                                tint = Color.White
                                            )
                                        }

                                        // Night Mode Toggle
                                        IconButton(onClick = { isNightMode = !isNightMode }) {
                                            Text(
                                                text = if (isNightMode) "☀" else "☾",
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }
                                }
                            }

                            // Thumbnail Bottom Sheet
                            if (showThumbnailSheet) {
                                ModalBottomSheet(
                                    onDismissRequest = { showThumbnailSheet = false },
                                    sheetState = sheetState,
                                    containerColor = if (isNightMode) Color.DarkGray else Color.White
                                ) {
                                    PdfThumbnailGrid(
                                        file = state.file,
                                        isNightMode = isNightMode,
                                        currentPage = currentPage,
                                        onPageClick = { page ->
                                            currentPage = page
                                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                showThumbnailSheet = false
                                            }
                                        }
                                    )
                                }
                            }
                        } // End ReaderUiState.Ready
                        else -> {}
                    } // End when
                }
            }
        }
    }


    @Composable
    fun PdfThumbnailGrid(
        file: java.io.File,
        isNightMode: Boolean,
        currentPage: Int,
        onPageClick: (Int) -> Unit
    ) {
        // Manage PdfRenderer Lifecycle
        val renderer = remember(file) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor)
        }

        DisposableEffect(file) {
            onDispose {
                renderer.close()
            }
        }

        val pageCount = renderer.pageCount

        LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(pageCount) { pageIndex ->
                val isSelected = pageIndex == currentPage
                PdfPageThumbnail(
                    renderer = renderer,
                    pageIndex = pageIndex,
                    isNightMode = isNightMode,
                    isSelected = isSelected,
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
                try {
                    // Must synchronize as PdfRenderer is not thread-safe and we might have multiple items requesting
                    synchronized(renderer) {
                        val page = renderer.openPage(pageIndex)
                        // Render at low res for thumbnail (e.g. width 300)
                        val width = 300
                        val height = (width * page.height / page.width)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // White background (or Invert for night mode ideally, but simple white is safer)
                        // For transparency, we might want to fill WHITE first.
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)

                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(4.dp)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color.Gray)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isNightMode) Color.White else Color.Black
            )
        }
    }

    // Helper to flatten recursive bookmarks into a list with depth info for indentation
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
