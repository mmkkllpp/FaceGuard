package com.faceguard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import com.faceguard.admin.DeviceAdminReceiver
import com.faceguard.data.AppPreferences
import com.faceguard.face.FaceEnrollmentActivity
import com.faceguard.face.ModelDownloadActivity
import com.faceguard.face.ModelDownloadManager
import com.faceguard.service.FaceGuardService
import com.faceguard.util.FileLogger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPreferences(this)
        FileLogger.i("Main", "主界面启动")
        setContent { FaceGuardTheme { MainScreen(onPrefs = { prefs }) } }
    }

    override fun onResume() {
        super.onResume()
        val prefs = AppPreferences(this)
        setContent { FaceGuardTheme { MainScreen(onPrefs = { prefs }) } }
    }
}

// ── 状态 ──
private class MState(
    val faceEnrolled: Boolean,
    val restrictionActive: Boolean,
    val modelReady: Boolean,
    val interval: Int,
    val threshold: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(onPrefs: () -> AppPreferences) {
    val context = LocalContext.current
    val p = onPrefs()

    var faceEnrolled by remember { mutableStateOf(p.isFaceEnrolled()) }
    var restrictionActive by remember { mutableStateOf(p.isRestrictionActive) }
    var modelReady by remember { mutableStateOf(ModelDownloadManager(context).isModelReady()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FaceGuard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 状态卡片 ──
            item { StatusCard(faceEnrolled, restrictionActive, modelReady, p.detectionInterval) }

            // ── 快捷操作 ──
            item { Text("快捷操作", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            item { ActionBtn(Icons.Default.Face, "录入人脸", if (faceEnrolled) "已录入" else "未录入", true,
                onClick = { context.startActivity(Intent(context, FaceEnrollmentActivity::class.java)) }) }

            item { ActionBtn(Icons.Default.CloudDownload, "下载识别模型", if (modelReady) "模型已就绪" else "未下载（将使用降级模式）", !modelReady,
                onClick = { context.startActivity(Intent(context, ModelDownloadActivity::class.java)) }) }

            item {
                ActionBtn(
                    if (restrictionActive) Icons.Default.Lock else Icons.Default.LockOpen,
                    if (restrictionActive) "停用限制模式" else "启用限制模式",
                    if (restrictionActive) "已启用，${p.detectionInterval}s 检测一次" else "点击启用",
                    false, restrictionActive,
                    onClick = {
                        if (!faceEnrolled) { Toast.makeText(context, "请先录入人脸", Toast.LENGTH_SHORT).show(); return@onClick }
                        val ns = !restrictionActive; restrictionActive = ns; p.isRestrictionActive = ns
                        if (ns) FaceGuardService.start(context) else FaceGuardService.stop(context)
                        FileLogger.i("Main", "限制模式: ${if(ns)"开启"else"关闭"}")
                    })
            }

            item { ActionBtn(Icons.Default.Refresh, "立即检测", "手动触发一次人脸检测",
                onClick = { FaceGuardService.checkNow(context) }) }

            // ── 设置 ──
            item { Spacer(Modifier.height(8.dp)); Text("设置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            item {
                SettingsCard(items = listOf(
                    SItem("权限设置", Icons.Default.Security) { context.startActivity(Intent(context, PermissionsActivity::class.java)) },
                    SItem("检测间隔 ${p.detectionInterval}s", Icons.Default.Timer) {},
                    SItem("相似度阈值 ${p.similarityThreshold}", Icons.Default.Tune) {},
                    SItem("清除人脸数据", Icons.Default.DeleteForever, danger = true) { p.clearFaceEnrollment(); faceEnrolled = false; Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show() },
                    SItem("查看调试日志", Icons.Default.Description) { context.startActivity(Intent(context, LogViewerActivity::class.java)) },
                ))
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── 状态卡片 ──
@Composable
private fun StatusCard(enrolled: Boolean, restricted: Boolean, modelReady: Boolean, interval: Int) {
    val bg = when { restricted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f); enrolled -> Color(0xFF1A4A2E); else -> MaterialTheme.colorScheme.surfaceVariant }
    val icon = when { restricted -> Icons.Default.WarningAmber; enrolled -> Icons.Default.VerifiedUser; else -> Icons.Default.Info }
    Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, "", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(when { restricted -> "⚠️ 限制模式已激活"; enrolled -> "✅ 防护就绪"; else -> "⚠️ 未配置" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(when { restricted -> "非录入用户使用将被拦截"; !enrolled -> "请录入人脸并开启限制模式"; !modelReady -> "建议下载识别模型"; else -> "一切正常，${interval}s 检测一次" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── 操作按钮 ──
@Composable
private fun ActionBtn(icon: ImageVector, label: String, desc: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(
        containerColor = when { danger -> MaterialTheme.colorScheme.errorContainer; primary -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
    )) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, "", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Medium); Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, "", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 设置项数据 ──
private data class SItem(val label: String, val icon: ImageVector, val onClick: () -> Unit, val danger: Boolean = false)

// ── 设置卡片 ──
@Composable
private fun SettingsCard(items: List<SItem>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().clickable(onClick = item.onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, "", tint = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(item.label, Modifier.weight(1f), color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ChevronRight, "", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            if (i < items.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
