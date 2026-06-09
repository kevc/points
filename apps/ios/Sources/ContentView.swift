import SwiftUI
import PointsKit

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    private let root: RootComponent
    @StateObject private var counterModel: CounterModel
    @StateObject private var syncModel: SyncModel

    init(root: RootComponent) {
        self.root = root
        _counterModel = StateObject(wrappedValue: CounterModel(root.counter))
        _syncModel = StateObject(wrappedValue: SyncModel(root.sync))
    }

    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 24) {
                Text("\(counterModel.value)")
                    .font(.system(size: 72, weight: .bold))
                    .monospacedDigit()

                HStack(spacing: 24) {
                    Button("-") { root.counter.onDecrement() }
                    Button("+") { root.counter.onIncrement() }
                }
                .font(.largeTitle)
                .buttonStyle(.bordered)
            }
            .frame(maxHeight: .infinity)

            SyncStatusView(status: syncModel.status)
                .padding(.top, 8)
        }
        .padding()
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                root.sync.onAppForegrounded() // reconcile when brought to the foreground
            }
        }
    }
}

/// Compact, secondary sync-status indicator: a spinner while syncing, otherwise a short label.
private struct SyncStatusView: View {
    let status: DomainSyncStatus

    var body: some View {
        HStack(spacing: 6) {
            if status == .syncing {
                ProgressView().controlSize(.small)
            }
            Text(label)
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("sync status: \(label)")
    }

    private var label: String {
        switch status {
        case .idle: return "Idle"
        case .syncing: return "Syncing…"
        case .synced: return "Synced"
        case .offline: return "Offline"
        case .failed: return "Sync failed"
        }
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

/// Bridges the shared Decompose `Value<SyncStore.State>` into an observable SwiftUI model.
private final class SyncModel: ObservableObject {
    @Published var status: DomainSyncStatus
    private var cancellation: DecomposeCancellation?

    init(_ component: SyncComponent) {
        let state = component.state
        status = state.value.status
        cancellation = state.subscribe { [weak self] newState in
            self?.status = newState.status
        }
    }

    deinit {
        cancellation?.cancel()
    }
}
