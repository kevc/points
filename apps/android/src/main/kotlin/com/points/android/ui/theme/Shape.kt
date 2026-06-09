package com.points.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Slate radii. Pill = CircleShape (50%).
val PointsShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),  // toast, inset chips
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(18.dp),  // stat / chart card  (--radius-card)
    large      = RoundedCornerShape(20.dp),  // home tile, FAB     (--radius-tile)
    extraLarge = RoundedCornerShape(28.dp),  // bottom sheet (top corners only)
)
// Pills (chips, segmented, hue dots, round buttons): CircleShape
