# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Xposed module (libxposed API 102)
# ---------------------------------------------------------------------------

# The framework looks the entry class up by name through this file, so the class
# names inside it have to be rewritten in step with obfuscation.
-adaptresourcefilecontents META-INF/xposed/java_init.list

# The entry class is instantiated reflectively by the framework: keep the
# no-argument constructor reachable even when the class itself is obfuscated.
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# libxposed `api` is compileOnly: the framework supplies it at runtime.
-dontwarn io.github.libxposed.api.**
