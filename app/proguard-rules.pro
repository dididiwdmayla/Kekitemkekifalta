# Libraries used here (Room, WorkManager, Navigation, kotlinx.serialization) ship their own
# consumer rules. These are belt-and-braces for our own serialized classes.

# Backup file DTOs and navigation routes are (de)serialized by kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class com.kekitemkekifalta.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kekitemkekifalta.**$$serializer { *; }

# Keep line numbers so crash traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
