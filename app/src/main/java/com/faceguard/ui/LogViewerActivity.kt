package com.faceguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.faceguard.util.FileLogger

/**
 * 调试日志查看器
 */
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

        LaunchedEffect(Unit) { logContent = FileLogger.readLatestLog(300) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("调试日志", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = {
                        IconButton(onClick = {
                            FileLogger.clearLogs()
                            logContent = "日志已清除"
                            Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.DeleteSweep, "清除") }
                        IconButton(onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("log", logContent))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "复制") }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            Text(
                text = logContent.ifEmpty { "暂无日志" },
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 13.sp
            )
        }
    }
}
