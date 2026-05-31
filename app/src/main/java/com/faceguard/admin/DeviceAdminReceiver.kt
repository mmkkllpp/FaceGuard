package com.faceguard.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.admin.DevicePolicyManager
import com.faceguard.util.FileLogger

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        FileLogger.i("DeviceAdmin", "设备管理员已启用")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        FileLogger.w("DeviceAdmin", "设备管理员被禁用")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "禁用设备管理员会降低 FaceGuard 的保护能力，建议保留"
    }

    companion object {
        private const val TAG = "DeviceAdmin"

        fun getComponent(context: Context): ComponentName =
            ComponentName(context, DeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponent(context))
        }

        fun lockNow(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(getComponent(context))) {
                dpm.lockNow()
                FileLogger.i(TAG, "设备已锁定")
            }
        }
    }
}
