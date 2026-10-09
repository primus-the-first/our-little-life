package com.ourlittlelife.game.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ourlittlelife.game.R

// Both fonts are variable (one file, every weight), so each weight sets its own variation.
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Pixelify Sans: the game's voice, for titles and numbers only. */
private val DisplayFont = FontFamily(
    variableFont(R.font.pixelify_sans, FontWeight.Medium),
    variableFont(R.font.pixelify_sans, FontWeight.SemiBold),
)

/** Figtree: everything people read. */
private val BodyFont = FontFamily(
    variableFont(R.font.figtree, FontWeight.Normal),
    variableFont(R.font.figtree, FontWeight.Medium),
    variableFont(R.font.figtree, FontWeight.SemiBold),
    variableFont(R.font.figtree, FontWeight.Bold),
)

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

    /** Short uppercase state words, such as "LOW" next to a need. */
    val tag = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em)
}
