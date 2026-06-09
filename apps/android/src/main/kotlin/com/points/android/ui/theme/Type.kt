package com.points.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.points.android.R

// Hanken Grotesk — static weights instanced from the Google Fonts variable
// face (OFL, see licenses/HankenGrotesk-OFL.txt) and bundled in res/font.
val Hanken = FontFamily(
    Font(R.font.hanken_regular,  FontWeight.Normal),   // 450 body
    Font(R.font.hanken_medium,   FontWeight.Medium),   // 500 numerals
    Font(R.font.hanken_semibold, FontWeight.SemiBold), // 600
    Font(R.font.hanken_bold,     FontWeight.Bold),     // 700
    Font(R.font.hanken_extrabold, FontWeight.ExtraBold) // 800 display
)

val PointsTypography = Typography(
    displayLarge  = TextStyle(fontFamily = Hanken, fontSize = 84.sp, fontWeight = FontWeight.Medium,   letterSpacing = (-1.7).sp), // hero numeral
    headlineLarge = TextStyle(fontFamily = Hanken, fontSize = 32.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.8).sp), // screen title
    headlineSmall = TextStyle(fontFamily = Hanken, fontSize = 26.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.5).sp),
    titleLarge    = TextStyle(fontFamily = Hanken, fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge     = TextStyle(fontFamily = Hanken, fontSize = 15.5.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = Hanken, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelLarge    = TextStyle(fontFamily = Hanken, fontSize = 13.sp, fontWeight = FontWeight.Bold,     letterSpacing = 0.5.sp), // UPPERCASE caps in UI
    bodySmall     = TextStyle(fontFamily = Hanken, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
)
// Numerals: apply TextStyle(fontFeatureSettings = "tnum") for tabular figures.
