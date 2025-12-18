package com.example.paperlessmeeting.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassyTopBar(title: String) {
    // Glassmorphism effect is tricky to do perfectly with just standard compose modifiers in a quick setup,
    // so we will simulate it with a semi-transparent surface and blur (if supported) or just alpha.
    // Real blur requires RenderEffect (API 31+). For now, we stick to a clean translucent design.

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = { /* TODO: Open Drawer */ }) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = { /* TODO: Notifications */ }) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
        ),
        modifier = Modifier.shadow(elevation = 1.dp)
    )
}
