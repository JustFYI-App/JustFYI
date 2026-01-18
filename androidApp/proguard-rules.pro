# ProGuard Rules for Just FYI App

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Serializable classes in the app
-keep,includedescriptorclasses class app.justfyi.**$$serializer { *; }
-keepclassmembers class app.justfyi.** {
    *** Companion;
}
-keepclasseswithmembers class app.justfyi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used for serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Metro DI generated classes
-keep class app.justfyi.di.** { *; }

# Keep navigation route classes
-keep class app.justfyi.presentation.navigation.** { *; }

# General Android rules
-keepattributes Signature
-keepattributes Exceptions

# Firebase (for future use)
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
