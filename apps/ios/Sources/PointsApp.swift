import SwiftUI
import PointsKit

@main
struct PointsApp: App {
    private let root: RootComponent

    init() {
        startKoinIos()
        root = createRootComponent()
    }

    var body: some Scene {
        WindowGroup {
            ContentView(root: root)
        }
    }
}
