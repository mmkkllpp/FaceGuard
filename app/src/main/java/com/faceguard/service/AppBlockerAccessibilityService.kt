package com.faceguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.faceguard.data.AppPreferences
import com.faceguard.util.FileLogger

/**
 * 辅助功能服务 — 实时检测前台 App，拦截受限应用
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppPreferences
    private var lastPkg: String? = null
    private var retryCount = 0
    private val tag = "A11yBlocker"

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppPreferences(this)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 200
        }
        FileLogger.i(tag, "辅助功能服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!prefs.isRestrictionActive || prefs.isTempUnlocked()) return
        val pkg = event.packageName?.toString() ?: return
        if (shouldIgnore(pkg)) return
        if (prefs.isAppBlocked(pkg)) {
            FileLogger.w(tag, "拦截受限应用: $pkg")
            if (pkg == lastPkg) {
                retryCount++
                if (retryCount >= 3) {
                    launchLockScreen()
                    retryCount = 0; return
                }
            } else { lastPkg = pkg; retryCount = 1 }
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({ if (prefs.isRestrictionActive && !prefs.isTempUnlocked()) performGlobalAction(GLOBAL_ACTION_HOME) }, 400)
        }
    }

    private fun launchLockScreen() {
        val i = Intent(this, com.faceguard.ui.LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(i)
    }

    private fun shouldIgnore(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui") return true
        if (pkg in listOf("com.android.dialer", "com.google.android.dialer", "com.android.phone")) return true
        if (pkg in listOf("com.android.launcher3", "com.google.android.apps.nexuslauncher")) return true
        if (pkg == "com.android.settings" || pkg.startsWith("com.android.server")) return true
        return false
    }

    override fun onInterrupt() { FileLogger.w(tag, "服务被中断") }
    override fun onDestroy() { FileLogger.w(tag, "服务被销毁") }

    companion object {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    }
}
