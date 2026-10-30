# Preserve Gson DTO names, generic signatures and runtime annotations.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.pokemontcg.sammlung.data.api.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }

# Retrofit service interfaces and annotations are inspected at runtime.
-keep interface com.pokemontcg.sammlung.data.api.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**

# Room generated database implementations and entities.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.pokemontcg.sammlung.data.local.entities.** { *; }
-keep class com.pokemontcg.sammlung.data.local.user.** { *; }
-keep class com.pokemontcg.sammlung.data.export.** { *; }
-dontwarn androidx.room.paging.**

# Keep only the ML Kit text-recognition API surface used by the scanner.
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.vision.text.**
