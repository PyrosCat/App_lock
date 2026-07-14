# App Lock ProGuard rules
# Keep accessibility service (referenced from manifest)
-keep class com.applock.applocker.service.** { *; }

# SQLCipher loads classes from JNI
-keep class net.zetetic.database.** { *; }

# Tink (via androidx.security.crypto) references compile-only annotations that
# aren't on the runtime classpath. Safe to ignore under R8.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**
