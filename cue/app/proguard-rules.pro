# MediaPipe and ML Kit both reflect into their own generated classes.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }

# kotlinx.serialization keeps the generated serializers on the companion.
-keepclassmembers class dev.cue.** {
    *** Companion;
}
-keepclasseswithmembers class dev.cue.** {
    kotlinx.serialization.KSerializer serializer(...);
}
