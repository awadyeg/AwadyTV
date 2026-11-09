package com.awadytv.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colour tokens of the Liquid Glass design language (see docs/DESIGN.md). */
object AwadyColors {
    val Canvas = Color(0xFF0B0B0F)
    val CanvasRaised = Color(0xFF16161D)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x73FFFFFF)
    val TextOnFocus = Color(0xFF0B0B0F)
    val Accent = Color(0xFF0A84FF)
    val AccentSoft = Color(0xFF64A8FF)
    val Danger = Color(0xFFFF453A)
    val Success = Color(0xFF30D158)
    val Warning = Color(0xFFFFD60A)
    val Like = Color(0xFFFF375F)

    /** Flat surfaces (replaced Liquid Glass for speed on low-end TVs). Opaque so nothing behind needs drawing. */
    val Surface = Color(0xFF1C1C24)
    val SurfaceClear = Color(0xCC111118)
    val Hairline = Color(0x1FFFFFFF)
    val LogoTile = Color(0xFF2A2A34)

    val GlassRegular = Color(0x24FFFFFF) // 14 %
    val GlassClear = Color(0x14FFFFFF) // 8 %
    val GlassFocused = Color(0xE6FFFFFF) // tvOS focused control: near-solid white
    val GlassScrim = Color(0x8C0B0B0F)
    val StrokeHighlight = Color(0x73FFFFFF) // 45 %
    val StrokeFade = Color(0x0DFFFFFF) // 5 %
    val Shadow = Color(0x59000000) // 35 %
    val FocusBorder = Color(0xF2FFFFFF)
}

object AwadyDimens {
    /** Overscan-safe area (plan 5.1). */
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp

    val RadiusSmall = 12.dp
    val RadiusCard = 16.dp
    val RadiusMedium = 20.dp
    val RadiusLarge = 24.dp
    val RadiusXL = 28.dp

    val TabBarHeight = 52.dp
    val TabBarCollapsedHeight = 6.dp
    val ContentTop = 96.dp

    val PosterWidth = 132.dp
    val PosterHeight = 198.dp
    val LandscapeWidth = 248.dp
    val LandscapeHeight = 140.dp
    val ShelfSpacing = 20.dp
    val SideRailWidth = 300.dp
}

object AwadyMotion {
    const val FOCUS_SCALE = 1.06f
    const val FOCUS_MS = 120
    const val BUTTON_FOCUS_SCALE = 1.06f
    const val DIMMED_ALPHA = 0.85f
    const val TILT_DEGREES = 4f
    const val TILT_MS = 200
    const val SETTLE_MS = 260
    const val SPECULAR_MS = 750
    const val SHELF_ENTER_MS = 350
    const val BACKDROP_COLOR_MS = 600
    const val SCREEN_TRANSITION_MS = 300
    val SHELF_ENTER_OFFSET = 24.dp

    /** Apple-style lift spring: damping 0.7, stiffness 400 - settles without a second bounce. */
    fun <T> liftSpring() = spring<T>(dampingRatio = 0.7f, stiffness = 400f)

    fun <T> gentleSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}
