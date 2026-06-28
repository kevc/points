import SwiftUI

// Accent is NOT a fixed brand color. Every point type owns a hue; lightness &
// chroma are constant so all hues read at one weight.
//   light: L 60% · C 0.095      dark: L 70% · C 0.105
//
// In SwiftUI this becomes `.tint(point.hue.color)`, set per tile — never a
// global accentColor. Color is reserved for data.
enum PointHue: String, CaseIterable, Codable {
    case green   // 152°
    case blue    // 215°
    case violet  // 285°
    case amber   // 40°
    case red     // 18°
    case magenta // 330°

    /// The color-wheel angle (degrees) each hue sits at — matches the Android palette and the design.
    var degrees: Int {
        switch self {
        case .green: return 152
        case .blue: return 215
        case .violet: return 285
        case .amber: return 40
        case .red: return 18
        case .magenta: return 330
        }
    }

    /// Maps a point type's stored hue (color-wheel degrees) to the nearest palette entry.
    static func forDegrees(_ hue: Int) -> PointHue {
        let deg = ((hue % 360) + 360) % 360
        return allCases.min { a, b in
            let da = min(abs(a.degrees - deg), 360 - abs(a.degrees - deg))
            let db = min(abs(b.degrees - deg), 360 - abs(b.degrees - deg))
            return da < db
        } ?? .green
    }

    var color: Color {
        switch self {
        case .green:   return Color(light: Color(red: 0.318, green: 0.569, blue: 0.392),
                                    dark:  Color(red: 0.412, green: 0.694, blue: 0.494))
        case .blue:    return Color(light: Color(red: 0.153, green: 0.557, blue: 0.643),
                                    dark:  Color(red: 0.243, green: 0.686, blue: 0.780))
        case .violet:  return Color(light: Color(red: 0.478, green: 0.471, blue: 0.718),
                                    dark:  Color(red: 0.596, green: 0.584, blue: 0.867))
        case .amber:   return Color(light: Color(red: 0.690, green: 0.424, blue: 0.329),
                                    dark:  Color(red: 0.839, green: 0.529, blue: 0.424))
        case .red:     return Color(light: Color(red: 0.698, green: 0.408, blue: 0.420),
                                    dark:  Color(red: 0.847, green: 0.514, blue: 0.525))
        case .magenta: return Color(light: Color(red: 0.627, green: 0.420, blue: 0.608),
                                    dark:  Color(red: 0.765, green: 0.525, blue: 0.745))
        }
    }
}
