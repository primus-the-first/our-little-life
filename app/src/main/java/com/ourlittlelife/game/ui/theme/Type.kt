package com.ourlittlelife.game.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// TODO: swap to Pixelify Sans and Figtree once their font files are in res/font.
private val DisplayFont = FontFamily.Default
private val BodyFont = FontFamily.Default

/** Text styles from the design system: the game voice (display, title, stat) and reading styles. */
@Immutable
object OllType {
    val display = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp)
    val title = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    val stat = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum")
    val heading = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val button = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em)
}
