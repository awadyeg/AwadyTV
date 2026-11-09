package com.awadytv.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.awadytv.R

/** Inter (static Latin cuts 400/500/600/700, converted from the fontsource distribution, OFL 1.1). */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Type scale from plan 5.1: Display 48, Title 28, Body 18, Caption 14 (16sp is the legibility floor for body copy). */
object AwadyType {
    val Display = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.01).em)
    val Title = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp)
    val Headline = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val Body = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight.Medium)
    val Label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
    val Caption = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp)
    val ShelfHeader = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.08.em)
}
