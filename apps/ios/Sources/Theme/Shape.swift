import SwiftUI

// Slate radii (pt). Pills use Capsule(); structural corners sit 14–28.
enum Radius {
    static let card:  CGFloat = 18   // stat / chart card
    static let tile:  CGFloat = 20   // home tile, FAB
    static let sheet: CGFloat = 28   // bottom-sheet top corners
    static let toast: CGFloat = 14
    static let inset: CGFloat = 12
}

// Cards lean on a 1pt outline rather than shadow.
extension View {
    func pointsCard() -> some View {
        self
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card)
                    .strokeBorder(Color.lineSoft, lineWidth: 1)
            )
    }
}
