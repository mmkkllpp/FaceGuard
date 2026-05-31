# FaceGuard ProGuard

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

# Gson
-keepattributes Signature, *Annotation*
-keep class com.faceguard.data.** { *; }
-keep class com.google.gson.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
