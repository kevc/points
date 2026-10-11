import SwiftUI

/// The design system's stroke icon set: geometric, calm, round-capped 24×24 paths, hand-ported from the
/// design handoff path data so the native icons match the prototype stroke for stroke (and the Android
/// `IconPaths` set).
enum StrokeGlyph {
    case back, edit, sliders, plus, minus, expand, chevronRight

    /// The glyph's path in its 24×24 design viewport.
    var path: Path {
        var p = Path()
        switch self {
        case .back: // M15 5l-7 7 7 7
            p.move(to: .init(x: 15, y: 5))
            p.addLine(to: .init(x: 8, y: 12))
            p.addLine(to: .init(x: 15, y: 19))
        case .edit: // M4 20h4L19 9l-4-4L4 16v4z
            p.move(to: .init(x: 4, y: 20))
            p.addLine(to: .init(x: 8, y: 20))
            p.addLine(to: .init(x: 19, y: 9))
            p.addLine(to: .init(x: 15, y: 5))
            p.addLine(to: .init(x: 4, y: 16))
            p.addLine(to: .init(x: 4, y: 20))
            p.closeSubpath()
        case .sliders: // M4 8h10M18 8h2M4 16h2M10 16h10M14 6v4M6 14v4
            p.move(to: .init(x: 4, y: 8)); p.addLine(to: .init(x: 14, y: 8))
            p.move(to: .init(x: 18, y: 8)); p.addLine(to: .init(x: 20, y: 8))
            p.move(to: .init(x: 4, y: 16)); p.addLine(to: .init(x: 6, y: 16))
            p.move(to: .init(x: 10, y: 16)); p.addLine(to: .init(x: 20, y: 16))
            p.move(to: .init(x: 14, y: 6)); p.addLine(to: .init(x: 14, y: 10))
            p.move(to: .init(x: 6, y: 14)); p.addLine(to: .init(x: 6, y: 18))
        case .plus: // M12 5v14M5 12h14
            p.move(to: .init(x: 12, y: 5)); p.addLine(to: .init(x: 12, y: 19))
            p.move(to: .init(x: 5, y: 12)); p.addLine(to: .init(x: 19, y: 12))
        case .minus: // M5 12h14
            p.move(to: .init(x: 5, y: 12)); p.addLine(to: .init(x: 19, y: 12))
        case .expand: // M15 4h5v5M20 4l-6 6M9 20H4v-5M4 20l6-6
            p.move(to: .init(x: 15, y: 4)); p.addLine(to: .init(x: 20, y: 4)); p.addLine(to: .init(x: 20, y: 9))
            p.move(to: .init(x: 20, y: 4)); p.addLine(to: .init(x: 14, y: 10))
            p.move(to: .init(x: 9, y: 20)); p.addLine(to: .init(x: 4, y: 20)); p.addLine(to: .init(x: 4, y: 15))
            p.move(to: .init(x: 4, y: 20)); p.addLine(to: .init(x: 10, y: 14))
        case .chevronRight: // M9 5l7 7-7 7
            p.move(to: .init(x: 9, y: 5))
            p.addLine(to: .init(x: 16, y: 12))
            p.addLine(to: .init(x: 9, y: 19))
        }
        return p
    }
}

/// Draws one [StrokeGlyph] as a stroked path, scaled from its 24×24 viewport to `size`.
struct StrokeIconView: View {
    let glyph: StrokeGlyph
    var size: CGFloat = 22
    var strokeWidth: CGFloat = 1.75
    var tint: Color = .ink

    var body: some View {
        let path = glyph.path
        Canvas { context, canvasSize in
            let s = min(canvasSize.width, canvasSize.height) / 24
            context.scaleBy(x: s, y: s)
            context.stroke(
                path,
                with: .color(tint),
                // the stroke is specified against the 24-unit viewport, so unscale it
                style: StrokeStyle(lineWidth: strokeWidth / s, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: size, height: size)
    }
}
