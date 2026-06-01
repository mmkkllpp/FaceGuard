package com.faceguard.face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.faceguard.data.AppPreferences
import com.faceguard.ui.FaceGuardTheme
import com.faceguard.util.FileLogger
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FaceEnrollmentActivity : ComponentActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var prefs: AppPreferences
    private var faceEngine: FaceEngine? = null
    private val capturedEmbeddings = mutableListOf<FloatArray>()
    private val isProcessing = AtomicBoolean(false)

    private var cameraGranted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        FileLogger.i("Enroll", "相机权限: ${if (granted) "已授权" else "被拒绝"}")
        if (!granted) { Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        FileLogger.i("Enroll", "人脸录入界面启动")

        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        FileLogger.i("Enroll", "相机权限状态: $cameraGranted")
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)

        faceEngine = FaceEngine(this).also { engine ->
            val modelFile = ModelDownloadManager(this).getModelFile()
            if (modelFile.exists()) {
                engine.loadModel(ArcFaceSession.load(modelFile))
                FileLogger.i("Enroll", "ArcFace 模型已加载")
            } else {
                FileLogger.w("Enroll", "ArcFace 模型不存在，使用降级模式")
            }
        }

        setContent { FaceGuardTheme { EnrollmentScreen() } }
    }

    @Composable
    fun EnrollmentScreen() {
        val context = LocalContext.current
        var state by remember { mutableStateOf(EnrollState.IDLE) }
        var samples by remember { mutableIntStateOf(0) }
        var statusText by remember { mutableStateOf("") }
        val required = 5

        Box(Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // TopBar
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Text("人脸录入", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Camera area
                Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp).clip(RoundedCornerShape(16.dp))) {
                    if (cameraGranted && state != EnrollState.DONE) {
                        CameraPreview(
                            onFaceDetected = { emb ->
                                if (state == EnrollState.CAPTURING && !isProcessing.get()) {
                                    isProcessing.set(true)
                                    capturedEmbeddings.add(emb)
                                    samples = capturedEmbeddings.size
                                    FileLogger.i("Enroll", "捕获样本 $samples/$required")
                                    isProcessing.set(false)
                                    if (samples >= required) {
                                        state = EnrollState.DONE
                                        val avg = averageEmbeddings(capturedEmbeddings)
                                        prefs.saveFaceEmbedding(avg)
                                        FileLogger.i("Enroll", "录入完成")
                                    } else {
                                        statusText = "样本 $samples / $required"
                                    }
                                }
                            }
                        )
                        // 引导圈
                        Box(Modifier.size(200.dp).align(Alignment.Center).border(3.dp, Color(0xFF00D4AA), CircleShape))
                    } else if (state == EnrollState.DONE) {
                        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Check, "", tint = Color(0xFF00D4AA), modifier = Modifier.size(72.dp))
                            Text("录入完成!", color = Color(0xFF00D4AA), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Controls
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        EnrollState.IDLE -> {
                            Text("正脸对准圆圈", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text("采集 $required 个样本", color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { state = EnrollState.CAPTURING; statusText = "等待人脸检测..." },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text("开始录入", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
                            }
                        }
                        EnrollState.CAPTURING -> {
                            Text("样本 $samples / $required", color = Color(0xFF00D4AA), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (required > 0) samples.toFloat() / required else 0f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF00D4AA), trackColor = Color(0xFF2A2A4E))
                            Spacer(Modifier.height(8.dp))
                            Text(statusText, color = Color.Gray, fontSize = 14.sp)
                        }
                        EnrollState.DONE -> {}
                    }
                }
            }
        }
    }

    @Composable
    fun CameraPreview(onFaceDetected: (FloatArray) -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val executor = remember { Executors.newSingleThreadExecutor() }
        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        var cameraReady by remember { mutableStateOf(false) }
        val engine = faceEngine

        // AndroidView 先渲染
        AndroidView(
            factory = { ctx -> PreviewView(ctx).also { previewView = it } },
            modifier = Modifier.fillMaxSize()
        )

        // 等 previewView 可用后再初始化相机
        LaunchedEffect(previewView) {
            val pv = previewView ?: return@LaunchedEffect
            if (engine == null) { FileLogger.e("Enroll", "FaceEngine 未初始化"); return@LaunchedEffect }

            try {
                FileLogger.i("Enroll", "开始初始化相机...")
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    FileLogger.i("Enroll", "CameraProvider 获取成功")

                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor) { proxy ->
                            if (!isProcessing.compareAndSet(false, true)) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            try {
                                val bmp = imageProxyToBitmap(proxy)
                                if (bmp != null) {
                                    kotlinx.coroutines.runBlocking {
                                        val result = engine.detectAndEmbed(bmp)
                                        bmp.recycle()
                                        if (result != null && result.livenessOk) {
                                            onFaceDetected(result.embedding)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                FileLogger.e("Enroll", "分析异常: ${e.message}")
                            } finally {
                                proxy.close()
                                isProcessing.set(false)
                            }
                        } }

                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                    cameraReady = true
                    FileLogger.i("Enroll", "相机绑定成功 (前置)")
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                FileLogger.e("Enroll", "相机初始化失败: ${e.message}")
            }
        }

        if (!cameraReady) {
            Box(Modifier.fillMaxSize().background(Color(0xFF2A2A4E)), contentAlignment = Alignment.Center) {
                Text("相机加载中...", color = Color.Gray)
            }
        }
    }

    private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        return try {
            val planes = proxy.planes
            val w = proxy.width
            val h = proxy.height

            // ── YUV_420_888 → NV21 ──
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val ySize = yPlane.rowStride * h
            val uvSize = (w * h) / 2
            val nv21 = ByteArray(ySize + uvSize)

            // 复制 Y（处理 pixelStride > 1 的情况，如某些 Huawei / Pixel）
            val yBuf = ByteArray(yPlane.buffer.remaining())
            yPlane.buffer.get(yBuf)
            if (yPlane.pixelStride == 1) {
                System.arraycopy(yBuf, 0, nv21, 0, ySize)
            } else {
                var pos = 0
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        nv21[pos++] = yBuf[row * yPlane.rowStride + col * yPlane.pixelStride]
                    }
                }
            }

            // 复制 UV（NV21 顺序是 V 在前, U 在后）
            val uSize = uPlane.buffer.remaining()
            val vSize = vPlane.buffer.remaining()
            val uBuf = ByteArray(uSize)
            val vBuf = ByteArray(vSize)
            uPlane.buffer.get(uBuf)
            vPlane.buffer.get(vBuf)

            var uvPos = ySize
            val uvW = minOf(uPlane.rowStride / uPlane.pixelStride, w / 2)
            val uvH = h / 2
            for (row in 0 until uvH) {
                for (col in 0 until uvW) {
                    val src = row * uPlane.rowStride + col * uPlane.pixelStride
                    if (src < vBuf.size && src < uBuf.size && uvPos + 1 < nv21.size) {
                        nv21[uvPos++] = vBuf[src]  // V
                        nv21[uvPos++] = uBuf[src]  // U
                    }
                }
            }

            // ── NV21 → JPEG → Bitmap ──
            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, w, h), 90, out)
            val jpeg = out.toByteArray()
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null

            // 前置摄像头水平镜像翻转
            val m = android.graphics.Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
            val mirrored = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            bmp.recycle()
            mirrored
        } catch (e: Exception) {
            FileLogger.e("Enroll", "Bitmap 转换失败: ${e.message}")
            null
        }
    }

    private fun averageEmbeddings(list: List<FloatArray>): FloatArray {
        if (list.isEmpty()) return FloatArray(0)
        val sz = list[0].size; val avg = FloatArray(sz)
        for (e in list) for (i in 0 until minOf(sz, e.size)) avg[i] += e[i]
        for (i in avg.indices) avg[i] /= list.size
        return avg
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown(); faceEngine?.close() }

    enum class EnrollState { IDLE, CAPTURING, DONE }
}
