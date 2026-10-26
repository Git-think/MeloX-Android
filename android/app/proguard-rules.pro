# JavaScript bridges are invoked by name from bundled WebView assets.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# JSON/domain objects are decoded explicitly; retain enum names used in persisted settings.
-keepclassmembers enum com.lladlam.melox.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin file-level static functions are resolved by R8 as *Kt classes.
# Retain the ones that are referenced across module boundaries or via companion objects.
-keep class com.lladlam.melox.core.account.NeteaseSessionStoreKt { *; }
-keep class com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStoreKt { *; }
-keep class com.lladlam.melox.core.provider.kugou.KugouSessionStoreKt { *; }
-keep class com.lladlam.melox.core.provider.qqmusic.QQMusicApiClientKt { *; }
-keep class com.lladlam.melox.core.network.NeteaseSearchClientKt { *; }

# Android 10/EMUI crash fix: the always-composed audio-output entry must stay
# free of API-30/34 MediaRouter2 types. R8 may merge small Kotlin file facades,
# which would pull those references back into the cold-start class.
-keep class com.lladlam.melox.ui.player.MeloXAudioOutputDialogKt { *; }
-keep class com.lladlam.melox.ui.player.MeloXModernAudioOutputDialogKt { *; }

# Shizuku and optional vendor bridges are discovered through Android framework metadata.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class com.lladlam.melox.platform.xiaomi.ShizukuKeepAliveService { *; }
# Keep optional vendor references behind their guarded call sites in release builds.
-keep class com.lladlam.melox.platform.xiaomi.HyperOsFocusBridge { *; }
-keep class com.lladlam.melox.platform.xiaomi.ShizukuXmsfNetworkHelper { *; }
-keep class com.lladlam.melox.platform.lyricon.MeloXLyriconBridge { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**
-dontwarn com.apple.android.music.**

## Rules for NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# ONNX Runtime calls Java methods/fields by name from native code
# (convertToTensorInfo -> GetMethodID). R8 must not rename or remove the
# ai.onnxruntime classes or AutoSync crashes in release builds.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# SuperLyricApi talks to an optional system service through a hidden API that
# only exists at runtime on the device; the class itself is fine.
-dontwarn android.os.ServiceManager
# The SDK is an inter-app contract (other lyric apps discover these classes),
# so keep it intact through R8.
-keep class com.hchen.superlyricapi.** { *; }

# Rhino JS engine references desktop APIs that are unavailable on Android.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.AbstractScriptEngine
-dontwarn javax.script.Bindings
-dontwarn javax.script.Compilable
-dontwarn javax.script.CompiledScript
-dontwarn javax.script.Invocable
-dontwarn javax.script.ScriptContext
-dontwarn javax.script.ScriptEngine
-dontwarn javax.script.ScriptEngineFactory
-dontwarn javax.script.ScriptException
-dontwarn javax.script.SimpleBindings
-dontwarn jdk.dynalink.**
