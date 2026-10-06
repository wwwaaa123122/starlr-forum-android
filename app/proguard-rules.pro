# Kotlin/Android
-keep class com.starlr.app.data.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.starlr.app.**$$serializer { *; }
-keepclassmembers class com.starlr.app.** { *** Companion; }
-keepclasseswithmembers class com.starlr.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
