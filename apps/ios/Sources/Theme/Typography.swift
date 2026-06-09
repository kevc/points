import SwiftUI

// Hanken Grotesk — the .ttf weights are bundled in Sources/Fonts and registered
// via UIAppFonts in Info.plist.
//
// Each style resolves to a concrete face by PostScript name rather than applying
// a `.weight()` trait to one base face: the five weights live in separate RIBBI
// families, so trait resolution would silently fall back to faux-bolding. Picking
// the face directly keeps every weight crisp and the public API unchanged.
extension Font {
    /// Scales with Dynamic Type, anchored to a system text style.
    static func points(_ size: CGFloat, _ weight: Font.Weight,
                       relativeTo style: Font.TextStyle = .body) -> Font {
        .custom(hankenFace(for: weight), size: size, relativeTo: style)
    }

    private static func hankenFace(for weight: Font.Weight) -> String {
        switch weight {
        case .medium:        return "HankenGrotesk-Medium"
        case .semibold:      return "HankenGrotesk-SemiBold"
        case .bold:          return "HankenGrotesk-Bold"
        case .heavy, .black: return "HankenGrotesk-ExtraBold"
        default:             return "HankenGrotesk-Regular"
        }
    }

    static let counter   = points(84, .medium,   relativeTo: .largeTitle) // hero numeral
    static let display   = points(46, .heavy,    relativeTo: .largeTitle) // brand title
    static let titleLg   = points(32, .bold,     relativeTo: .title)      // screen title
    static let tileNum   = points(38, .medium,   relativeTo: .title)      // tile value
    static let statNum   = points(26, .medium,   relativeTo: .title2)     // stat figure
    static let headline  = points(21, .semibold, relativeTo: .title3)     // sheet / row title
    static let bodyLg    = points(15.5, .regular, relativeTo: .body)
    static let bodyUI    = points(14, .semibold, relativeTo: .subheadline)
    static let labelCaps = points(13, .bold,     relativeTo: .caption)    // UPPERCASE in UI
    static let caption   = points(12, .semibold, relativeTo: .caption2)
}

// Numerals always use tabular figures so counters never shift width:
//   Text(value, format: .number).font(.tileNum).monospacedDigit()
