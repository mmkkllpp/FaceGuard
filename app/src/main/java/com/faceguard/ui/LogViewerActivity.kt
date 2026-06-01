package com.faceguard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.faceguard.util.FileLogger
import java.io.File

class LogViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceGuardTheme { LogScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LogScreen() {
        val context = LocalContext.current
        var logContent by remember { mutableStateOf("") }
        var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }

        LaunchedEffect(Unit) {
            logFiles = FileLogger.getLogFiles()
            logContent = FileLogger.readLatestLog(500)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("调试日志", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = {
                        // 分享
                        IconButton(onClick = {
                            try {
                                val file = FileLogger.getLogFiles().firstOrNull()
                                if (file != null) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享日志"))
                                } else {
                                    Toast.makeText(context, "暂无日志可分享", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                // fallback: 复制到剪贴板
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("log", logContent))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Share, "分享") }
                        // 复制
                        IconButton(onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("log", logContent))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "复制") }
                        // 清除
                        IconButton(onClick = {
                            FileLogger.clearLogs()
                            logContent = "日志已清除"
                            Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.DeleteSweep, "清除") }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // 文件列表
                if (logFiles.isNotEmpty()) {
                    Text(
                        "共 ${logFiles.size} 个日志文件",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 日志内容
                Text(
                    text = logContent.ifEmpty { "暂无日志" },
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
