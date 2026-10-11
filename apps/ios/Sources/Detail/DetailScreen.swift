import SwiftUI
import UIKit
import PointsKit

/// The M5 per-point Detail screen (design `screens-detail.jsx` → `Detail`): app bar, hero value with the
/// week-delta pill, counter controls with custom step chips, the stats row, the chart card (Trend / Bars /
/// Cal with a Week / Month / Year range), the insight caption, and the recent-activity log. Reset and remove
/// live behind the more-sheet and stay reversible via the undo bar.
struct DetailView: View {
    let component: DetailComponent
    @StateObject private var model: DetailModel
    @State private var showMore = false
    @State private var showFocus = false

    init(component: DetailComponent) {
        self.component = component
        _model = StateObject(wrappedValue: DetailModel(component))
    }

    var body: some View {
        let state = model.state
        if let trend = state.trend {
            content(trend: trend, state: state)
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func content(trend: PointTrend, state: DetailStoreState) -> some View {
        let accent = PointHue.forDegrees(Int(state.hue)).color
        return ZStack {
            VStack(spacing: 0) {
                AppBarView(
                    title: trend.type.name,
                    onBack: component.onBack,
                    onEdit: component.onEdit,
                    onMore: { showMore = true }
                )

                ScrollView {
                    VStack(spacing: 0) {
                        HeroView(trend: trend, accent: accent)
                        CounterControlsView(
                            trend: trend,
                            enabled: !state.deleted,
                            onIncrement: { counterHaptic(); component.onIncrement() },
                            onDecrement: { counterHaptic(); component.onDecrement() },
                            onIncrementBy: { counterHaptic(); component.onIncrementBy(amount: $0) },
                            onExpand: { showFocus = true }
                        )
                        StatsRowView(trend: trend)
                        ChartCardView(
                            trend: trend,
                            chart: state.chart,
                            range: state.range,
                            accent: accent,
                            onSetChart: { component.onSetChart(chart: $0) },
                            onSetRange: { component.onSetRange(range: $0) }
                        )
                        SectionLabelView(text: "Recent activity")
                        ActivityLogView(trend: trend, accent: accent)
                        Spacer().frame(height: 32)
                    }
                }

                if let label = state.undoLabel {
                    UndoBarView(label: label, onUndo: component.onUndo, onDismiss: component.onDismissUndo)
                }
            }

            if showFocus {
                FocusCounterView(
                    trend: trend,
                    accent: accent,
                    onIncrement: component.onIncrement,
                    onDecrement: component.onDecrement,
                    onClose: { showFocus = false }
                )
                .transition(.opacity)
            }
        }
        .confirmationDialog(trend.type.name, isPresented: $showMore, titleVisibility: .visible) {
            Button("Reset to zero") { component.onReset() }
            Button("Remove this point") { component.onDelete() }
            Button("Cancel", role: .cancel) {}
        } message: {
            // both actions are reversible by design ("nothing is ever destroyed") — no destructive red
            Text("Resets and deletes here are safe — they're recorded as adjustments, never erased. You can always undo.")
        }
    }
}

private func counterHaptic() {
    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
}

// MARK: - App bar

private struct AppBarView: View {
    let title: String
    let onBack: () -> Void
    let onEdit: () -> Void
    let onMore: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            iconButton(.back, size: 22, action: onBack, label: "back")
            Text(title)
                .font(.points(17, .semibold))
                .foregroundColor(.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
            iconButton(.edit, size: 20, action: onEdit, label: "edit")
            iconButton(.sliders, size: 20, action: onMore, label: "more")
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    private func iconButton(_ glyph: StrokeGlyph, size: CGFloat, action: @escaping () -> Void, label: String) -> some View {
        Button(action: action) {
            StrokeIconView(glyph: glyph, size: size)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Hero

private struct HeroView: View {
    let trend: PointTrend
    let accent: Color

    var body: some View {
        let down = trend.type.goal == .down
        let week = trend.deltaThisWeek
        let deltaText: String = {
            if down && week == 0 { return "None this week — nice" }
            if down { return "\(formatCount(week)) this week" }
            return "+\(formatCount(week)) this week"
        }()

        VStack(spacing: 0) {
            Text(trend.type.mode == .daily ? "Today" : "Total")
                .font(.points(15, .semibold))
                .foregroundColor(.inkDim)
            HStack(alignment: .bottom, spacing: 8) {
                Text(formatCount(trend.value))
                    .font(.points(56, .medium, relativeTo: .largeTitle))
                    .monospacedDigit()
                    .foregroundColor(.ink)
                    .accessibilityLabel("counter value")
                if !trend.type.unit.isEmpty {
                    Text(trend.type.unit)
                        .font(.points(26, .medium))
                        .foregroundColor(.inkFaint)
                        .padding(.bottom, 8)
                }
            }
            HStack(spacing: 6) {
                Circle()
                    .fill(down ? Color.inkFaint : accent)
                    .frame(width: 8, height: 8)
                Text(deltaText)
                    .font(.points(13.5, .semibold))
                    .foregroundColor(.inkDim)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Capsule().fill(Color.surfaceHigh))
            .padding(.top, 12)
        }
        .padding(.horizontal, 22)
        .padding(.top, 4)
        .padding(.bottom, 18)
    }
}

// MARK: - Counter controls

private struct CounterControlsView: View {
    let trend: PointTrend
    let enabled: Bool
    let onIncrement: () -> Void
    let onDecrement: () -> Void
    let onIncrementBy: (Int64) -> Void
    let onExpand: () -> Void

    var body: some View {
        let step = max(trend.type.step, 1)
        let chips: [Int64] = step >= 10 ? [step, step * 2, step * 5] : [1, 5, 10]

        VStack(spacing: 0) {
            HStack(spacing: 16) {
                roundButton(diameter: 64, background: .surfaceHigh, action: onDecrement, label: "decrement") {
                    StrokeIconView(glyph: .minus, size: 26)
                }
                roundButton(diameter: 96, background: .ink, action: onIncrement, label: "increment") {
                    StrokeIconView(glyph: .plus, size: 40, tint: .bg)
                }
                roundButton(diameter: 64, background: .surfaceHigh, action: onExpand, label: "focus mode") {
                    StrokeIconView(glyph: .expand, size: 22)
                }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 6)

            HStack(spacing: 8) {
                ForEach(chips, id: \.self) { amount in
                    stepChip(text: "+\(amount)") { onIncrementBy(amount) }
                }
                stepChip(text: "−\(step)", action: onDecrement)
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 16)
        }
    }

    private func roundButton<Content: View>(
        diameter: CGFloat,
        background: Color,
        action: @escaping () -> Void,
        label: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(background.opacity(enabled ? 1 : 0.4))
                content()
            }
            .frame(width: diameter, height: diameter)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private func stepChip(text: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.points(14, .semibold))
                .foregroundColor(.inkDim)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .overlay(Capsule().strokeBorder(Color.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

// MARK: - Stats row

private struct StatsRowView: View {
    let trend: PointTrend

    var body: some View {
        let week = trend.deltaThisWeek
        let weekText = week >= 0 && trend.type.goal != .down ? "+\(formatCount(week))" : formatCount(week)

        HStack(spacing: 10) {
            stat(label: "This week", value: weekText)
            stat(label: "Best day", value: formatCount(trend.bestDay))
            stat(label: "Active days", value: String(trend.activeDays))
        }
        .padding(.horizontal, 18)
        .padding(.top, 6)
        .padding(.bottom, 18)
    }

    private func stat(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.points(12, .semibold))
                .foregroundColor(.inkFaint)
            Text(value)
                .font(.statNum)
                .monospacedDigit()
                .foregroundColor(.ink)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.surface))
    }
}

// MARK: - Chart card

private let chartStyles: [ChartStyle] = [.trend, .bars, .calendar]
private let trendRanges: [TrendRange] = [.week, .month, .year]

private struct ChartCardView: View {
    let trend: PointTrend
    let chart: ChartStyle
    let range: TrendRange
    let accent: Color
    let onSetChart: (ChartStyle) -> Void
    let onSetRange: (TrendRange) -> Void

    var body: some View {
        let series = trend.series(range: range)

        VStack(spacing: 0) {
            HStack {
                Text(chartTitle)
                    .font(.labelCaps)
                    .foregroundColor(.inkFaint)
                Spacer()
                SegmentedView(
                    options: ["Trend", "Bars", "Cal"],
                    selected: chartStyles.firstIndex(of: chart) ?? 0,
                    onSelect: { onSetChart(chartStyles[$0]) }
                )
            }
            .padding(.bottom, 16)

            switch chart {
            case .trend:
                AreaTrendView(trend: trend, line: series.line.map(\.int64Value), range: range, accent: accent)
                Text(trend.insight.text)
                    .font(.points(13, .medium))
                    .foregroundColor(.inkDim)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.surfaceHigh))
                    .padding(.top, 14)
            case .bars:
                TrendBarsView(bars: series.bars.map(\.int64Value), labels: series.labels, accent: accent)
            default:
                HeatmapView(heatmap: trend.heatmap(weeksBack: 20), bestDay: trend.bestDay, accent: accent)
            }

            if chart == .calendar {
                calendarLegend.padding(.top, 14)
            } else {
                SegmentedView(
                    options: ["Week", "Month", "Year"],
                    selected: trendRanges.firstIndex(of: range) ?? 1,
                    onSelect: { onSetRange(trendRanges[$0]) }
                )
                .padding(.top, 14)
            }
        }
        .padding(.top, 18)
        .padding(.horizontal, 18)
        .padding(.bottom, 14)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color.surface))
        .padding(.horizontal, 18)
    }

    private var chartTitle: String {
        switch chart {
        case .trend: return "TREND"
        case .bars: return "PER PERIOD"
        default: return "CALENDAR"
        }
    }

    private var calendarLegend: some View {
        HStack {
            Text("Last 20 weeks")
                .font(.points(12, .semibold))
                .foregroundColor(.inkFaint)
            Spacer()
            HStack(spacing: 6) {
                Text("less").font(.points(12, .semibold)).foregroundColor(.inkFaint)
                HStack(spacing: 3) {
                    RoundedRectangle(cornerRadius: 3).fill(Color.ringTrack.opacity(0.5)).frame(width: 10, height: 10)
                    ForEach([0.35, 0.6, 0.85, 1.0], id: \.self) { alpha in
                        RoundedRectangle(cornerRadius: 3).fill(accent.opacity(alpha)).frame(width: 10, height: 10)
                    }
                }
                Text("more").font(.points(12, .semibold)).foregroundColor(.inkFaint)
            }
        }
    }
}

private struct SegmentedView: View {
    let options: [String]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options.indices, id: \.self) { index in
                Text(options[index])
                    .font(.points(13, .semibold))
                    .foregroundColor(index == selected ? .ink : .inkDim)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(index == selected ? Color.raised : Color.clear))
                    .contentShape(Capsule())
                    .onTapGesture { onSelect(index) }
            }
        }
        .padding(3)
        .background(Capsule().fill(Color.surfaceHigh))
    }
}

// MARK: - Activity log

private struct SectionLabelView: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.labelCaps)
            .foregroundColor(.inkFaint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 22)
            .padding(.top, 14)
            .padding(.bottom, 10)
    }
}

private struct ActivityLogView: View {
    let trend: PointTrend
    let accent: Color

    var body: some View {
        let log = trend.recentLog(limit: 12)

        VStack(spacing: 0) {
            if log.isEmpty {
                Text("No activity yet — tap + to begin.")
                    .font(.points(14, .medium))
                    .foregroundColor(.inkFaint)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 8)
            }
            ForEach(log.indices, id: \.self) { index in
                let entry = log[index]
                HStack(spacing: 14) {
                    Circle()
                        .fill(entry.delta < 0 ? Color.inkFaint : accent)
                        .frame(width: 8, height: 8)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(TrendChartGeometryKt.dateLabelOf(date: entry.date, today: trend.today))
                            .font(.points(14.5, .semibold))
                            .foregroundColor(.ink)
                        Text(entry.delta > 0 ? "Added" : "Adjusted")
                            .font(.points(12.5, .medium))
                            .foregroundColor(.inkFaint)
                    }
                    Spacer()
                    Text((entry.delta > 0 ? "+" : "−") + formatCount(abs(entry.delta)))
                        .font(.points(16, .medium))
                        .monospacedDigit()
                        .foregroundColor(entry.delta < 0 ? .inkFaint : .ink)
                }
                .padding(.vertical, 13)
                if index < log.count - 1 {
                    Rectangle().fill(Color.lineSoft).frame(height: 1)
                }
            }
        }
        .padding(.horizontal, 22)
    }
}

// MARK: - Undo bar

private struct UndoBarView: View {
    let label: String
    let onUndo: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack {
            Text(label).font(.bodyUI).foregroundColor(.ink)
            Spacer()
            Button("Undo", action: onUndo).font(.bodyUI)
            Button("Dismiss", action: onDismiss).font(.bodyUI).foregroundColor(.inkDim)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.surfaceHigh))
        .padding(16)
    }
}

// MARK: - State bridge

/// Bridges the shared Decompose `Value<DetailStore.State>` into an observable SwiftUI model.
private final class DetailModel: ObservableObject {
    @Published var state: DetailStoreState
    private var cancellation: DecomposeCancellation?

    init(_ component: DetailComponent) {
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
