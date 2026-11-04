# R8 rules of the release build (docs/11 §11.2). The libraries (Room, Hilt, WorkManager, Navigation, Compose,
# kotlinx.serialization) ship their own consumer rules, so only what they cannot know is listed here.

# Navigation routes are @Serializable and their serializers are looked up at run time (type-safe navigation).
# Without these rules a renamed Companion or $$serializer breaks every navigate() in the release build.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ru.bpdiary.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.bpdiary.**$$serializer { *; }

# WorkManager stores the class name of a worker in its database. Obfuscated names would change with the next
# release and orphan the daily watchdog and the reschedule job queued by the previous version.
-keepnames class ru.bpdiary.data.reminders.*Worker
-keepnames class ru.bpdiary.feature.transfer.*Worker

# Readable stack traces in Play Console (Android vitals) come from the mapping file of the build, but a line number
# is only there when this is kept.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
