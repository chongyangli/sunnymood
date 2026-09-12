package com.sunnymood.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// 注意：顶层属性按文件顺序初始化，这两个色值必须声明在 LightColors/DarkColors 之前
private val Color_White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val Color_DarkOnPrimary = androidx.compose.ui.graphics.Color(0xFF2A1A0A)

private val LightColors = lightColorScheme(
    primary = SunnyAmber,
    onPrimary = Color_White,
    background = LightBg,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColors = darkColorScheme(
    primary = SunnyAmberDark,
    onPrimary = Color_DarkOnPrimary,
    background = DarkBg,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

/** ColorOS 式大圆角：卡片 24dp、容器 28dp、按钮 20dp（§4.7） */
private val SunnyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 心晴主题：
 * - Android 12+ 跟随系统动态取色（ColorOS 上自动联动 Flux 主题色，§4.7）
 * - 不引入自定义字体，跟随系统（ColorOS 上即 OPPO Sans）
 */
@Composable
fun SunnyMoodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SunnyShapes,
        typography = SunnyTypography,
        content = content
    )
}
