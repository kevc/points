package com.points.core.presentation.home

/**
 * A starter point type offered on the empty/first-run screen — tap to create it (name + color + glyph; the
 * rest defaults, and it can be edited after). Shared by both platforms so the chips read identically.
 */
data class Suggestion(val name: String, val hue: Int, val icon: String)

/** The design's gentle first-run prompts — "a few things people like to track." */
val pointSuggestions: List<Suggestion> = listOf(
    Suggestion("Days smoke-free", hue = 152, icon = "leaf"),
    Suggestion("Glasses of water", hue = 215, icon = "drop"),
    Suggestion("Meditation", hue = 285, icon = "spark"),
    Suggestion("Push-ups", hue = 40, icon = "bolt"),
    Suggestion("Times angry", hue = 18, icon = "pulse"),
    Suggestion("Books read", hue = 330, icon = "book"),
)
