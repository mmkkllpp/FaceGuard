package com.faceguard.face

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.faceguard.FaceGuardApp
import com.faceguard.ui.FaceGuardTheme
import com.faceguard.util.FileLogger
import kotlinx.coroutines.*

/**
 * 模型下载界面 — 在 App 内下载 ArcFace ONNX 模型
 */
class ModelDownloadActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var downloader: ModelDownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloader = ModelDownloadManager(this)

        setContent {
            FaceGuardTheme {
                DownloadScreen()
            }
        }
    }

    @Composable
    fun DownloadScreen() {
        var state by remember { mutableStateOf(DownloadState.IDLE) }
        var progress by remember { mutableFloatStateOf(0f) }
        var progressText by remember { mutableStateOf("") }

        val config = MaterialTheme.colorScheme

        Box(Modifier.fillMaxSize().background(config.surface)) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 图标
                when (state) {
                    DownloadState.IDLE, DownloadState.DOWNLOADING -> {
                        Icon(Icons.Default.CloudDownload, "",
                            tint = config.primary, modifier = Modifier.size(80.dp))
                    }
                    DownloadState.SUCCESS -> {
                        Icon(Icons.Default.CheckCircle, "",
                            tint = Color(0xFF00D4AA), modifier = Modifier.size(80.dp))
                    }
                    DownloadState.ERROR -> {
                        Icon(Icons.Default.ErrorOutline, "",
                            tint = config.error, modifier = Modifier.size(80.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text("人脸识别模型", color = config.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("下载 ArcFace ONNX 模型以启用高精度人脸识别\n不下载也可使用降级模式", color = config.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(24.dp))

                // 进度条
                if (state == DownloadState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = config.primary, trackColor = config.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(progressText, color = config.onSurfaceVariant, fontSize = 13.sp)
                } else if (state == DownloadState.SUCCESS) {
                    Text("模型已就绪 ✅", color = Color(0xFF00D4AA), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else if (state == DownloadState.ERROR) {
                    Text("下载失败，请重试", color = config.error, fontSize = 14.sp)
                }

                Spacer(Modifier.height(24.dp))

                // 按钮
                if (state == DownloadState.IDLE || state == DownloadState.ERROR) {
                    Button(
                        onClick = { startDownload { s, p, t -> state = s; progress = p; progressText = t } },
                        colors = ButtonDefaults.buttonColors(containerColor = config.primary),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("下载模型", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { finish() }) { Text("跳过（使用降级模式）", color = config.onSurfaceVariant) }
                } else if (state == DownloadState.SUCCESS) {
                    Button(
                        onClick = { finish() },
                        colors = ButtonDefaults.buttonColors(containerColor = config.primary),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("完成", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    private fun startDownload(onUpdate: (DownloadState, Float, String) -> Unit) {
        scope.launch {
            onUpdate(DownloadState.DOWNLOADING, 0f, "准备下载...")
            downloader.download { downloaded, total ->
                val p = if (total > 0) downloaded.toFloat() / total else 0f
                val mbDown = downloaded / 1_000_000f
                val mbTotal = if (total > 0) total / 1_000_000f else 0f
                val text = if (total > 0) "%.1f / %.1f MB".format(mbDown, mbTotal) else "%.1f MB".format(mbDown)
                launch(Dispatchers.Main) { onUpdate(DownloadState.DOWNLOADING, p, text) }
            }.fold(
                onSuccess = {
                    FileLogger.i("ModelDL", "模型下载成功")
                    onUpdate(DownloadState.SUCCESS, 1f, "下载完成")
                },
                onFailure = { e ->
                    FileLogger.e("ModelDL", "模型下载失败: ${e.message}")
                    onUpdate(DownloadState.ERROR, 0f, e.message ?: "未知错误")
                }
            )
        }
    }

    enum class DownloadState { IDLE, DOWNLOADING, SUCCESS, ERROR }
}
