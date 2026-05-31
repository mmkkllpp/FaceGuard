package com.faceguard.face

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.faceguard.FaceGuardApp
import com.faceguard.R
import com.faceguard.util.FileLogger
import com.faceguard.data.AppPreferences
import com.faceguard.ui.MainActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 模型文件管理器 — 在 App 内从 HuggingFace 下载 ArcFace ONNX 模型
 *
 * 模型来源: https://huggingface.co/garavv/arcface-onnx/resolve/main/arc.onnx
 * 保存路径: context.filesDir/models/arc.onnx
 */
class ModelDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val tag = "ModelDownload"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val MODEL_URL = "https://huggingface.co/garavv/arcface-onnx/resolve/main/arc.onnx"
        const val MODEL_FILENAME = "arc.onnx"
        const val EXPECTED_HASH = "a1b2c3d4e5f6..." // TODO: 替换为实际 SHA-256
    }

    fun getModelFile(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > 1_000_000
    }

    fun isModelReady(): Boolean {
        // Check if ArcFace model is available (exists and > 1MB)
        val file = getModelFile()
        return file.exists() && file.length() > 1_000_000
    }

    /**
     * 下载模型（协程方式）
     * @param onProgress (downloadedBytes, totalBytes) → Unit
     */
    suspend fun download(onProgress: (Long, Long) -> Unit = { _, _ -> }): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                FileLogger.i(tag, "开始下载模型: $MODEL_URL")
                val request = Request.Builder().url(MODEL_URL).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val msg = "下载失败: HTTP ${response.code}"
                    FileLogger.e(tag, msg)
                    return@withContext Result.failure(Exception(msg))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("响应体为空"))
                val totalBytes = body.contentLength()
                val file = getModelFile()
                // 确保父目录存在
                file.parentFile?.mkdirs()
                if (file.exists()) file.delete()

                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    val inputStream = body.byteStream()

                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress(downloaded, totalBytes)
                    }
                }

                FileLogger.i(tag, "模型下载完成: ${file.absolutePath} (${file.length()} bytes)")
                Result.success(file)
            } catch (e: Exception) {
                FileLogger.e(tag, "下载异常: ${e.message}")
                Result.failure(e)
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
