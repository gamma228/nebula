# Keep libbox Gomobile classes and JNI proxies
-keep class io.nekohasekai.libbox.** { *; }
-keep interface io.nekohasekai.libbox.** { *; }
-keep class go.** { *; }
-keep interface go.** { *; }

# Keep Room generated implementations
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SnakeYAML engine classes if reflected
-keep class org.snakeyaml.engine.** { *; }
