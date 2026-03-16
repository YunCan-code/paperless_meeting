package com.example.paperlessmeeting.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    var showPasswordSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    val passwordSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "\u8bbe\u7f6e",
                        style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Button(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).height(36.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("\u9000\u51fa\u767b\u5f55", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item { ProfileCard(state = state, onClick = { showProfileSheet = true }) }
            item {
                SettingsGroup("\u8d26\u6237") {
                    SettingsItem(Icons.Outlined.Lock, "\u4fee\u6539\u5bc6\u7801") { showPasswordSheet = true }
                }
            }
            item {
                AppearanceSection(
                    themeMode = state.themeMode,
                    fontScaleLevel = state.fontScaleLevel,
                    onThemeModeChange = viewModel::setThemeMode,
                    onFontScaleLevelChange = viewModel::setFontScaleLevel
                )
            }
            item {
                SettingsGroup("\u8fde\u63a5") {
                    SettingsItem(Icons.Outlined.Dns, "\u670d\u52a1\u5668\u5730\u5740", state.serverHost) {
                        showServerDialog = true
                    }
                }
            }
            item {
                SettingsGroup("\u7cfb\u7edf") {
                    SettingsItem(Icons.Outlined.CleaningServices, "\u6e05\u7406\u7f13\u5b58", state.cacheSize) {
                        viewModel.clearCache()
                    }
                    SettingsItem(Icons.Outlined.SystemUpdate, "\u68c0\u67e5\u66f4\u65b0", "\u5f53\u524d\u7248\u672c ${state.versionName}") {
                        viewModel.checkForUpdate(
                            onNoUpdate = {
                                Toast.makeText(context, "\u5f53\u524d\u5df2\u662f\u6700\u65b0\u7248\u672c", Toast.LENGTH_SHORT).show()
                            },
                            onUpdateAvailable = { update ->
                                val builder = android.app.AlertDialog.Builder(context)
                                    .setTitle("\u53d1\u73b0\u65b0\u7248\u672c ${update.version_name}")
                                    .setMessage(update.release_notes.ifBlank { "\u662f\u5426\u7acb\u5373\u4e0b\u8f7d\u5e76\u5b89\u88c5\u65b0\u7248\u672c\uff1f" })
                                    .setPositiveButton("\u7acb\u5373\u66f4\u65b0") { _, _ -> viewModel.triggerAppUpdate(update) }
                                if (!update.is_force_update) {
                                    builder.setNegativeButton("\u7a0d\u540e\u518d\u8bf4", null)
                                }
                                builder.show()
                            },
                            onError = {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            item { DeviceInfoSection(deviceInfo = state.deviceInfo, onCopy = viewModel::copyDeviceInfoToClipboard) }
        }

        if (showPasswordSheet) {
            ModalBottomSheet(onDismissRequest = { showPasswordSheet = false }, sheetState = passwordSheetState) {
                ChangePasswordSheet(onDismiss = { showPasswordSheet = false }, viewModel = viewModel)
            }
        }
        if (showProfileSheet) {
            ModalBottomSheet(onDismissRequest = { showProfileSheet = false }, sheetState = profileSheetState) {
                EditProfileSheet(
                    state = state,
                    onDismiss = { showProfileSheet = false },
                    onSave = { dept, district, phone, email ->
                        viewModel.updateProfile(dept, district, phone, email) { showProfileSheet = false }
                    }
                )
            }
        }
        if (showServerDialog) {
            ServerAddressDialog(
                currentHost = state.serverHost,
                onDismiss = { showServerDialog = false },
                onSave = {
                    viewModel.updateServerHost(it)
                    showServerDialog = false
                },
                onReset = {
                    viewModel.resetServerHost()
                    showServerDialog = false
                }
            )
        }
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("\u786e\u8ba4\u9000\u51fa\u767b\u5f55") },
                text = { Text("\u662f\u5426\u4ece\u5f53\u524d\u8bbe\u5907\u9000\u51fa\u767b\u5f55\uff1f") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onLogout()
                    }) { Text("\u9000\u51fa\u767b\u5f55", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("\u53d6\u6d88") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    fontScaleLevel: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontScaleLevelChange: (Int) -> Unit
) {
    val labels = listOf("\u5c0f", "\u6807\u51c6", "\u5927", "\u8d85\u5927")
    SettingsGroup("\u5916\u89c2") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ThemeMode.SYSTEM to "\u8ddf\u968f\u7cfb\u7edf",
                    ThemeMode.LIGHT to "\u6d45\u8272",
                    ThemeMode.DARK to "\u6df1\u8272"
                )
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label)
                    }
                }
            }
            Slider(
                value = fontScaleLevel.toFloat(),
                onValueChange = { onFontScaleLevelChange(it.toInt()) },
                valueRange = 0f..3f,
                steps = 2
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            Text(
                text = "\u9884\u89c8\u6587\u5b57 Aa",
                fontSize = (16 * AppSettingsState.fontScaleFactor(fontScaleLevel)).sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DeviceInfoSection(deviceInfo: DeviceInfo, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    SettingsGroup("\u8bbe\u5907\u4fe1\u606f") {
        Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Smartphone, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(deviceInfo.model.ifBlank { "\u672a\u77e5\u8bbe\u5907" }, fontWeight = FontWeight.Medium)
                    Text(deviceInfo.ipAddress.ifBlank { "\u672a\u77e5IP" }, style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceInfoRow("\u8bbe\u5907ID", deviceInfo.deviceId)
                    DeviceInfoRow("\u7cfb\u7edf\u7248\u672c", deviceInfo.osVersion)
                    DeviceInfoRow("\u5e94\u7528\u7248\u672c", deviceInfo.appVersion)
                    DeviceInfoRow("MAC\u5730\u5740", deviceInfo.macAddress)
                    DeviceInfoRow("\u5b58\u50a8\u7a7a\u95f4", "${deviceInfo.storageAvailableMB}MB / ${deviceInfo.storageTotalMB}MB")
                    OutlinedButton(
                        onClick = {
                            onCopy()
                            Toast.makeText(context, "\u8bbe\u5907\u4fe1\u606f\u5df2\u590d\u5236", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("\u590d\u5236\u8bbe\u5907\u4fe1\u606f")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" }, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ServerAddressDialog(currentHost: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onReset: () -> Unit) {
    var host by remember { mutableStateOf(currentHost) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u670d\u52a1\u5668\u5730\u5740") },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("\u670d\u52a1\u5668\u5730\u5740") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { host = AppSettingsState.getDefaultHost() }) { Text("\u6062\u590d\u9ed8\u8ba4") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = host.trim().trimEnd('/')
                if (trimmed.isNotBlank()) onSave(trimmed)
            }) { Text("\u4fdd\u5b58") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("\u91cd\u7f6e") }
                TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") }
            }
        }
    )
}

@Composable
fun ProfileCard(state: SettingsUiState, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) {
                    Text(state.userName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(state.userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(listOf(state.userDept, state.userRole).filter { it.isNotBlank() }.joinToString(" | "))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))
        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            content = content
        )
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun EditProfileSheet(state: SettingsUiState, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var dept by remember { mutableStateOf(state.userDept) }
    var district by remember { mutableStateOf(state.userDistrict) }
    var phone by remember { mutableStateOf(state.userPhone) }
    var email by remember { mutableStateOf(state.userEmail) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp).navigationBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") }
            Text("\u4e2a\u4eba\u8d44\u6599", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onSave(dept, district, phone, email) }) { Text("\u4fdd\u5b58", fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = state.userName, onValueChange = {}, label = { Text("\u59d3\u540d") }, readOnly = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = dept, onValueChange = { dept = it }, label = { Text("\u90e8\u95e8") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("\u533a\u53bf") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("\u624b\u673a\u53f7") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("\u90ae\u7bb1") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ChangePasswordSheet(onDismiss: () -> Unit, viewModel: SettingsViewModel) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text("\u4fee\u6539\u5bc6\u7801", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        PasswordTextField(oldPassword, { oldPassword = it }, "\u5f53\u524d\u5bc6\u7801", oldVisible) { oldVisible = !oldVisible }
        Spacer(modifier = Modifier.height(16.dp))
        PasswordTextField(newPassword, { newPassword = it }, "\u65b0\u5bc6\u7801", newVisible) { newVisible = !newVisible }
        Spacer(modifier = Modifier.height(16.dp))
        PasswordTextField(confirmPassword, { confirmPassword = it }, "\u786e\u8ba4\u65b0\u5bc6\u7801", confirmVisible) { confirmVisible = !confirmVisible }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    newPassword.isBlank() -> errorMessage = "\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"
                    newPassword != confirmPassword -> errorMessage = "\u4e24\u6b21\u8f93\u5165\u7684\u5bc6\u7801\u4e0d\u4e00\u81f4"
                    newPassword.length < 6 -> errorMessage = "\u5bc6\u7801\u81f3\u5c11\u9700\u8981 6 \u4f4d"
                    else -> {
                        loading = true
                        viewModel.changePassword(
                            oldPwd = oldPassword,
                            newPwd = newPassword,
                            onSuccess = {
                                loading = false
                                onDismiss()
                            },
                            onError = {
                                loading = false
                                errorMessage = it
                            }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("\u786e\u8ba4")
            }
        }
    }
}

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(if (isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
