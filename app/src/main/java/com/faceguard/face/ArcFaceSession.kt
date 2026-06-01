package com.faceguard.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.faceguard.util.FileLogger
import java.io.File
import java.nio.FloatBuffer

/**
 * ArcFace ONNX 推理引擎
 * 输入: NHWC float32 [1,112,112,3] ∈ [-1,1]
 * 输出: float32 [1,512] 归一化特征向量
 */
class ArcFaceSession private constructor(private val ortSession: OrtSession) {

    private val ortEnv = OrtEnvironment.getEnvironment()
    private val inputName: String? = ortSession.inputNames?.firstOrNull()

    fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        val name = inputName ?: return null
        return try {
            val tensor = preprocess(faceBitmap)
            val results = ortSession.run(mapOf(name to tensor))
            val output = results.first().value
            val emb: FloatArray = when (output) {
                is OnnxTensor -> {
                    val buf = (output as OnnxTensor).asFloatBuffer()
                    val arr = FloatArray(buf.remaining())
                    buf.get(arr)
                    arr
                }
                is Array<*> -> (output as Array<FloatArray>)[0]
                is FloatArray -> output as FloatArray
                else -> {
                    FileLogger.w("ArcFace", "未知输出类型: ${output.javaClass.name}")
                    return null
                }
            }
            l2Normalize(emb)
        } catch (e: Exception) {
            FileLogger.e("ArcFace", "推理失败: ${e.message}")
            null
        }
    }

    fun close() { ortSession.close() }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        val buf = FloatBuffer.allocate(SIZE * SIZE * 3)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val p = pixels[y * SIZE + x]
            buf.put(((p shr 16) and 0xFF).toFloat() / 128f - 1f)
            buf.put(((p shr 8) and 0xFF).toFloat() / 128f - 1f)
            buf.put((p and 0xFF).toFloat() / 128f - 1f)
        }
        buf.rewind()
        return OnnxTensor.createTensor(ortEnv, buf, longArrayOf(1, SIZE.toLong(), SIZE.toLong(), 3))
    }

    private fun l2Normalize(a: FloatArray): FloatArray {
        var n = 0f; for (v in a) n += v * v; n = kotlin.math.sqrt(n)
        if (n > 0f) for (i in a.indices) a[i] /= n
        return a
    }

    companion object {
        private const val SIZE = 112
        private const val TAG = "ArcFace"

        /**
         * 从本地文件加载模型。返回 null 表示加载失败。
         */
        fun load(modelFile: File): ArcFaceSession? {
            return try {
                val env = OrtEnvironment.getEnvironment()
                val bytes = modelFile.readBytes()
                val session = env.createSession(bytes)
                FileLogger.i(TAG, "模型加载成功: ${modelFile.name}, 输入: ${session.inputNames}")
                ArcFaceSession(session)
            } catch (e: Exception) {
                FileLogger.e(TAG, "模型加载失败: ${e.message}")
                null
            }
        }
    }
}
