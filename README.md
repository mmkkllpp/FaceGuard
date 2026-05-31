# FaceGuard 🥕

**你的脸就是你的密码。** 定时检测前置摄像头，非录入用户自动进入限制模式。

## 功能特点

- **人脸识别** — ML Kit 检测人脸 + ArcFace embedding (高精度)
- **模型在 App 内下载** — 不再打包进 APK，点击即可下载
- **定时检测** — 每 90 秒自动检测当前持机人
- **应用限制** — 辅助功能服务拦截未授权应用
- **设备锁定** — 全屏锁定界面 + PIN 解锁
- **防卸载** — 设备管理员权限保护
- **活体检测** — 防照片/视频攻击
- **本地日志** — 调试日志写入文件，方便排查问题
- **Material 3 设计** — 深色模式，中文界面
- **兼容 Android 14-16**
- **自更新友好** — 自身更新不受防卸载限制

## 首次使用

1. 安装 APK，打开 App
2. **权限设置** → 按提示开启所有权限
3. **下载识别模型**（可选但推荐，不下载自动降级）
4. **录入人脸** → 采集 5 个样本
5. **启用限制模式** → 开始保护

## 技术架构

```
FaceGuard/
├── face/           # 人脸检测 + 特征提取 + 模型下载
│   ├── FaceEngine.kt
│   ├── ArcFaceSession.kt
│   ├── FaceEnrollmentActivity.kt
│   ├── ModelDownloadManager.kt
│   └── ModelDownloadActivity.kt
├── service/        # 后台服务
│   ├── FaceGuardService.kt          # 前台定时检测
│   └── AppBlockerAccessibilityService.kt  # 应用拦截
├── admin/          # 设备管理员
├── ui/             # Material 3 界面
│   ├── MainActivity.kt
│   ├── LockScreenActivity.kt
│   ├── PermissionsActivity.kt
│   ├── LogViewerActivity.kt
│   └── Theme.kt
├── data/           # 加密集加密存储
└── util/           # 文件日志工具
```

## 构建

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 许可

MIT
