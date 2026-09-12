package com.sunnymood.app.util

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.sunnymood.app.R
import org.json.JSONArray

/**
 * 7 级心情的唯一事实源（名称 / 主题色 / 图标）。
 * 色阶 + 表情形状双重编码，色弱用户也能区分（开发文档 §4.1）。
 */
data class MoodLevelSpec(
    val level: Int,
    val name: String,
    val colorHex: Long,
    @DrawableRes val iconRes: Int
) {
    val color: Color get() = Color(colorHex)
}

object MoodSpec {

    val levels: List<MoodLevelSpec> = listOf(
        MoodLevelSpec(1, "非常差", 0xFFE85D8A, R.drawable.mood_1),
        MoodLevelSpec(2, "不太好", 0xFFF0716B, R.drawable.mood_2),
        MoodLevelSpec(3, "有点低",  0xFFF5A46B, R.drawable.mood_3),
        MoodLevelSpec(4, "一般",   0xFFF5C26B, R.drawable.mood_4),
        MoodLevelSpec(5, "还不错", 0xFFB5D46A, R.drawable.mood_5),
        MoodLevelSpec(6, "很好",   0xFF7ED3A0, R.drawable.mood_6),
        MoodLevelSpec(7, "非常好", 0xFF34C77B, R.drawable.mood_7),
    )

    fun spec(level: Int): MoodLevelSpec =
        levels.firstOrNull { it.level == level } ?: levels[3]

    fun name(level: Int): String = spec(level).name

    @DrawableRes
    fun icon(level: Int): Int = spec(level).iconRes

    fun color(level: Int): Color = spec(level).color

    fun colorHex(level: Int): Long = spec(level).colorHex

    /** 身体感觉固定词表（10 项，面向职场人；P1 支持自定义增删，开发文档 §4.3） */
    val BODY_FEELINGS: List<String> = listOf(
        "疲惫", "困倦", "不想动", "没睡好", "头昏脑涨",
        "肩颈酸痛", "眼睛干涩", "胃部不适", "精力充沛", "身体轻盈"
    )

    /** List -> JSON 数组字符串（存 Room）；空列表返回 null，保持记录干净 */
    fun encodeFeelings(items: List<String>): String? =
        items.filter { it in BODY_FEELINGS }
            .takeIf { it.isNotEmpty() }
            ?.let { JSONArray(it).toString() }

    /** JSON 数组字符串 -> List；容错解析，异常返回空 */
    fun decodeFeelings(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        }.getOrDefault(emptyList())
    }
}
