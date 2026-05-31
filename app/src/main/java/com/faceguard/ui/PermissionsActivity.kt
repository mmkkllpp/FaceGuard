package com.faceguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
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

class PermissionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceGuardTheme { PermissionsScreen() } }
    }

    override fun onResume() {
        super.onResume()
        setContent { FaceGuardTheme { PermissionsScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PermissionsScreen() {
        val context = LocalContext.current

        val items = listOf(
            PermDef("相机", "人脸检测必需", Icons.Default.CameraAlt),
            PermDef("无障碍服务", "拦截受限应用", Icons.Default.Accessibility),
            PermDef("设备管理员", "防卸载 + 锁屏", Icons.Default.AdminPanelSettings),
            PermDef("使用情况访问", "检测前台应用", Icons.Default.BarChart),
            PermDef("悬浮窗权限", "显示锁屏界面", Icons.Default.PictureInPicture),
            PermDef("电池优化", "防止被系统杀进程", Icons.Default.BatteryFull),
        )

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
                items(items) { def ->
                    val granted = checkGranted(context, def.label)
                    val bg = if (granted) Color(0xFF1A6B3C) else MaterialTheme.colorScheme.surfaceVariant
                    Card(colors = CardDefaults.cardColors(containerColor = bg)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(def.icon, "", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(def.label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text(def.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (granted) {
                                Icon(Icons.Default.CheckCircle, "已授权", tint = Color(0xFF00D4AA))
                            } else if (def.label != "电池优化") {
                                Button(
                                    onClick = {
                                        val intent = when (def.label) {
                                            "无障碍服务" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            "设备管理员" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                                            "使用情况访问" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            "悬浮窗权限" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                            else -> null
                                        }
                                        if (intent != null) context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) { Text("去开启", fontSize = 13.sp) }
                            } else {
                                Text("手动", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("从系统设置返回后页面自动刷新", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }

    data class PermDef(val label: String, val desc: String, val icon: ImageVector)

    private fun checkGranted(context: android.content.Context, label: String): Boolean {
        return when (label) {
            "相机" -> ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            "无障碍服务" -> {
                val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
                    .any { it.resolveInfo.serviceInfo?.name == "com.faceguard.service.AppBlockerAccessibilityService" }
            }
            "设备管理员" -> DeviceAdminReceiver.isActive(context)
            "使用情况访问" -> {
                val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
            }
            "悬浮窗权限" -> Settings.canDrawOverlays(context)
            "电池优化" -> true
            else -> false
        }
    }
}
