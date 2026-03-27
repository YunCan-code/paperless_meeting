package com.example.paperlessmeeting.ui.components.notice

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object BottomOverlayDefaults {
    val PhoneFloatingNavClearance = 92.dp
    val TabletFloatingNavClearance = 108.dp
    val BaseBottomGap = 16.dp
    val HorizontalPadding = 16.dp
}

class AppNoticeController(
    private val onShowMessage: (String, SnackbarDuration) -> Unit = { _, _ -> }
) {
    fun showMessage(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short
    ) {
        if (message.isBlank()) return
        onShowMessage(message, duration)
    }
}

val LocalAppNoticeController = staticCompositionLocalOf { AppNoticeController() }

@Composable
fun rememberAppNoticeController(
    hostState: SnackbarHostState,
    scope: CoroutineScope
): AppNoticeController {
    return remember(hostState, scope) {
        AppNoticeController { message, duration ->
            scope.launch {
                hostState.currentSnackbarData?.dismiss()
                hostState.showSnackbar(message = message, duration = duration)
            }
        }
    }
}

@Composable
fun rememberBottomOverlayPadding(hasFloatingNav: Boolean): Dp {
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    return if (hasFloatingNav) {
        if (isPhone) {
            BottomOverlayDefaults.PhoneFloatingNavClearance
        } else {
            BottomOverlayDefaults.TabletFloatingNavClearance
        }
    } else {
        BottomOverlayDefaults.BaseBottomGap
    }
}

@Composable
fun AppNoticeHost(
    hostState: SnackbarHostState,
    hasFloatingNav: Boolean,
    modifier: Modifier = Modifier
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    val bottomPadding = rememberBottomOverlayPadding(hasFloatingNav = hasFloatingNav)

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = BottomOverlayDefaults.HorizontalPadding,
                end = BottomOverlayDefaults.HorizontalPadding,
                bottom = bottomPadding
            ),
        snackbar = { data ->
            Snackbar(
                modifier = Modifier.fillMaxWidth(if (isPhone) 0.94f else 0.62f),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(text = data.visuals.message)
            }
        }
    )
}
