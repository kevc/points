import SwiftUI
import PointsKit

struct ContentView: View {
    var body: some View {
        // PointsKit — the SKIE-exported shared Kotlin framework — is linked and
        // importable here. Rendering live shared component state is wired in #17.
        Text("Points")
            .font(.largeTitle)
            .padding()
    }
}

#Preview {
    ContentView()
}
