package com.ourlittlelife.game.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens from the Our Little Life design system. Use these, never raw hex values in screens. */
@Immutable
data class OllColors(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val onFill: Color,
    val peach: Color,
    val peachText: Color,
    val leaf: Color,
    val sky: Color,
    val honey: Color,
    val rose: Color,
    val berry: Color,
    val focus: Color,
    val popShadow: Color,
    val isNight: Boolean,
)

val DayColors = OllColors(
    surface = Color(0xFFF3F7F4),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFE6EEE8),
    line = Color(0xFFCBD8CF),
    ink = Color(0xFF24302A),
    inkMuted = Color(0xFF5B6B62),
    onFill = Color(0xFF24302A),
    peach = Color(0xFFF28C6B),
    peachText = Color(0xFFB34A2C),
    leaf = Color(0xFF7DBA8A),
    sky = Color(0xFF8DB8E8),
    honey = Color(0xFFF2C14E),
    rose = Color(0xFFEFA3B5),
    berry = Color(0xFF9E2B4B),
    focus = Color(0xFF2F5FA8),
    popShadow = Color(0x4724302A),
    isNight = false,
)

val NightColors = OllColors(
    surface = Color(0xFF1A2130),
    surfaceRaised = Color(0xFF242C3D),
    surfaceSunken = Color(0xFF141A26),
    line = Color(0xFF364056),
    ink = Color(0xFFEEF1EA),
    inkMuted = Color(0xFFA9B3AE),
    onFill = Color(0xFF24302A),
    peach = Color(0xFFF59E80),
    peachText = Color(0xFFFFB49A),
    leaf = Color(0xFF86C493),
    sky = Color(0xFF9CC3EE),
    honey = Color(0xFFF5CB62),
    rose = Color(0xFFF2B0C0),
    berry = Color(0xFFF08AA6),
    focus = Color(0xFF9CC3EE),
    popShadow = Color(0x80000000),
    isNight = true,
)
