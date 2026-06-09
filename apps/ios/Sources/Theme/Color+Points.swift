import SwiftUI

// A runtime light/dark pair — the in-code mirror of the Asset-Catalog color
// sets. Ship the catalog for production; this keeps the theme self-contained.
extension Color {
    init(light: Color, dark: Color) {
        self.init(UIColor { trait in
            trait.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
        })
    }
}

// Slate token palette. sRGB components are computed from the design system's
// oklch source (see "Points Design System — iOS" → SwiftUI handoff), so they
// are exact, not approximate. The comment names the closest iOS semantic color.
extension Color {
    // ── Slate ──  (light  ·  dark)
    static let bg          = Color(light: Color(red: 0.965, green: 0.976, blue: 0.984),
                                   dark:  Color(red: 0.063, green: 0.078, blue: 0.098))  // systemBackground
    static let surface     = Color(light: Color(red: 0.988, green: 0.992, blue: 1.000),
                                   dark:  Color(red: 0.094, green: 0.110, blue: 0.133))  // secondarySystemBackground
    static let surfaceHigh = Color(light: Color(red: 0.929, green: 0.945, blue: 0.957),
                                   dark:  Color(red: 0.122, green: 0.141, blue: 0.173))  // tertiarySystemBackground
    static let raised      = Color(light: Color(red: 1.000, green: 1.000, blue: 1.000),
                                   dark:  Color(red: 0.110, green: 0.129, blue: 0.157))  // systemBackground (elevated)
    static let ink         = Color(light: Color(red: 0.133, green: 0.153, blue: 0.180),
                                   dark:  Color(red: 0.898, green: 0.910, blue: 0.925))  // label
    static let inkDim      = Color(light: Color(red: 0.337, green: 0.357, blue: 0.388),
                                   dark:  Color(red: 0.612, green: 0.635, blue: 0.663))  // secondaryLabel
    static let inkFaint    = Color(light: Color(red: 0.541, green: 0.565, blue: 0.592),
                                   dark:  Color(red: 0.427, green: 0.447, blue: 0.475))  // tertiaryLabel
    static let line        = Color(light: Color(red: 0.863, green: 0.878, blue: 0.898),
                                   dark:  Color(red: 0.173, green: 0.192, blue: 0.220))  // separator
    static let lineSoft    = Color(light: Color(red: 0.910, green: 0.922, blue: 0.937),
                                   dark:  Color(red: 0.137, green: 0.153, blue: 0.173))  // separator @ 60%
    static let ringTrack   = Color(light: Color(red: 0.882, green: 0.898, blue: 0.918),
                                   dark:  Color(red: 0.165, green: 0.180, blue: 0.208))  // systemFill
}
