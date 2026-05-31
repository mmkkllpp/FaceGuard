package com.faceguard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.faceguard.admin.DeviceAdminReceiver

/**
 * 权限设置页面 — 自动刷新权限状态
 */
class PermissionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceGuardTheme { PermissionsScreen() } }
    }

    override fun onResume() {
        super.onResume()
        // 返回时刷新
        setContent { FaceGuardTheme { PermissionsScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PermissionsScreen() {
        val context = LocalContext.current

        val permissions = remember {
            mutableStateOf(
                listOf(
                    PermItem("相机", "人脸检测必需", Icons.Default.CameraAlt, false) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    },
                    PermItem("无障碍服务", "拦截受限应用", Icons.Default.Accessibility, false) {
                        checkAccessibility()
                    },
                    PermItem("设备管理员", "防卸载 + 锁屏", Icons.Default.AdminPanelSettings, false) {
                        DeviceAdminReceiver.isActive(context)
                    },
                    PermItem("使用情况访问", "检测前台应用", Icons.Default.BarChart, false) {
                        checkUsageStats()
                    },
                    PermItem("悬浮窗权限", "显示锁屏界面", Icons.Default.PictureInPicture, false) {
                        Settings.canDrawOverlays(context)
                    },
                    PermItem("电池优化", "防止被系统杀进程", Icons.Default.BatteryFull, true) {
                        true // 手动设置
                    },
                )
            )
        }

        // 刷新状态
        LaunchedEffect(Unit) {
            permissions.value = permissions.value.map { it.copy(granted = it.check()) }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("权限设置", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("开启以下权限以确保 App 正常工作", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                items(permissions.value) { perm ->
                    PermCard(perm)
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Text("提示：从系统设置返回后页面会自动刷新状态", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }

    @Composable
    fun PermCard(perm: PermItem) {
        val color = if (perm.granted) Color(0xFF1A6B3C) else MaterialTheme.colorScheme.surfaceVariant
        Card(colors = CardDefaults.cardColors(containerColor = color)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(perm.icon, "", tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(perm.label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(perm.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (perm.granted) {
                    Icon(Icons.Default.CheckCircle, "已授权", tint = Color(0xFF00D4AA))
                } else if (!perm.skipAction) {
                    Button(
                        onClick = {
                            when (perm.label) {
                                "无障碍服务" -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                "设备管理员" -> context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                "使用情况访问" -> context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                "悬浮窗权限" -> context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                "电池优化" -> context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("去开启", fontSize = 13.sp) }
                } else {
                    Text("手动", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }

    private fun checkAccessibility(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo?.name == "com.faceguard.service.AppBlockerAccessibilityService" }
    }

    private fun checkUsageStats(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        return appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    data class PermItem(
        val label: String, val desc: String, val icon: ImageVector,
        val skipAction: Boolean, val check: () -> Boolean,
        val granted: Boolean = false
    )
}
