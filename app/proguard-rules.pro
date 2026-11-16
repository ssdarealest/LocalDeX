# ProGuard rules for LocalDeX
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
