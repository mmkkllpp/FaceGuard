package com.faceguard.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.faceguard.FaceGuardApp
import com.faceguard.data.AppPreferences
import com.faceguard.data.DetectionLogEntry
import com.faceguard.face.ArcFaceSession
import com.faceguard.face.FaceEngine
import com.faceguard.face.ModelDownloadManager
import com.faceguard.ui.LockScreenActivity
import com.faceguard.util.FileLogger
import kotlinx.coroutines.*

/**
 * 前台监控服务 — 每 N 秒采集前置摄像头检测用户身份
 */
class FaceGuardService : LifecycleService() {

    private lateinit var prefs: AppPreferences
    private var faceEngine: FaceEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var detectionJob: Job? = null
    private val tag = "FaceGuardSvc"
    private var notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        initFaceEngine()
        startForeground(notificationId, buildNotif("启动中..."))
        FileLogger.i(tag, "服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLoop()
            ACTION_STOP -> { stopLoop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            ACTION_CHECK -> scope.launch { detectOnce() }
        }
        return START_STICKY
    }

    private fun startLoop() {
        detectionJob?.cancel()
        val ms = prefs.detectionInterval * 1000L
        detectionJob = scope.launch {
            while (isActive) { detectOnce(); delay(ms) }
        }
        FileLogger.i(tag, "定时检测已启动 (${prefs.detectionInterval}s)")
    }

    private fun stopLoop() { detectionJob?.cancel(); detectionJob = null }

    private suspend fun detectOnce() {
        if (!prefs.isFaceEnrolled()) { updateNotif("未录入人脸"); return }
        try {
            val bmp = captureFrame() ?: return
            val result = faceEngine?.detectAndEmbed(bmp)
            bmp.recycle()
            if (result == null) { handleResult(false, 0f, "no_face"); return }
            if (!result.livenessOk) { handleResult(false, 0f, "liveness"); return }

            val enrolled = prefs.getFaceEmbedding() ?: return
            val sim = faceEngine?.compare(enrolled, result.embedding) ?: return
            handleResult(sim >= prefs.similarityThreshold, sim)
        } catch (e: Exception) {
            FileLogger.e(tag, "检测异常: ${e.message}")
        }
    }

    private suspend fun handleResult(matched: Boolean, sim: Float, reason: String = "") {
        prefs.lastDetectionTime = System.currentTimeMillis()
        prefs.addDetectionLog(DetectionLogEntry(System.currentTimeMillis(), matched, sim, reason))

        withContext(Dispatchers.Main) {
            if (matched) {
                if (prefs.isRestrictionActive) {
                    prefs.isRestrictionActive = false
                    prefs.tempUnlockExpiry = 0L
                    updateNotif("用户已验证，恢复正常")
                    FileLogger.i(tag, "人脸匹配 ($sim)，解除限制")
                } else updateNotif("监控中...")
            } else {
                if (!prefs.isRestrictionActive) {
                    prefs.isRestrictionActive = true
                    updateNotif("⚠️ 非录入用户，已启动限制模式")
                    startActivity(Intent(this@FaceGuardService, LockScreenActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        putExtra("source", "face_mismatch")
                    })
                    FileLogger.w(tag, "人脸不匹配 ($sim)，已激活限制")
                } else updateNotif("⚠️ 限制模式激活中")
            }
        }
    }

    private suspend fun captureFrame(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            val providerFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val capture = androidx.camera.core.ImageCapture.Builder()
                    .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val exec = java.util.concurrent.Executors.newSingleThreadExecutor()
                capture.takePicture(exec, object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        provider.unbindAll()
                        val bmp = imageProxyToBitmap(image)
                        image.close()
                        if (cont.isActive) cont.resume(bmp) {}
                    }
                    override fun onError(e: androidx.camera.core.ImageCaptureException) {
                        provider.unbindAll()
                        FileLogger.e(tag, "拍照失败: ${e.message}")
                        if (cont.isActive) cont.resume(null) {}
                    }
                })
                try { provider.bindToLifecycle(this@FaceGuardService, androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA, capture) }
                catch (e: Exception) { provider.unbindAll(); if (cont.isActive) cont.resume(null) {} }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) { if (cont.isActive) cont.resume(null) {} }
    }

    private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap? {
        return try {
            val buf = image.planes[0].buffer; val bytes = ByteArray(buf.remaining()); buf.get(bytes)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val m = android.graphics.Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
            val mirrored = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true); bmp.recycle(); mirrored
        } catch (_: Exception) { null }
    }

    private fun initFaceEngine() {
        try {
            val engine = FaceEngine(this)
            val modelFile = ModelDownloadManager(this).getModelFile()
            if (modelFile.exists()) engine.loadModel(ArcFaceSession.load(modelFile))
            faceEngine = engine
            FileLogger.i(tag, "FaceEngine 初始化完成")
        } catch (e: Exception) {
            FileLogger.e(tag, "FaceEngine 初始化失败: ${e.message}")
        }
    }

    private fun buildNotif(text: String): Notification {
        return NotificationCompat.Builder(this, FaceGuardApp.CHANNEL_SERVICE)
            .setContentTitle("FaceGuard")
            .setContentText(text).setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, com.faceguard.ui.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, buildNotif(text))
    }

    override fun onDestroy() { scope.cancel(); faceEngine?.close(); super.onDestroy(); FileLogger.i(tag, "服务销毁") }

    companion object {
        const val ACTION_START = "com.faceguard.START"
        const val ACTION_STOP = "com.faceguard.STOP"
        const val ACTION_CHECK = "com.faceguard.CHECK"

        fun start(c: Context) { c.startForegroundService(Intent(c, FaceGuardService::class.java).apply { action = ACTION_START }) }
        fun stop(c: Context) { c.startService(Intent(c, FaceGuardService::class.java).apply { action = ACTION_STOP }) }
        fun checkNow(c: Context) { c.startService(Intent(c, FaceGuardService::class.java).apply { action = ACTION_CHECK }) }
    }
}
