# ---- Kotlin serialization ----
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**

-keepclassmembers class github.franco.chat.ai.data.** {
    *** Companion;
}
-keepclasseswithmembers class github.franco.chat.ai.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class github.franco.chat.ai.data.**$$serializer { *; }

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ---- Optimizacion agresiva ----
-repackageclasses
-allowaccessmodification
-optimizationpasses 5
