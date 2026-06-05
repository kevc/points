import SwiftUI
import PointsKit

struct ContentView: View {
    @StateObject private var model: CounterModel
    private let component: CounterComponent

    init(component: CounterComponent) {
        self.component = component
        _model = StateObject(wrappedValue: CounterModel(component))
    }

    var body: some View {
        VStack(spacing: 24) {
            Text("\(model.value)")
                .font(.system(size: 72, weight: .bold))
                .monospacedDigit()

            HStack(spacing: 24) {
                Button("-") { component.onDecrement() }
                Button("+") { component.onIncrement() }
            }
            .font(.largeTitle)
            .buttonStyle(.bordered)
        }
        .padding()
    }
}

/// Bridges the shared Decompose `Value<CounterStore.State>` into an observable SwiftUI model.
private final class CounterModel: ObservableObject {
    @Published var value: Int64
    private var cancellation: DecomposeCancellation?

    init(_ component: CounterComponent) {
        let state = component.state
        value = state.value.value
        cancellation = state.subscribe { [weak self] newState in
            self?.value = newState.value
        }
    }

    deinit {
        cancellation?.cancel()
    }
}
