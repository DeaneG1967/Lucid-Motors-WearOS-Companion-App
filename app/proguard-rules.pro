# Lucid Cars (Wear OS) ProGuard / R8 Rules
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# Keep data models and gRPC / REST transport models
-keep class com.droid.lucidgravitywatch.model.** { *; }
-keep class com.droid.lucidgravitywatch.network.** { *; }

# OkHttp3 & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
