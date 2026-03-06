package com.example.paperlessmeeting.ui.screens.reader

data class NormalizedPoint(val x: Float, val y: Float)

data class AnnotationStroke(
    val points: List<NormalizedPoint>,
    val color: Int,
    val strokeWidth: Float = 5f,
    val isHighlighter: Boolean = false
)

data class PageAnnotations(
    val pageIndex: Int,
    val strokes: List<AnnotationStroke> = emptyList()
)
