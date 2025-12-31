package com.example.paperlessmeeting.ui.screens.reader

data class PointFCompat(
    val x: Float,
    val y: Float
)

data class AnnotationLine(
    val pageIndex: Int,
    val points: List<PointFCompat>,
    val color: Int, // ARGB
    val strokeWidth: Float
)

data class DocumentAnnotations(
    val fileName: String,
    val lines: List<AnnotationLine>
)
