# ============================================================================
# V3SP3R / Dai — ProGuard / R8 rules for release builds
# ============================================================================

# ── General Android ─────────────────────────────────────────────────────────

# Keep annotations used at runtime
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Keep Parcelable creators
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enum values (used by serialization, Room, etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Kotlin / Coroutines ────────────────────────────────────────────────────

-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ── Kotlinx Serialization ──────────────────────────────────────────────────

-keepattributes RuntimeVisibleAnnotations

# Keep serializers and serializable classes
-keep,includedescriptorclasses class com.dai.flipper.**$$serializer { *; }
-keepclassmembers class com.dai.flipper.** {
    *** Companion;
}
-keepclasseswithmembers class com.dai.flipper.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn kotlinx.serialization.**

# ── OkHttp ──────────────────────────────────────────────────────────────────

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Protobuf ────────────────────────────────────────────────────────────────

-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ── Room Database ───────────────────────────────────────────────────────────

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Hilt / Dagger ───────────────────────────────────────────────────────────

-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class * extends dagger.internal.Factory
-keep class * extends dagger.hilt.internal.GeneratedComponent

# ── Coil (image loading) ───────────────────────────────────────────────────

-dontwarn coil.**
-keep class coil.** { *; }

# ── USB Serial ──────────────────────────────────────────────────────────────

-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# ── AndroidX Security Crypto ───────────────────────────────────────────────

-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# ── Compose ─────────────────────────────────────────────────────────────────

# Compose is handled by R8 automatically with AGP 8+, but keep stability:
-dontwarn androidx.compose.**

# ── App-specific: keep data classes used in serialization / API ─────────────

-keep class com.dai.flipper.ai.** { *; }
-keep class com.dai.flipper.data.** { *; }
-keep class com.dai.flipper.voice.VoiceOption { *; }
-keep class com.dai.flipper.voice.TtsState { *; }
-keep class com.dai.flipper.voice.TtsState$* { *; }
