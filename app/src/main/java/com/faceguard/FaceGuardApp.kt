package com.faceguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.faceguard.util.FileLogger
import java.io.PrintWriter
import java.io.StringWriter

class FaceGuardApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "faceguard_service"
        const val CHANNEL_ALERT = "faceguard_alert"
        const val CHANNEL_DOWNLOAD = "faceguard_download"
        lateinit var instance: FaceGuardApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        FileLogger.init(this)
        // 全局崩溃捕获
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            PrintWriter(sw).use { throwable.printStackTrace(it) }
            FileLogger.e("CRASH", "线程: ${thread.name}\n${sw.toString()}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
        FileLogger.i("FaceGuard", "应用启动 (崩溃处理器已注册)")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "监控服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "人脸检测后台服务"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "安全警报", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "检测到非录入用户时发送警报"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOAD, "模型下载", NotificationManager.IMPORTANCE_LOW).apply {
                description = "模型文件下载进度"
                setShowBadge(false)
                setSound(null, null)
            }
        )
    }
}
