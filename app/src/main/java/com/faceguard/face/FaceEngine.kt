package com.faceguard.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.faceguard.util.FileLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.sqrt
import kotlin.coroutines.resume

/**
 * 人脸检测 + 特征提取引擎
 * ML Kit 检测人脸 → ArcFace 提取 embedding
 * ArcFace 不可用时降级为 landmark 比对
 */
class FaceEngine(private val context: Context) {

    private val mlKit by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        FaceDetection.getClient(opts)
    }

    private var arcFace: ArcFaceSession? = null
    private val tag = "FaceEngine"

    /** 加载 ArcFace 模型 */
    fun loadModel(session: ArcFaceSession?) {
        arcFace = session
        if (session != null) FileLogger.i(tag, "ArcFace 模型已加载")
        else FileLogger.w(tag, "ArcFace 未加载，使用降级模式")
    }

    /** 检查是否有 ArcFace 模型 */
    fun hasArcFace(): Boolean = arcFace != null

    /** 检测人脸并提取特征 */
    suspend fun detectAndEmbed(bitmap: Bitmap): FaceResult? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detect(image)
            if (faces.isEmpty()) return null

            val face = faces.first()
            val crop = cropFace(bitmap, face.boundingBox)
            val embedding = arcFace?.getEmbedding(crop) ?: extractLandmarkEmbedding(face)

            FaceResult(
                embedding = embedding,
                livenessOk = checkLiveness(face)
            )
        } catch (e: Exception) {
            FileLogger.e(tag, "检测失败: ${e.message}")
            null
        }
    }

    /** 比较两个特征向量的余弦相似度 */
    fun compare(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        return if (a.size >= 128) cosineSimilarity(a, b) else landmarkSimilarity(a, b)
    }

    private suspend fun detect(image: InputImage) = suspendCancellableCoroutine { cont ->
        mlKit.process(image)
            .addOnSuccessListener { faces -> if (cont.isActive) cont.resume(faces) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap {
        val pad = 0.3f; val pw = (box.width() * pad).toInt(); val ph = (box.height() * pad).toInt()
        val l = maxOf(0, box.left - pw); val t = maxOf(0, box.top - ph)
        val r = minOf(bitmap.width, box.right + pw); val b = minOf(bitmap.height, box.bottom + ph)
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    private fun checkLiveness(face: com.google.mlkit.vision.face.Face): Boolean {
        val leftOk = (face.leftEyeOpenProbability ?: 1f) > 0.5f
        val rightOk = (face.rightEyeOpenProbability ?: 1f) > 0.5f
        val headOk = kotlin.math.abs(face.headEulerAngleY) < 30f
        return leftOk && rightOk && headOk
    }

    private fun extractLandmarkEmbedding(face: com.google.mlkit.vision.face.Face): FloatArray {
        val box = face.boundingBox
        val emb = mutableListOf<Float>()
        for (lm in face.allLandmarks) {
            emb.add((lm.position.x - box.left).toFloat() / box.width())
            emb.add((lm.position.y - box.top).toFloat() / box.height())
        }
        return emb.toFloatArray()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val d = sqrt(na) * sqrt(nb)
        return if (d > 0f) dot / d else 0f
    }

    private fun landmarkSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var sum = 0f; for (i in a.indices) { val d = a[i] - b[i]; sum += d * d }
        return (1f - sqrt(sum) * 2f).coerceIn(0f, 1f)
    }

    fun close() { mlKit.close(); arcFace?.close() }
}

data class FaceResult(
    val embedding: FloatArray,
    val livenessOk: Boolean = true
)
