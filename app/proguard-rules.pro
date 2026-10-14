# Keep rules for release builds (code shrinking with R8).

# The 3D engine: Filament's native code calls back into these Java classes by name.
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.android.filament.**
-dontwarn io.github.sceneview.**

# Saved worlds are kotlinx.serialization JSON; keep the generated serializers and the game's data classes.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.ourlittlelife.rules.**$$serializer { *; }
-keepclassmembers class com.ourlittlelife.rules.** {
    *** Companion;
}
-keepclasseswithmembers class com.ourlittlelife.rules.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.ourlittlelife.rules.** { *; }

# The in-game updater reads latest.json into this class.
-keep class com.ourlittlelife.game.update.** { *; }

# Camp news worker, made by WorkManager by name.
-keep class com.ourlittlelife.game.notify.** { *; }
