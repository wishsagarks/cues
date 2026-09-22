# Routines and sessions are persisted as JSON via kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cues.core.model.** { *; }
-keep,includedescriptorclasses class com.cues.core.**$$serializer { *; }
