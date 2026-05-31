package com.faceguard.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faceguard.admin.DeviceAdminReceiver
import com.faceguard.data.AppPreferences
import com.faceguard.util.FileLogger

/**
 * 锁屏界面 — 检测到非录入用户时显示
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        FileLogger.w("LockScreen", "锁屏界面已显示")

        setContent {
            FaceGuardTheme { LockScreen() }
        }
    }

    @Composable
    fun LockScreen() {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Box(Modifier.fillMaxSize().background(Color(0xFF0D0D1A)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, "锁定", tint = Color(0xFFFF6B6B), modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(24.dp))
                Text("设备已锁定", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("检测到非录入用户，请输入 PIN 解锁", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = false },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D4AA), unfocusedBorderColor = Color(0xFF2A2A4E),
                        focusedLabelColor = Color(0xFF00D4AA), cursorColor = Color(0xFF00D4AA)
                    )
                )
                if (error) Text("PIN 错误", color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val saved = prefs.lockPin ?: AppPreferences.DEFAULT_PIN
                        if (pin == saved) {
                            prefs.isRestrictionActive = false
                            prefs.tempUnlockExpiry = System.currentTimeMillis() + 5 * 60 * 1000
                            FileLogger.i("LockScreen", "PIN 解锁成功")
                            Toast.makeText(context, "已解锁 5 分钟", Toast.LENGTH_SHORT).show()
                            finish()
                        } else { error = true }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA))
                ) { Text("解锁", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(12.dp))
                Text("默认 PIN: 1234", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }

    override fun onBackPressed() { /* 禁止返回 */ }
}
