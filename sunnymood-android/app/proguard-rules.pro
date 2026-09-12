# 心晴 SunnyMood 混淆规则
# Room / Compose / DataStore 均自带 consumer rules，以下仅补充业务侧需要

# 保留 Room 实体（反射用于表结构映射）
-keep class com.sunnymood.app.data.db.** { *; }

# 崩溃日志可读性：保留行号与源文件名
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
