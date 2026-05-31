package com.faceguard.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件日志工具 — 日志写入本地文件供调试用
 * 日志文件路径: context.filesDir/logs/FaceGuard_YYYY-MM-DD.log
 */
object FileLogger {

    private lateinit var logDir: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private val dateFileFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val lock = Any()

    fun init(context: Context) {
        logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        // 清理 7 天前的日志
        cleanup(context)
        i("FileLogger", "日志初始化完成: ${logDir.absolutePath}")
    }

    fun v(tag: String, msg: String) = log('V', tag, msg)
    fun d(tag: String, msg: String) = log('D', tag, msg)
    fun i(tag: String, msg: String) = log('I', tag, msg)
    fun w(tag: String, msg: String) = log('W', tag, msg)
    fun e(tag: String, msg: String) = log('E', tag, msg)

    fun getLogFiles(): List<File> {
        return logDir.listFiles { f -> f.name.startsWith("FaceGuard_") }?.sortedDescending() ?: emptyList()
    }

    fun readLatestLog(maxLines: Int = 500): String {
        val files = getLogFiles()
        if (files.isEmpty()) return "暂无日志"
        val latest = files.first()
        val lines = latest.readLines()
        val tail = if (lines.size > maxLines) lines.takeLast(maxLines) else lines
        return tail.joinToString("\n")
    }

    fun clearLogs() {
        synchronized(lock) {
            getLogFiles().forEach { it.delete() }
        }
    }

    private fun log(level: Char, tag: String, msg: String) {
        val time = dateFormat.format(Date())
        val line = "$time $level/$tag: $msg"
        // Also log to logcat
        android.util.Log.println(
            when (level) { 'V' -> android.util.Log.VERBOSE; 'D' -> android.util.Log.DEBUG; 'I' -> android.util.Log.INFO; 'W' -> android.util.Log.WARN; else -> android.util.Log.ERROR },
            tag, msg
        )
        synchronized(lock) {
            try {
                val file = File(logDir, "FaceGuard_${dateFileFormat.format(Date())}.log")
                FileWriter(file, true).use { it.write("$line\n") }
            } catch (_: Exception) {}
        }
    }

    private fun cleanup(context: Context) {
        val deadline = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        logDir.listFiles { f -> f.name.startsWith("FaceGuard_") }?.forEach { file ->
            if (file.lastModified() < deadline) file.delete()
        }
    }
}
