# ProGuard / R8 Keep Rules for CryptoNote

# Keep Room DB entities and DAOs
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep Kotlinx Serialization DTOs
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class *
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    *** serializer(...);
}

# Keep Retrofit and OkHttp Models
-keep class com.example.cryptnote.data.remote.model.** { *; }
-keepclassmembers class * extends retrofit2.Callback { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep Bouncy Castle & Security Providers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.google.errorprone.annotations.**
-keep class com.example.cryptnote.security.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Keep Hilt and WorkManager Worker Factories
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }

# Strip android.util.Log calls in release builds to prevent leaking keys/data
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
