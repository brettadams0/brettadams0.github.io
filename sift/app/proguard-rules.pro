# Room and Hilt generate code that reflection reaches; the libraries ship their
# own rules. Kotlinx serialization needs its serializers kept.
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class dev.sift.**$$serializer { *; }
-keepclassmembers class dev.sift.** { *** Companion; }
-keepclasseswithmembers class dev.sift.** { kotlinx.serialization.KSerializer serializer(...); }
