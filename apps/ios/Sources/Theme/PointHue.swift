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
