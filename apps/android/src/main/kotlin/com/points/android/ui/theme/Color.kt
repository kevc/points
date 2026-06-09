package com.points.android.ui.theme

import androidx.compose.ui.graphics.Color

// Slate token palette. Hex values are computed from the design system's oklch
// source (see Points Design System → Compose handoff), so they are exact, not
// approximate. Do not hand-edit; regenerate from the oklch tokens if they change.

// ── Slate · Light ──────────────────────────────────────────────
val BgLight = Color(0xFFF6F9FB)
val SurfaceLight = Color(0xFFFCFDFF)
val SurfaceHighLight = Color(0xFFEDF1F4)
val RaisedLight = Color(0xFFFFFFFF)
val InkLight = Color(0xFF22272E)
val InkDimLight = Color(0xFF565B63)
val InkFaintLight = Color(0xFF8A9097)
val LineLight = Color(0xFFDCE0E5)
val LineSoftLight = Color(0xFFE8EBEF)
val RingTrackLight = Color(0xFFE1E5EA)

// ── Slate · Dark ───────────────────────────────────────────────
val BgDark  = Color(0xFF101419)
val SurfaceDark  = Color(0xFF181C22)
val SurfaceHighDark  = Color(0xFF1F242C)
val RaisedDark  = Color(0xFF1C2128)
val InkDark  = Color(0xFFE5E8EC)
val InkDimDark  = Color(0xFF9CA2A9)
val InkFaintDark  = Color(0xFF6D7279)
val LineDark  = Color(0xFF2C3138)
val LineSoftDark  = Color(0xFF23272C)
val RingTrackDark  = Color(0xFF2A2E35)

// Scrim
val ScrimLight = Color(0x57121A24) // onSurface @ 34%
val ScrimDark  = Color(0x85000000) // black @ 52%
