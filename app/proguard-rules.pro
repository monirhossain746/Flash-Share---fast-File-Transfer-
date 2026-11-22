# FlashShare R8 / ProGuard Rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep ZXing QR code classes used at runtime
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep DataStore preferences
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep Google Mobile Ads & Firebase Remote Config metadata/callbacks
-keep public class com.google.android.gms.ads.** { public *; }
-keep class com.google.firebase.remoteconfig.** { *; }
