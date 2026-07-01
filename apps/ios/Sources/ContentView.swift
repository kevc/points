import SwiftUI
import UIKit
import PointsKit

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    private let root: RootComponent
    @StateObject private var stackModel: RootStackModel
    @StateObject private var syncModel: SyncModel

    init(root: RootComponent) {
        self.root = root
        _stackModel = StateObject(wrappedValue: RootStackModel(root))
        _syncModel = StateObject(wrappedValue: SyncModel(root.sync))
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.bg.ignoresSafeArea()

            childView(stackModel.child)

            SyncStatusView(status: syncModel.status)
                .padding(.top, 8)
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                root.sync.onAppForegrounded() // reconcile when brought to the foreground
            }
        }
    }

    /// Renders the active Decompose child — the home grid, or a per-type counter when a tile is tapped.
    @ViewBuilder
    private func childView(_ child: RootComponentChild) -> some View {
        if let home = child as? RootComponentChildHome {
            HomeView(component: home.component)
        } else if let detail = child as? RootComponentChildDetail {
            CounterView(component: detail.component)
        } else if let create = child as? RootComponentChildCreate {
            CreateEditView(component: create.component)
        }
    }
}

// MARK: - Home grid

private struct HomeView: View {
    let component: HomeComponent
    @StateObject private var model: HomeModel

    init(component: HomeComponent) {
        self.component = component
        _model = StateObject(wrappedValue: HomeModel(component))
    }

    private let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14),
    ]

    var body: some View {
        let tiles = model.state.tiles
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Points").font(.titleLg).foregroundColor(.ink)
                    Text("\(tiles.count) things, quietly counting")
                        .font(.bodyUI).foregroundColor(.inkDim)
                }
                .padding(.horizontal, 22)
                .padding(.top, 24)
                .padding(.bottom, 8)

                if model.state.loaded && tiles.isEmpty {
                    EmptyStateView { component.onQuickCreate(suggestion: $0) }
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 14) {
                            ForEach(tiles.indices, id: \.self) { index in
                                let tile = tiles[index]
                                TileView(
                                    tile: tile,
                                    onIncrement: { component.onIncrement(pointTypeId: tile.id, step: tile.step) },
                                    onDecrement: { component.onDecrement(pointTypeId: tile.id, step: tile.step) }
                                )
                                .onTapGesture { component.onTileClicked(pointTypeId: tile.id) }
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            Button(action: { component.onCreate() }) {
                Label("New", systemImage: "plus")
                    .font(.bodyUI)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .background(Color.ink)
                    .foregroundColor(.bg)
                    .clipShape(Capsule())
            }
            .padding(20)
        }
    }
}

/// Gentle first-run prompt: a question + starter chips that quick-create a point type.
private struct EmptyStateView: View {
    let onQuickCreate: (Suggestion) -> Void
    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        VStack(spacing: 16) {
            Text("What would you like to count?")
                .font(.titleLg).foregroundColor(.ink).multilineTextAlignment(.center)
            Text("Pick one to start — you can rename or change it any time. Nothing here is ever permanent.")
                .font(.bodyUI).foregroundColor(.inkDim).multilineTextAlignment(.center)
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(pointSuggestions, id: \.name) { suggestion in
                    Button { onQuickCreate(suggestion) } label: {
                        Text(suggestion.name).frame(maxWidth: .infinity).padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                    .tint(PointHue.forDegrees(Int(suggestion.hue)).color)
                }
            }
            .padding(.top, 8)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct TileView: View {
    let tile: HomeTile
    let onIncrement: () -> Void
    let onDecrement: () -> Void

    var body: some View {
        let hue = PointHue.forDegrees(Int(tile.hue))
        VStack(spacing: 12) {
            RingView(ring: tile.ring, hue: hue) {
                VStack(spacing: 0) {
                    Text(tile.valueText).font(.tileNum).monospacedDigit().foregroundColor(.ink)
                    if !tile.unit.isEmpty {
                        Text(tile.unit).font(.caption).foregroundColor(.inkDim)
                    }
                }
            }
            VStack(spacing: 2) {
                Text(tile.name).font(.bodyUI).foregroundColor(.ink)
                    .multilineTextAlignment(.center)
                Text(tile.meta).font(.caption).foregroundColor(.inkDim)
            }
            // The primary action: soft tonal ± buttons carrying the point's own hue.
            HStack(spacing: 8) {
                Button { tileHaptic(); onDecrement() } label: {
                    Text("−").padding(.horizontal, 10).padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                Button { tileHaptic(); onIncrement() } label: {
                    Text("+\(tile.step)").padding(.horizontal, 12).padding(.vertical, 4)
                }
                .buttonStyle(.borderedProminent)
                .tint(hue.color)
            }
            .font(.bodyUI)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .padding(.horizontal, 12)
        .background(Color.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private func tileHaptic() {
    UIImpactFeedbackGenerator(style: .light).impactOccurred()
}

/// The tile gauge: a track, a progress arc (the point's hue when accented, else calm ink), scale ticks, and
/// an over-target marker — the SwiftUI rendering of the domain `RingState`. `content` sits at the center.
private struct RingView<Content: View>: View {
    let ring: DomainRingState
    let hue: PointHue
    @ViewBuilder var content: () -> Content

    private let diameter: CGFloat = 104
    private let stroke: CGFloat = 7

    var body: some View {
        let arcColor = ring.useAccent ? hue.color : Color.ink
        let progress = CGFloat(min(max(ring.progress, 0), 1))
        let tickCount = Int(ring.ticks)

        ZStack {
            Circle().stroke(Color.ringTrack, lineWidth: stroke)

            if progress > 0 {
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(arcColor, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }

            if tickCount > 1 {
                ForEach(0..<tickCount, id: \.self) { i in
                    Rectangle()
                        .fill(Color.surface)
                        .frame(width: 2, height: stroke + 3)
                        .offset(y: -(diameter / 2 - stroke / 2))
                        .rotationEffect(.degrees(Double(i) / Double(tickCount) * 360))
                }
            }

            if ring.over {
                Circle()
                    .fill(arcColor)
                    .frame(width: stroke + 3, height: stroke + 3)
                    .offset(y: -diameter / 2)
            }

            content()
        }
        .frame(width: diameter, height: diameter)
    }
}

private final class HomeModel: ObservableObject {
    @Published var state: HomeStoreState
    private var cancellation: DecomposeCancellation?

    init(_ component: HomeComponent) {
        let s = component.state
        state = s.value
        cancellation = s.subscribe { [weak self] newState in
            self?.state = newState
        }
    }

    deinit {
        cancellation?.cancel()
    }
}

private final class RootStackModel: ObservableObject {
    @Published var child: RootComponentChild
    private var cancellation: DecomposeCancellation?

    init(_ root: RootComponent) {
        let stack = root.stack
        child = stack.value.active.instance as! RootComponentChild
        cancellation = stack.subscribe { [weak self] newStack in
            self?.child = newStack.active.instance as! RootComponentChild
        }
    }

    deinit {
        cancellation?.cancel()
    }
}

// MARK: - Per-type counter (interim detail until M5)

private struct CounterView: View {
    let component: CounterComponent
    @StateObject private var model: CounterModel

    init(component: CounterComponent) {
        self.component = component
        _model = StateObject(wrappedValue: CounterModel(component))
    }

    var body: some View {
        let s = model.state
        VStack {
            HStack {
                Button("← Back") { component.onBack() }
                    .font(.bodyUI)
                    .foregroundColor(.inkDim)
                Spacer()
                Button("Edit") { component.onEdit() }
                    .font(.bodyUI)
                    .foregroundColor(.inkDim)
            }
            .padding()

            Spacer()
            Text("\(s.value)")
                .font(.counter)
                .monospacedDigit()
                .foregroundColor(PointHue.forDegrees(Int(s.hue)).color)
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)

            HStack(spacing: 24) {
                Button("−") { component.onDecrement() }
                Button("+") { component.onIncrement() }
            }
            .font(.titleLg)
            .buttonStyle(.bordered)
            .padding(.top, 24)
            .disabled(s.deleted)

            HStack(spacing: 16) {
                Button("Reset to zero") { component.onReset() }
                Button("Remove") { component.onDelete() }
            }
            .font(.bodyUI)
            .foregroundColor(.inkDim)
            .padding(.top, 16)
            .disabled(s.deleted)

            Spacer()

            if let label = s.undoLabel {
                HStack {
                    Text(label).foregroundColor(.ink)
                    Spacer()
                    Button("Undo") { component.onUndo() }
                }
                .font(.bodyUI)
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(Color.surfaceHigh)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Sync status

/// Compact, secondary sync-status indicator: a spinner while syncing, otherwise a short label.
private struct SyncStatusView: View {
    let status: DomainSyncStatus

    var body: some View {
        HStack(spacing: 6) {
            if status == .syncing {
                ProgressView().controlSize(.small)
            }
            Text(label)
                .font(.caption)
                .foregroundColor(.inkDim)
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
    @Published var state: CounterStoreState
    private var cancellation: DecomposeCancellation?

    init(_ component: CounterComponent) {
        let s = component.state
        state = s.value
        cancellation = s.subscribe { [weak self] newState in
            self?.state = newState
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

// MARK: - Create / edit form

private struct CreateEditView: View {
    let component: CreateEditComponent
    @StateObject private var model: CreateEditModel

    init(component: CreateEditComponent) {
        self.component = component
        _model = StateObject(wrappedValue: CreateEditModel(component))
    }

    // The picker offers exactly the palette's hues — one swatch per PointHue.
    private let hues: [Int32] = PointHue.allCases.map { Int32($0.degrees) }

    var body: some View {
        let s = model.state
        VStack(spacing: 0) {
            HStack {
                Button("Cancel") { component.onCancel() }.foregroundColor(.inkDim)
                Spacer()
                Text(s.editing ? "Edit point" : "New point").foregroundColor(.ink)
                Spacer()
                Button("Save") { component.onSave() }
            }
            .font(.bodyUI)
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    field("What are you counting?") {
                        TextField("e.g. Days smoke-free", text: Binding(
                            get: { s.name },
                            set: { component.onName(value: $0) }
                        ))
                        .textFieldStyle(.roundedBorder)
                    }

                    field("Color — shows up in its charts") {
                        HStack(spacing: 12) {
                            ForEach(hues, id: \.self) { hue in
                                Circle()
                                    .fill(PointHue.forDegrees(Int(hue)).color)
                                    .frame(width: 34, height: 34)
                                    .overlay(Circle().stroke(Color.ink, lineWidth: s.hue == hue ? 3 : 0))
                                    .onTapGesture { component.onHue(hue: hue) }
                            }
                        }
                    }

                    field("How is it counted?") {
                        HStack(spacing: 12) {
                            modeCard("Tally up", "A running total that climbs.", selected: s.mode == .cumulative) {
                                component.onMode(mode: .cumulative)
                            }
                            modeCard("Daily", "Resets gently each morning.", selected: s.mode == .daily) {
                                component.onMode(mode: .daily)
                            }
                        }
                    }

                    field("Step — how much each tap adds") {
                        stepper(value: "+\(s.step)", onMinus: { component.onStepDown() }, onPlus: { component.onStepUp() })
                    }

                    if s.mode == .daily {
                        field("Gentle daily target (optional)") {
                            stepper(
                                value: s.target > 0 ? "\(s.target)" : "Off",
                                onMinus: { component.onTargetDown() },
                                onPlus: { component.onTargetUp() }
                            )
                            Text(s.target > 0
                                ? "Counts up to this — never turns red if you go over."
                                : "No target — just a gentle daily tally.")
                                .font(.caption).foregroundColor(.inkDim)
                        }
                    }

                    if s.mode == .cumulative {
                        field("Tone") {
                            HStack(spacing: 12) {
                                modeCard("Climbing", "More feels like progress.", selected: s.goal == .up) {
                                    component.onGoal(goal: .up)
                                }
                                modeCard("Easing", "Just noticing — no red.", selected: s.goal == .down) {
                                    component.onGoal(goal: .down)
                                }
                            }
                        }
                    }
                }
                .padding(22)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.bg)
    }

    @ViewBuilder
    private func field<Content: View>(_ label: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(label).font(.caption).foregroundColor(.inkDim)
            content()
        }
    }

    private func modeCard(_ title: String, _ subtitle: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.bodyUI).foregroundColor(.ink)
                Text(subtitle).font(.caption).foregroundColor(.inkDim)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(selected ? Color.ink : Color.line, lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func stepper(value: String, onMinus: @escaping () -> Void, onPlus: @escaping () -> Void) -> some View {
        HStack(spacing: 16) {
            Button("−", action: onMinus).buttonStyle(.bordered)
            Text(value).font(.titleLg).foregroundColor(.ink)
            Button("+", action: onPlus).buttonStyle(.bordered)
        }
    }
}

private final class CreateEditModel: ObservableObject {
    @Published var state: CreateEditStoreState
    private var cancellation: DecomposeCancellation?

    init(_ component: CreateEditComponent) {
        let s = component.state
        state = s.value
        cancellation = s.subscribe { [weak self] newState in
            self?.state = newState
        }
    }

    deinit {
        cancellation?.cancel()
    }
}
