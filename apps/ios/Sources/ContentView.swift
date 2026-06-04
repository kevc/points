import SwiftUI
import PointsKit

struct ContentView: View {
    @StateObject private var model: HelloModel

    init(component: HelloComponent) {
        _model = StateObject(wrappedValue: HelloModel(component))
    }

    var body: some View {
        Text(model.greeting)
            .font(.largeTitle)
            .padding()
    }
}

/// Bridges the shared Decompose `Value` into an observable SwiftUI model.
private final class HelloModel: ObservableObject {
    @Published var greeting: String
    private var cancellation: DecomposeCancellation?

    init(_ component: HelloComponent) {
        let state = component.state
        greeting = state.value.greeting
        cancellation = state.subscribe { [weak self] newState in
            self?.greeting = newState.greeting
        }
    }

    deinit {
        cancellation?.cancel()
    }
}
