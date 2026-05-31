package com.faceguard.face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
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
import com.faceguard.ui.FaceGuardTheme
import com.faceguard.util.FileLogger
import java.util.concurrent.Executors

/**
 * 人脸录入界面
 */
class FaceEnrollmentActivity : ComponentActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var preferences: com.faceguard.data.AppPreferences
    private var faceEngine by mutableStateOf<FaceEngine?>(null)
    private var isProcessing = false
    private var capturedEmbeddings = mutableListOf<FloatArray>()
    private var cameraGranted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (!granted) { Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = com.faceguard.data.AppPreferences(this)

        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)

        // 初始化 FaceEngine
        faceEngine = FaceEngine(this).also { engine ->
            val modelFile = com.faceguard.face.ModelDownloadManager(this).getModelFile()
            if (modelFile.exists()) {
                engine.loadModel(ArcFaceSession.load(modelFile))
            }
        }

        setContent {
            FaceGuardTheme {
                EnrollmentScreen(
                    hasCamera = cameraGranted,
                    onEnroll = { onEnrollmentComplete() },
                    onBack = { finish() }
                )
            }
        }
    }

    @Composable
    fun EnrollmentScreen(hasCamera: Boolean, onEnroll: (Boolean) -> Unit, onBack: () -> Unit) {
        var state by remember { mutableStateOf(EnrollState.IDLE) }
        var samples by remember { mutableIntStateOf(0) }
        val required = 5

        Box(Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // TopBar
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Text("人脸录入", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // 预览区
                Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp).clip(RoundedCornerShape(16.dp))) {
                    if (hasCamera && state != EnrollState.DONE) {
                        CameraPreview(
                            faceEngine = faceEngine,
                            onFaceDetected = { emb ->
                                if (state == EnrollState.CAPTURING && !isProcessing) {
                                    isProcessing = true
                                    capturedEmbeddings.add(emb)
                                    samples = capturedEmbeddings.size
                                    isProcessing = false
                                    if (samples >= required) {
                                        state = EnrollState.DONE
                                        val avg = averageEmbeddings(capturedEmbeddings)
                                        preferences.saveFaceEmbedding(avg)
                                        FileLogger.i("Enroll", "录入完成，${samples}个样本")
                                        onEnroll(true)
                                    }
                                }
                            }
                        )
                        // 圆形引导框
                        Box(Modifier.size(200.dp).align(Alignment.Center).border(3.dp, Color(0xFF00D4AA), CircleShape))
                    } else if (state == EnrollState.DONE) {
                        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Check, "", tint = Color(0xFF00D4AA), modifier = Modifier.size(72.dp))
                            Text("录入完成!", color = Color(0xFF00D4AA), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("需要相机权限", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 底部控制
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        EnrollState.IDLE -> {
                            Text("正脸对准圆圈", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text("采集 $required 个样本", color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { state = EnrollState.CAPTURING },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text("开始录入", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
                            }
                        }
                        EnrollState.CAPTURING -> {
                            Text("样本 $samples / $required", color = Color(0xFF00D4AA), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { samples.toFloat() / required },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF00D4AA), trackColor = Color(0xFF2A2A4E)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("保持不动...", color = Color.Gray, fontSize = 14.sp)
                        }
                        EnrollState.DONE -> { /* 显示完成 */ }
                    }
                }
            }
        }
    }

    /** 实时相机预览 + 人脸分析 */
    @Composable
    fun CameraPreview(faceEngine: FaceEngine?, onFaceDetected: (FloatArray) -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val executor = remember { Executors.newSingleThreadExecutor() }

        AndroidView(
            factory = { ctx -> PreviewView(ctx) },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(faceEngine) {
            val provider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor) { proxy ->
                    if (isProcessing) { proxy.close(); return@setAnalyzer }
                    @androidx.camera.core.ExperimentalGetImage
                    val mediaImage = proxy.image ?: run { proxy.close(); return@setAnalyzer }
                    val bitmap = imageProxyToBitmap(proxy) ?: run { proxy.close(); return@setAnalyzer }
                    proxy.close()
                    val engine = faceEngine ?: return@setAnalyzer
                    kotlinx.coroutines.runBlocking {
                        val result = engine.detectAndEmbed(bitmap)
                        bitmap.recycle()
                        if (result != null && result.livenessOk) {
                            onFaceDetected(result.embedding)
                        }
                    }
                } }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {
                FileLogger.e("CameraPreview", "相机绑定失败: ${e.message}")
            }
        }
    }

    private fun imageProxyToBitmap(proxy: androidx.camera.core.ImageProxy): Bitmap? {
        return try {
            val buffer = proxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()); buffer.get(bytes)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val m = android.graphics.Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
            val mirrored = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            bmp.recycle(); mirrored
        } catch (_: Exception) { null }
    }

    private fun averageEmbeddings(list: List<FloatArray>): FloatArray {
        if (list.isEmpty()) return FloatArray(0)
        val sz = list[0].size; val avg = FloatArray(sz)
        for (e in list) for (i in 0 until minOf(sz, e.size)) avg[i] += e[i]
        for (i in avg.indices) avg[i] /= list.size
        return avg
    }

    override fun onDestroy() {
        super.onDestroy(); cameraExecutor.shutdown(); faceEngine?.close()
    }

    enum class EnrollState { IDLE, CAPTURING, DONE }
}
