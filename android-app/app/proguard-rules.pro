# 超商卡管家 — ProGuard 規則（目前 release 未開啟 minify，保留以備日後使用）
-keep class com.chaoshangka.manager.backup.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
