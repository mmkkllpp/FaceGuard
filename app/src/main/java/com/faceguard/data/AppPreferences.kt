package com.faceguard.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.faceguard.util.FileLogger

/**
 * 加密存储 — 人脸特征、PIN、设置等敏感数据
 * AES-256 GCM 加密
 */
class AppPreferences(context: Context) {

    private val gson = Gson()
    private val tag = "AppPrefs"

    private val prefs = run {
        val mk = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "faceguard_secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── 人脸数据 ──

    fun saveFaceEmbedding(emb: FloatArray) {
        prefs.edit().putString("face_embedding", gson.toJson(emb)).apply()
        FileLogger.i(tag, "人脸特征已保存")
    }

    fun getFaceEmbedding(): FloatArray? {
        val json = prefs.getString("face_embedding", null) ?: return null
        return try {
            gson.fromJson(json, object : TypeToken<FloatArray>() {}.type)
        } catch (_: Exception) { null }
    }

    fun isFaceEnrolled(): Boolean = prefs.getString("face_embedding", null) != null

    fun clearFaceEnrollment() {
        prefs.edit().remove("face_embedding").remove("detection_logs").apply()
    }

    // ── 模型文件 ──

    var modelFileHash: String?
        get() = prefs.getString("model_hash", null)
        set(v) = prefs.edit().putString("model_hash", v).apply()

    fun isModelReady(): Boolean = prefs.getString("model_hash", null) != null

    // ── 检测设置 ──

    var detectionInterval: Int
        get() = prefs.getInt("detection_interval", 90)
        set(v) = prefs.edit().putInt("detection_interval", v).apply()

    var similarityThreshold: Float
        get() = prefs.getFloat("similarity_threshold", 0.6f)
        set(v) = prefs.edit().putFloat("similarity_threshold", v).apply()

    // ── 受限 App ──

    fun saveBlockedApps(apps: Set<String>) =
        prefs.edit().putStringSet("blocked_apps", apps).apply()

    fun getBlockedApps(): Set<String> =
        prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

    fun isAppBlocked(pkg: String): Boolean = pkg in getBlockedApps()

    // ── 限制模式 ──

    var isRestrictionActive: Boolean
        get() = prefs.getBoolean("restriction", false)
        set(v) = prefs.edit().putBoolean("restriction", v).apply()

    var tempUnlockExpiry: Long
        get() = prefs.getLong("unlock_expiry", 0L)
        set(v) = prefs.edit().putLong("unlock_expiry", v).apply()

    fun isTempUnlocked(): Boolean = System.currentTimeMillis() < tempUnlockExpiry

    // ── 锁屏 PIN ──

    var lockPin: String?
        get() = prefs.getString("lock_pin", null)
        set(v) = prefs.edit().putString("lock_pin", v).apply()

    // ── 检测日志 ──

    fun addDetectionLog(entry: DetectionLogEntry) {
        val logs = getDetectionLogs().toMutableList()
        logs.add(entry)
        if (logs.size > 200) logs.subList(0, logs.size - 200).clear()
        prefs.edit().putString("detection_logs", gson.toJson(logs)).apply()
    }

    fun getDetectionLogs(): List<DetectionLogEntry> {
        val json = prefs.getString("detection_logs", "[]") ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<DetectionLogEntry>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    var lastDetectionTime: Long
        get() = prefs.getLong("last_detect", 0L)
        set(v) = prefs.edit().putLong("last_detect", v).apply()

    // ── 兼容旧版 Device Admin 检测入口 ──

    companion object {
        /** 默认 PIN */
        const val DEFAULT_PIN = "1234"
    }
}

data class DetectionLogEntry(
    val timestamp: Long,
    val matched: Boolean,
    val similarity: Float,
    val reason: String = ""
)
