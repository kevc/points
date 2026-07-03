import SwiftUI
import PointsKit

// MARK: - Formatting

private let countFormatter: NumberFormatter = {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    return formatter
}()

/// Grouped integer formatting for chart labels and values ("4,030").
func formatCount(_ value: Int64) -> String {
    countFormatter.string(from: NSNumber(value: value)) ?? String(value)
}

func formatCount(_ value: Double) -> String {
    formatCount(Int64(value.rounded()))
}

// MARK: - Trend ("the reflective instrument")

/// The trend chart (design `charts.jsx` → `AreaTrend`): a smoothed accent line over a soft gradient area, on
/// an adaptive y-domain that hugs the data; gridlines at max/mid/min; a dashed goal line when the next
/// milestone/target is close enough to mean something; a today marker; and a touch scrubber that snaps to the
/// nearest day and reads its exact value and date. The line draws itself in over one second on mount. All the
/// domain/goal/snapping math comes from the shared `TrendChartGeometry`, so this can't drift from Android.
struct AreaTrendView: View {
    let trend: PointTrend
    let line: [Int64]
    let range: TrendRange
    let accent: Color

    @State private var progress: CGFloat = 0
    @State private var scrub: Int? = nil
    @State private var hideWork: DispatchWorkItem? = nil

    private let padX: CGFloat = 8
    private let padT: CGFloat = 20
    private let padB: CGFloat = 26

    var body: some View {
        let domain = trend.chartYDomain(line: line.map { KotlinLong(value: $0) })
        let goal = trend.chartGoalLine()?.int64Value

        GeometryReader { geo in
            let size = geo.size
            let pts = points(in: size, domain: domain)

            ZStack {
                Canvas { context, _ in
                    drawBase(context: context, size: size, domain: domain, goal: goal, pts: pts)
                }
                TrendLineShape(points: pts)
                    .trim(from: 0, to: progress)
                    .stroke(accent, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                Canvas { context, _ in
                    drawMarker(context: context, size: size, pts: pts)
                }
            }
            // a press reads a day; a horizontal drag scrubs; vertical drags fall through to the scroll view
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        scrub = Int(TrendChartGeometryKt.scrubIndexOf(
                            x: gesture.location.x, width: size.width, padX: padX, count: Int32(line.count)
                        ))
                        // failsafe: if the scroll view steals the drag, onEnded never fires — still fade out
                        hideSoon(after: 2.5)
                    }
                    .onEnded { _ in hideSoon(after: 0.9) }
            )
        }
        .frame(height: 196)
        .onAppear { animateIn() }
        .onChange(of: line) { _ in animateIn() }
    }

    private func animateIn() {
        progress = 0
        withAnimation(.easeInOut(duration: 1)) { progress = 1 }
    }

    /// Keeps the tooltip readable for a beat after the finger lifts, like the prototype.
    private func hideSoon(after delay: TimeInterval) {
        hideWork?.cancel()
        let work = DispatchWorkItem { scrub = nil }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
    }

    private func points(in size: CGSize, domain: ChartYDomain) -> [CGPoint] {
        guard !line.isEmpty else { return [] }
        let stepX = (size.width - padX * 2) / CGFloat(max(1, line.count - 1))
        return line.enumerated().map { i, v in
            CGPoint(x: padX + CGFloat(i) * stepX, y: y(Double(v), in: size, domain: domain))
        }
    }

    private func y(_ value: Double, in size: CGSize, domain: ChartYDomain) -> CGFloat {
        padT + (1 - (value - domain.min) / domain.span) * (size.height - padT - padB)
    }

    private func drawBase(context: GraphicsContext, size: CGSize, domain: ChartYDomain, goal: Int64?, pts: [CGPoint]) {
        guard !pts.isEmpty else { return }

        // gridlines + y labels at max / mid / min
        for tick in [domain.max, (domain.max + domain.min) / 2, domain.min] {
            let ty = y(tick, in: size, domain: domain)
            var grid = Path()
            grid.move(to: CGPoint(x: padX, y: ty))
            grid.addLine(to: CGPoint(x: size.width - padX, y: ty))
            context.stroke(grid, with: .color(.lineSoft), lineWidth: 1)
            context.draw(
                Text(formatCount(tick)).font(.points(9.5, .semibold)).foregroundColor(.inkFaint),
                at: CGPoint(x: padX, y: ty - 4),
                anchor: .bottomLeading
            )
        }

        // dashed goal line (next milestone / daily target) when it falls inside the domain
        if let goal = goal, Double(goal) <= domain.max {
            let gy = y(Double(goal), in: size, domain: domain)
            var dash = Path()
            dash.move(to: CGPoint(x: padX, y: gy))
            dash.addLine(to: CGPoint(x: size.width - padX, y: gy))
            context.stroke(
                dash,
                with: .color(accent.opacity(0.6)),
                style: StrokeStyle(lineWidth: 1.25, dash: [3, 4])
            )
            let label = trend.type.mode == .daily ? "target \(goal)" : formatCount(goal)
            context.draw(
                Text(label).font(.points(10, .bold)).foregroundColor(accent),
                at: CGPoint(x: size.width - padX, y: gy - 5),
                anchor: .bottomTrailing
            )
        }

        // the soft gradient area under the line
        var area = smoothPath(pts)
        area.addLine(to: CGPoint(x: pts[pts.count - 1].x, y: size.height - padB))
        area.addLine(to: CGPoint(x: pts[0].x, y: size.height - padB))
        area.closeSubpath()
        context.fill(
            area,
            with: .linearGradient(
                Gradient(colors: [accent.opacity(0.22), accent.opacity(0)]),
                startPoint: CGPoint(x: 0, y: padT),
                endPoint: CGPoint(x: 0, y: size.height - padB)
            )
        )
    }

    private func drawMarker(context: GraphicsContext, size: CGSize, pts: [CGPoint]) {
        guard !pts.isEmpty else { return }

        guard let active = scrub, pts.indices.contains(active) else {
            // today marker
            let last = pts[pts.count - 1]
            context.fill(Path(ellipseIn: CGRect(x: last.x - 7, y: last.y - 7, width: 14, height: 14)), with: .color(accent.opacity(0.16)))
            context.fill(Path(ellipseIn: CGRect(x: last.x - 3.5, y: last.y - 3.5, width: 7, height: 7)), with: .color(accent))
            return
        }

        let p = pts[active]
        var guide = Path()
        guide.move(to: CGPoint(x: p.x, y: padT))
        guide.addLine(to: CGPoint(x: p.x, y: size.height - padB))
        context.stroke(guide, with: .color(accent.opacity(0.45)), lineWidth: 1.25)
        context.fill(Path(ellipseIn: CGRect(x: p.x - 9, y: p.y - 9, width: 18, height: 18)), with: .color(accent.opacity(0.16)))
        context.fill(Path(ellipseIn: CGRect(x: p.x - 4, y: p.y - 4, width: 8, height: 8)), with: .color(accent))
        context.stroke(Path(ellipseIn: CGRect(x: p.x - 4, y: p.y - 4, width: 8, height: 8)), with: .color(.surface), lineWidth: 1.5)

        // tooltip: exact value + the day it belongs to
        let daysAgo = Int32((range == .year ? 7 : 1) * (line.count - 1 - active))
        let date = Kotlinx_datetimeLocalDate.companion.fromEpochDays(epochDays: trend.today.toEpochDays() - daysAgo)
        let unit = trend.type.unit.isEmpty ? "" : " \(trend.type.unit)"
        drawTooltip(
            context: context,
            size: size,
            title: formatCount(line[active]) + unit,
            subtitle: TrendChartGeometryKt.dateLabelOf(date: date, today: trend.today),
            anchor: p
        )
    }

    private func drawTooltip(context: GraphicsContext, size: CGSize, title: String, subtitle: String, anchor: CGPoint) {
        let w: CGFloat = 96
        let h: CGFloat = 38
        let above = anchor.y - 52 > padT
        let tx = min(max(anchor.x - w / 2, 2), size.width - w - 2)
        let ty = above ? anchor.y - 52 : anchor.y + 14
        context.fill(
            Path(roundedRect: CGRect(x: tx, y: ty, width: w, height: h), cornerRadius: 9),
            with: .color(.ink)
        )
        context.draw(
            Text(title).font(.points(13, .bold)).foregroundColor(.bg),
            at: CGPoint(x: tx + w / 2, y: ty + 5),
            anchor: .top
        )
        context.draw(
            Text(subtitle).font(.points(10, .semibold)).foregroundColor(.bg.opacity(0.65)),
            at: CGPoint(x: tx + w / 2, y: ty + h - 4),
            anchor: .bottom
        )
    }
}

/// The accent line as a `Shape` so `.trim` can animate the draw-in.
private struct TrendLineShape: Shape {
    let points: [CGPoint]

    func path(in rect: CGRect) -> Path { smoothPath(points) }
}

/// Catmull-Rom-style smoothing (t = 0.18), the same curve the prototype and the Android port draw.
private func smoothPath(_ pts: [CGPoint]) -> Path {
    var path = Path()
    guard pts.count >= 2 else { return path }
    path.move(to: pts[0])
    let t: CGFloat = 0.18
    for i in 0..<(pts.count - 1) {
        let p0 = i > 0 ? pts[i - 1] : pts[i]
        let p1 = pts[i]
        let p2 = pts[i + 1]
        let p3 = i + 2 < pts.count ? pts[i + 2] : p2
        path.addCurve(
            to: p2,
            control1: CGPoint(x: p1.x + (p2.x - p0.x) * t, y: p1.y + (p2.y - p0.y) * t),
            control2: CGPoint(x: p2.x - (p3.x - p1.x) * t, y: p2.y - (p3.y - p1.y) * t)
        )
    }
    return path
}

// MARK: - Bars

/// Per-bucket bars (design `charts.jsx` → `BarChart`): a faint full-height track behind each bar, rounded
/// caps, the last (current) bucket at full opacity, and thinned axis labels.
struct TrendBarsView: View {
    let bars: [Int64]
    let labels: [String]
    let accent: Color

    var body: some View {
        Canvas { context, size in
            guard !bars.isEmpty else { return }
            let padT: CGFloat = 12
            let padB: CGFloat = 22
            let n = bars.count
            let gap: CGFloat = n > 20 ? 2 : (n > 10 ? 4 : 7)
            let bw = (size.width - gap * CGFloat(n - 1)) / CGFloat(n)
            let plotH = size.height - padB - padT
            let maxValue = Double(max(1, bars.max() ?? 1))
            let radius = min(4, bw / 2)

            for (i, v) in bars.enumerated() {
                let bx = CGFloat(i) * (bw + gap)
                context.fill(
                    Path(roundedRect: CGRect(x: bx, y: padT, width: bw, height: plotH), cornerRadius: radius),
                    with: .color(.ringTrack.opacity(0.5))
                )
                let h = max(Double(v) / maxValue * plotH, v > 0 ? 3 : 0)
                if h > 0 {
                    context.fill(
                        Path(roundedRect: CGRect(x: bx, y: size.height - padB - h, width: bw, height: h), cornerRadius: radius),
                        with: .color(accent.opacity(i == n - 1 ? 1 : 0.78))
                    )
                }
            }

            // thin the labels to at most ~7 so month/year stay readable
            let stride = Int((Double(n) / 7.0).rounded(.up))
            for i in Swift.stride(from: 0, to: n, by: max(1, stride)) {
                guard i < labels.count else { continue }
                let text = n == 7 ? String(labels[i].prefix(1)) : labels[i]
                let cx = CGFloat(i) * (bw + gap) + bw / 2
                context.draw(
                    Text(text).font(.points(11, .semibold)).foregroundColor(.inkFaint),
                    at: CGPoint(x: min(cx, size.width - 8), y: size.height),
                    anchor: .bottom
                )
            }
        }
        .frame(height: 168)
    }
}

// MARK: - Calendar heatmap

/// The calendar heatmap (design `charts.jsx` → `Heatmap`): 20 week columns × Mon–Sun rows, M/W/F row labels,
/// zero days as a faint track and active days as the accent with opacity ramping against the busiest day.
struct HeatmapView: View {
    let heatmap: Heatmap
    let bestDay: Int64
    let accent: Color

    var body: some View {
        let columns = heatmap.columns
        let cols = columns.count
        // prototype units: 13px cells, 4px gaps, 22px weekday gutter — keep its aspect and scale to width
        let unitW: CGFloat = 22 + CGFloat(cols) * 17 - 4
        let unitH: CGFloat = 7 * 17 - 4

        Canvas { context, size in
            guard cols > 0 else { return }
            let s = size.width / unitW
            let cell = 13 * s
            let pitch = 17 * s
            let gutter = 22 * s
            let maxValue = Double(max(1, bestDay))
            let radius = 3 * s

            for (row, label) in [(0, "M"), (2, "W"), (4, "F")] {
                context.draw(
                    Text(label).font(.points(10, .semibold)).foregroundColor(.inkFaint),
                    at: CGPoint(x: 0, y: CGFloat(row) * pitch + cell),
                    anchor: .bottomLeading
                )
            }

            for (ci, column) in columns.enumerated() {
                for (ri, day) in column.enumerated() {
                    let value = day.value
                    let level = value <= 0 ? 0 : min(0.22 + Double(value) / maxValue * 0.78, 1)
                    context.fill(
                        Path(roundedRect: CGRect(
                            x: gutter + CGFloat(ci) * pitch, y: CGFloat(ri) * pitch, width: cell, height: cell
                        ), cornerRadius: radius),
                        with: .color(value <= 0 ? Color.ringTrack.opacity(0.5) : accent.opacity(level))
                    )
                }
            }
        }
        .aspectRatio(unitW / unitH, contentMode: .fit)
    }
}
