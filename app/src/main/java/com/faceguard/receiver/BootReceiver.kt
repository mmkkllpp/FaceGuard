package com.faceguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.faceguard.util.FileLogger
import com.faceguard.service.FaceGuardService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                FileLogger.i("BootReceiver", "系统启动完成，重启服务")
                FaceGuardService.start(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                FileLogger.i("BootReceiver", "应用更新完成，重启服务")
                FaceGuardService.start(context)
            }
        }
    }
}
