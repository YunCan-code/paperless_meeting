package com.example.paperlessmeeting.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // BottomSheet States
    var showPasswordSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    val passwordSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "设置", 
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Profile Card
            item {
                ProfileCard(state, onClick = { showProfileSheet = true })
            }

            // 2. Account Settings
            item {
                SettingsGroup("账户安全") {
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "修改密码",
                        onClick = { showPasswordSheet = true }
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Badge,
                        title = "个人信息",
                        subtitle = "完善您的个人资料",
                        onClick = { showProfileSheet = true }
                    )
                }
            }

            // 3. System Settings
            item {
                SettingsGroup("系统设置") {
                    SettingsItem(
                        icon = Icons.Outlined.CleaningServices,
                        title = "清理缓存",
                        subtitle = state.cacheSize,
                        onClick = { 
                            viewModel.clearCache()
                            Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsItem(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "检查更新",
                        subtitle = "当前版本 ${state.versionName}",
                        onClick = { 
                             Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            
            // 4. About
            item {
                SettingsGroup("关于") {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "关于我们",
                        onClick = { /* TODO */ }
                    )
                }
            }

            // 5. Logout Button
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("退出登录", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        
        // --- Bottom Sheets ---
        
        if (showPasswordSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPasswordSheet = false },
                sheetState = passwordSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                ChangePasswordSheet(
                    onDismiss = { showPasswordSheet = false },
                    viewModel = viewModel
                )
            }
        }
        
        if (showProfileSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProfileSheet = false },
                sheetState = profileSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                EditProfileSheet(
                    state = state,
                    onDismiss = { showProfileSheet = false },
                    onSave = { dept, district, phone, email ->
                        viewModel.updateProfile(dept, district, phone, email) {
                             showProfileSheet = false
                             Toast.makeText(context, "个人信息已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
        
        // --- Logout Dialog ---
        if (showLogoutDialog) {
             AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("确认退出") },
                text = { Text("确定要退出登录吗？") },
                confirmButton = {
                    TextButton(
                        onClick = { 
                            showLogoutDialog = false 
                            viewModel.logout()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    ) { Text("退出", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

// ---------------------- Sub-Composables ----------------------

@Composable
fun ProfileCard(state: SettingsUiState, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.userName.take(1),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column {
                Text(
                    text = state.userName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Business, 
                        null, 
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${state.userDept} | ${state.userRole}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) 
        },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
        } else null,
        leadingContent = {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ---------------------- Bottom Sheets ----------------------

@Composable
fun EditProfileSheet(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var dept by remember { mutableStateOf(state.userDept) }
    var district by remember { mutableStateOf(state.userDistrict) }
    var phone by remember { mutableStateOf(state.userPhone) }
    var email by remember { mutableStateOf(state.userEmail) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp) // Add padding for bottom navigation bar
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onDismiss, 
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text("取消", color = MaterialTheme.colorScheme.outline)
            }
            Text(
                "个人主页", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            TextButton(
                onClick = { onSave(dept, district, phone, email) }, 
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Avatar
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = state.userName.take(1),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        
        // Form Fields
        OutlinedTextField(
            value = state.userName,
            onValueChange = {},
            label = { Text("姓名") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = dept,
            onValueChange = { dept = it },
            label = { Text("部门") },
            leadingIcon = { Icon(Icons.Outlined.Business, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = district,
            onValueChange = { district = it },
            label = { Text("区县") },
            leadingIcon = { Icon(Icons.Outlined.LocationOn, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("联系方式") },
            leadingIcon = { Icon(Icons.Outlined.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            leadingIcon = { Icon(Icons.Outlined.Email, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ChangePasswordSheet(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var oldPwdVisible by remember { mutableStateOf(false) }
    var newPwdVisible by remember { mutableStateOf(false) }
    var confirmPwdVisible by remember { mutableStateOf(false) }
    
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag Handle removed (using default ModalBottomSheet handle)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "修改密码",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Fields
        PasswordTextField(
            value = oldPwd,
            onValueChange = { oldPwd = it },
            label = "旧密码",
            isVisible = oldPwdVisible,
            onToggleVisibility = { oldPwdVisible = !oldPwdVisible }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        PasswordTextField(
            value = newPwd,
            onValueChange = { newPwd = it },
            label = "新密码",
            isVisible = newPwdVisible,
            onToggleVisibility = { newPwdVisible = !newPwdVisible }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        PasswordTextField(
            value = confirmPwd,
            onValueChange = { confirmPwd = it },
            label = "确认新密码",
            isVisible = confirmPwdVisible,
            onToggleVisibility = { confirmPwdVisible = !confirmPwdVisible }
        )
        
        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMsg!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (newPwd.isBlank()) { errorMsg = "密码不能为空"; return@Button }
                if (newPwd != confirmPwd) { errorMsg = "两次输入的密码不一致"; return@Button }
                if (newPwd.length < 6) { errorMsg = "密码长度至少6位"; return@Button }
                
                loading = true
                viewModel.changePassword(
                    oldPwd = oldPwd,
                    newPwd = newPwd,
                    onSuccess = {
                        loading = false
                        onDismiss()
                    },
                    onError = {
                        loading = false
                        errorMsg = it
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("确认修改")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
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
                Icon(
                    imageVector = if (isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
