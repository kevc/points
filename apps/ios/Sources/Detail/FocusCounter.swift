import SwiftUI
import UIKit
import PointsKit

/// The full-screen Focus counter (design `screens-detail.jsx` → `CounterFS`), reached from the Detail counter
/// row's expand button: the whole screen is the tap target — every tap adds the type's own step with a soft
/// accent ripple where the finger landed and a little pop of the numeral. A round − in the footer corrects
/// overshoots; Done (or the back chevron) returns to the Detail screen.
struct FocusCounterView: View {
    let trend: PointTrend
    let accent: Color
    let onIncrement: () -> Void
    let onDecrement: () -> Void
    let onClose: () -> Void

    @State private var ripples: [FocusRipple] = []
    @State private var popScale: CGFloat = 1

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Button(action: onClose) {
                    StrokeIconView(glyph: .back, size: 22)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("done")
                Text("Focus")
                    .font(.points(16, .semibold))
                    .foregroundColor(.inkDim)
                    .frame(maxWidth: .infinity)
                Spacer().frame(width: 44)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)

            tapField

            HStack(spacing: 22) {
                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onDecrement()
                } label: {
                    ZStack {
                        Circle().fill(Color.surfaceHigh)
                        StrokeIconView(glyph: .minus, size: 26)
                    }
                    .frame(width: 64, height: 64)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("decrement")

                Button("Done", action: onClose)
                    .font(.points(15.5, .semibold))
                    .foregroundColor(.ink)
                    .frame(minWidth: 120)
                    .padding(.vertical, 14)
            }
            .padding(.top, 20)
            .padding(.bottom, 30)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.bg.ignoresSafeArea())
    }

    private var tapField: some View {
        let valueText = formatCount(trend.value)
        // the numeral shrinks as it grows: 150pt up to 3 chars, 104pt to 5, 78pt beyond
        let numSize: CGFloat = valueText.count <= 3 ? 150 : (valueText.count <= 5 ? 104 : 78)
        let step = max(trend.type.step, 1)

        return ZStack {
            ForEach(ripples) { ripple in
                FocusRippleView(ripple: ripple, accent: accent)
            }
            VStack(spacing: 0) {
                Text(trend.type.name)
                    .font(.points(17, .semibold))
                    .foregroundColor(.inkDim)
                Text(valueText)
                    .font(.points(numSize, .medium, relativeTo: .largeTitle))
                    .monospacedDigit()
                    .foregroundColor(.ink)
                    .scaleEffect(popScale)
                    .padding(.vertical, 14)
                    .accessibilityLabel("counter value")
                Text(step > 1 ? "Tap anywhere to add \(step)" : "Tap anywhere to add")
                    .font(.points(14, .medium))
                    .foregroundColor(.inkFaint)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        // DragGesture(minimumDistance: 0) is the iOS-15 way to read the tap's location
        .gesture(
            DragGesture(minimumDistance: 0)
                .onEnded { gesture in tap(at: gesture.location) }
        )
        .onChange(of: trend.value) { _ in
            popScale = 1.1
            withAnimation(.easeOut(duration: 0.32)) { popScale = 1 }
        }
    }

    private func tap(at location: CGPoint) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let ripple = FocusRipple(center: location)
        ripples.append(ripple)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.65) {
            ripples.removeAll { $0.id == ripple.id }
        }
        onIncrement()
    }
}

/// One in-flight tap ripple: where it landed. Its expansion is animated by `FocusRippleView`.
private struct FocusRipple: Identifiable {
    let id = UUID()
    let center: CGPoint
}

private struct FocusRippleView: View {
    let ripple: FocusRipple
    let accent: Color

    @State private var progress: CGFloat = 0

    var body: some View {
        Circle()
            .fill(accent.opacity(0.16 * (1 - progress)))
            .frame(width: 240, height: 240)
            .scaleEffect(progress)
            .position(ripple.center)
            .onAppear {
                withAnimation(.easeOut(duration: 0.6)) { progress = 1 }
            }
            .allowsHitTesting(false)
    }
}
