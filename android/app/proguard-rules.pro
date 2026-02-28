# Tink / ErrorProne (来自 androidx.security:security-crypto)
-dontwarn com.google.errorprone.annotations.**

# 【核心】Gson 需要泛型签名和注解信息
-keepattributes Signature
-keepattributes *Annotation*

# 保留 Gson TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# 数据模型 (Gson/Retrofit 序列化需要)
-keep class com.example.paperlessmeeting.domain.model.** { *; }
-keep class com.example.paperlessmeeting.data.local.ReadingProgress { *; }

# Retrofit 接口
-keep,allowobfuscation interface com.example.paperlessmeeting.data.remote.ApiService { *; }

# AndroidPdfViewer
-dontwarn com.github.barteksc.pdfviewer.**
