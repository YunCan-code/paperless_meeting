package com.example.paperlessmeeting.ui.components.image

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.example.paperlessmeeting.R
import com.example.paperlessmeeting.domain.model.MediaItem
import com.example.paperlessmeeting.domain.model.Meeting

enum class AppImageSlot {
    LoginPoster,
    MeetingHero,
    MeetingCard,
    MediaGrid,
    FullscreenPreview
}

enum class AppImageFallback {
    Meeting,
    Image,
    Video,
    Document,
    Poster
}

data class AppImageModel(
    val data: Any?,
    val diskCacheKey: String? = null,
    val memoryCacheKey: String? = null,
    val placeholderMemoryCacheKey: String? = null,
    @DrawableRes val fallbackResId: Int? = null,
    val contentScale: ContentScale,
    val crossfadeEnabled: Boolean,
    val crossfadeMillis: Int,
    val precision: Precision,
    val scale: Scale,
    val preferThumbnail: Boolean = false,
    val fallback: AppImageFallback,
    val allowHardware: Boolean = true,
    val description: String? = null
)

object MeetingImageResolver {

    fun loginPosterModel(
        posterUrl: String? = null,
        posterVersion: String? = null
    ): AppImageModel {
        val resolvedUrl = posterUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { appendVersionParam(it, posterVersion) }
        return AppImageModel(
            data = resolvedUrl ?: R.drawable.login_poster,
            diskCacheKey = resolvedUrl?.let { "login-poster:$it" },
            memoryCacheKey = resolvedUrl?.let { "login-poster:$it" } ?: "login-poster:local",
            fallbackResId = R.drawable.login_poster,
            contentScale = ContentScale.Crop,
            crossfadeEnabled = resolvedUrl != null,
            crossfadeMillis = if (resolvedUrl != null) 220 else 0,
            precision = if (resolvedUrl != null) Precision.INEXACT else Precision.EXACT,
            scale = Scale.FILL,
            fallback = AppImageFallback.Poster,
            description = "Login poster"
        )
    }

    private fun appendVersionParam(url: String, version: String?): String {
        if (version.isNullOrBlank()) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}v=$version"
    }

    fun resolve(meeting: Meeting, slot: AppImageSlot): AppImageModel {
        require(slot == AppImageSlot.MeetingCard || slot == AppImageSlot.MeetingHero) {
            "MeetingImageResolver only supports meeting image slots."
        }

        val primaryUrl = when (slot) {
            AppImageSlot.MeetingCard -> meeting.cardImageThumbUrl?.takeIf { it.isNotBlank() }
                ?: meeting.cardImageUrl?.takeIf { it.isNotBlank() }
            AppImageSlot.MeetingHero -> meeting.cardImageUrl?.takeIf { it.isNotBlank() }
                ?: meeting.cardImageThumbUrl?.takeIf { it.isNotBlank() }
            else -> null
        }
        val cardPlaceholderKey = meeting.cardImageThumbUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { meetingCardMemoryKey(meeting.id, it) }
            ?: meeting.cardImageUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { meetingCardMemoryKey(meeting.id, it) }

        return AppImageModel(
            data = primaryUrl,
            diskCacheKey = primaryUrl?.let { cacheKey(slot, meeting.id, it) },
            memoryCacheKey = primaryUrl?.let { cacheKey(slot, meeting.id, it) },
            placeholderMemoryCacheKey = if (slot == AppImageSlot.MeetingHero) {
                cardPlaceholderKey
            } else {
                null
            },
            contentScale = ContentScale.Crop,
            crossfadeEnabled = true,
            crossfadeMillis = if (slot == AppImageSlot.MeetingHero) 220 else 180,
            precision = if (slot == AppImageSlot.MeetingCard) Precision.INEXACT else Precision.EXACT,
            scale = Scale.FILL,
            preferThumbnail = slot == AppImageSlot.MeetingCard,
            fallback = AppImageFallback.Meeting,
            description = meeting.title
        )
    }

    fun meetingCardMemoryKey(meetingId: Int, imageUrl: String): String {
        return cacheKey(AppImageSlot.MeetingCard, meetingId, imageUrl)
    }

    private fun cacheKey(slot: AppImageSlot, id: Int, imageUrl: String): String {
        val prefix = when (slot) {
            AppImageSlot.MeetingCard -> "meeting-card"
            AppImageSlot.MeetingHero -> "meeting-hero"
            else -> "meeting-image"
        }
        return "$prefix:$id:$imageUrl"
    }
}

object MediaImageResolver {

    fun resolveGrid(item: MediaItem, staticBaseUrl: String): AppImageModel {
        val resolvedUrl = resolveStaticUrl(
            rawUrl = item.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: item.previewUrl?.takeIf { it.isNotBlank() },
            staticBaseUrl = staticBaseUrl
        )
        return AppImageModel(
            data = resolvedUrl,
            diskCacheKey = resolvedUrl?.let { "media-grid:${item.id}:$it" },
            memoryCacheKey = resolvedUrl?.let { "media-grid:${item.id}:$it" },
            contentScale = ContentScale.Crop,
            crossfadeEnabled = true,
            crossfadeMillis = 180,
            precision = Precision.INEXACT,
            scale = Scale.FILL,
            preferThumbnail = true,
            fallback = if (item.kind == "video") AppImageFallback.Video else AppImageFallback.Image,
            description = item.title
        )
    }

    fun resolveFullscreen(item: MediaItem, staticBaseUrl: String): AppImageModel {
        val resolvedUrl = resolveStaticUrl(
            rawUrl = item.previewUrl?.takeIf { it.isNotBlank() }
                ?: item.thumbnailUrl?.takeIf { it.isNotBlank() },
            staticBaseUrl = staticBaseUrl
        )
        return AppImageModel(
            data = resolvedUrl,
            diskCacheKey = resolvedUrl?.let { "media-preview:${item.id}:$it" },
            memoryCacheKey = resolvedUrl?.let { "media-preview:${item.id}:$it" },
            contentScale = ContentScale.Fit,
            crossfadeEnabled = true,
            crossfadeMillis = 220,
            precision = Precision.EXACT,
            scale = Scale.FIT,
            preferThumbnail = false,
            fallback = if (item.kind == "video") AppImageFallback.Video else AppImageFallback.Image,
            description = item.title
        )
    }

    private fun resolveStaticUrl(rawUrl: String?, staticBaseUrl: String): String? {
        if (rawUrl.isNullOrBlank()) return null
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }
        return staticBaseUrl.trimEnd('/') + rawUrl.removePrefix("/static")
    }
}

@Composable
fun MeetingCoverImage(
    meeting: Meeting,
    slot: AppImageSlot,
    accentColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val model = remember(meeting, slot) {
        MeetingImageResolver.resolve(meeting = meeting, slot = slot).copy(
            description = contentDescription ?: meeting.title
        )
    }
    AppAsyncImage(
        model = model,
        modifier = modifier,
        fallbackContent = { state ->
            MeetingImageFallback(
                accentColor = accentColor,
                isLoading = state == AppAsyncImageState.Loading,
                isError = state == AppAsyncImageState.Error
            )
        }
    )
}

enum class AppAsyncImageState {
    Loading,
    Success,
    Error
}

@Composable
fun AppAsyncImage(
    model: AppImageModel,
    modifier: Modifier = Modifier,
    fallbackContent: @Composable BoxScope.(AppAsyncImageState) -> Unit = { state ->
        DefaultImageFallback(
            fallback = model.fallback,
            isLoading = state == AppAsyncImageState.Loading,
            isError = state == AppAsyncImageState.Error
        )
    }
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val request = remember(context, model, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(model.data ?: model.fallbackResId)
                .memoryCacheKey(model.memoryCacheKey)
                .diskCacheKey(model.diskCacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .precision(model.precision)
                .scale(model.scale)
                .allowHardware(model.allowHardware)
                .size(Size(widthPx, heightPx))
                .apply {
                    if (model.crossfadeEnabled) {
                        crossfade(model.crossfadeMillis)
                    }
                }
                .build()
        }
        val painter = rememberAsyncImagePainter(model = request)
        val state = when (painter.state) {
            is AsyncImagePainter.State.Success -> AppAsyncImageState.Success
            is AsyncImagePainter.State.Error -> AppAsyncImageState.Error
            else -> AppAsyncImageState.Loading
        }

        fallbackContent(state)
        if (state == AppAsyncImageState.Error && model.fallbackResId != null) {
            Image(
                painter = painterResource(id = model.fallbackResId),
                contentDescription = model.description,
                contentScale = model.contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (model.data != null || model.fallbackResId != null) {
            Image(
                painter = painter,
                contentDescription = model.description,
                contentScale = model.contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (state == AppAsyncImageState.Error) 0f else 1f)
            )
        }
    }
}

@Composable
private fun MeetingImageFallback(
    accentColor: Color,
    isLoading: Boolean,
    isError: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.96f),
                        accentColor.copy(alpha = 0.68f),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(180.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f)
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(124.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.06f)
        ) {}
        LoadingOrErrorBadge(
            icon = Icons.Default.Image,
            tint = Color.White,
            isLoading = isLoading,
            isError = isError
        )
    }
}

@Composable
private fun DefaultImageFallback(
    fallback: AppImageFallback,
    isLoading: Boolean,
    isError: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "image-placeholder")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "image-placeholder-alpha"
    )
    val backgroundColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    )
    val icon = when (fallback) {
        AppImageFallback.Image -> Icons.Default.Image
        AppImageFallback.Video -> Icons.Default.PlayCircleFilled
        AppImageFallback.Document -> Icons.Default.Description
        AppImageFallback.Poster -> Icons.Default.Image
        AppImageFallback.Meeting -> Icons.Default.Image
    }
    val tint = if (fallback == AppImageFallback.Meeting) {
        Color.White.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(backgroundColors),
                shape = RoundedCornerShape(0.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        LoadingOrErrorBadge(
            icon = if (isError) Icons.Default.BrokenImage else icon,
            tint = tint,
            isLoading = isLoading,
            isError = isError
        )
    }
}

@Composable
private fun BoxScope.LoadingOrErrorBadge(
    icon: ImageVector,
    tint: Color,
    isLoading: Boolean,
    isError: Boolean
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp),
            color = tint,
            strokeWidth = 2.4.dp
        )
        return
    }

    if (isError) {
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.18f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(36.dp)
            )
        }
        return
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint.copy(alpha = 0.85f),
        modifier = Modifier
            .align(Alignment.Center)
            .size(34.dp)
    )
}
