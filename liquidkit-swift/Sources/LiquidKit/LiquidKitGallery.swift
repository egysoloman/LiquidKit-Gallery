import SwiftUI
#if SKIP
import io.liquidkit.skip.__
#endif

/// Cross-platform GPU MeshGradient (`MeshGradient` on iOS 18+/macOS 15+, AGSL `LiquidKitMeshGradient` on Android).
public struct LiquidMeshGradientView: View {
    public let width: Int
    public let height: Int
    public let colors: [LiquidRGBA]

    public init(width: Int, height: Int, colors: [LiquidRGBA]) {
        self.width = max(2, width)
        self.height = max(2, height)
        self.colors = colors
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitMeshGradient(
                width: width,
                height: height,
                colors: colors.map { $0.composeColor },
                modifier: context.modifier
            )
        }
        #else
        if colors.count >= width * height {
            MeshGradient(
                width: width,
                height: height,
                points: Self.uniformPoints(width: width, height: height),
                colors: Array(colors.prefix(width * height)).map(\.swiftUIColor)
            )
        } else {
            LinearGradient(
                colors: colors.isEmpty ? [.blue, .purple] : colors.map(\.swiftUIColor),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        #endif
    }

    #if !SKIP
    private static func uniformPoints(width: Int, height: Int) -> [SIMD2<Float>] {
        var pts: [SIMD2<Float>] = []
        pts.reserveCapacity(width * height)
        for row in 0..<height {
            let y = Float(row) / Float(max(1, height - 1))
            for col in 0..<width {
                let x = Float(col) / Float(max(1, width - 1))
                pts.append(SIMD2<Float>(x, y))
            }
        }
        return pts
    }
    #endif
}

/// Cross-platform multi-date calendar picker using ISO `YYYY-MM-DD` strings for frictionless Skip bridging.
public struct LiquidMultiDatePickerControl: View {
    @Binding private var selectedIsoDates: [String]
    public let tint: LiquidRGBA
    public let initialYear: Int
    public let initialMonth: Int

    public init(
        selectedIsoDates: Binding<[String]>,
        tint: LiquidRGBA = .accentBlue,
        initialYear: Int = 2026,
        initialMonth: Int = 11
    ) {
        _selectedIsoDates = selectedIsoDates
        self.tint = tint
        self.initialYear = initialYear
        self.initialMonth = initialMonth
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitMultiDatePicker(
                selectedIsoDates: selectedIsoDates,
                onSelectedIsoDatesChange: { selectedIsoDates = $0 },
                tint: tint.composeColor,
                initialYear: initialYear,
                initialMonth: initialMonth,
                modifier: context.modifier
            )
        }
        #else
        VStack(alignment: .leading, spacing: 8) {
            Text("\(initialYear)年\(initialMonth)月 · 已选 \(selectedIsoDates.count) 天")
                .font(.subheadline.weight(.semibold))
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 6) {
                ForEach(1...30, id: \.self) { day in
                    let iso = String(format: "%04d-%02d-%02d", initialYear, initialMonth, day)
                    let active = selectedIsoDates.contains(iso)
                    Button {
                        if active {
                            selectedIsoDates.removeAll { $0 == iso }
                        } else {
                            selectedIsoDates.append(iso)
                            selectedIsoDates.sort()
                        }
                    } label: {
                        Text("\(day)")
                            .font(.callout.weight(active ? .semibold : .regular))
                            .frame(maxWidth: .infinity, minHeight: 32)
                            .background(active ? tint.swiftUIColor : Color.clear, in: Circle())
                            .foregroundStyle(active ? .white : .primary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        #endif
    }
}

/// Adaptive Inspector presentation (`.inspector` on Apple, responsive trailing drawer/sheet `LiquidKitInspector` on Android).
public struct LiquidInspectorContainer<InspectorContent: View, Content: View>: View {
    @Binding private var isPresented: Bool
    public let title: String
    private let inspectorContent: () -> InspectorContent
    private let content: () -> Content

    public init(
        isPresented: Binding<Bool>,
        title: String,
        @ViewBuilder inspectorContent: @escaping () -> InspectorContent,
        @ViewBuilder content: @escaping () -> Content
    ) {
        _isPresented = isPresented
        self.title = title
        self.inspectorContent = inspectorContent
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitInspector(
                isPresented: isPresented,
                title: title,
                onDismiss: { isPresented = false },
                modifier: context.modifier,
                inspectorContent: { inspectorContent().Compose(context: context.content()) },
                content: { content().Compose(context: context.content()) }
            )
        }
        #else
        content()
            .inspector(isPresented: $isPresented) {
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            inspectorContent()
                        }
                        .padding()
                    }
                    .navigationTitle(title)
                }
            }
        #endif
    }
}

/// Floating glass accessory bar docked above `LiquidFloatingTabBar` (matching `.tabViewBottomAccessory`).
public struct LiquidTabViewBottomAccessoryView<Content: View>: View {
    public let onTap: (() -> Void)?
    private let content: () -> Content

    public init(onTap: (() -> Void)? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.onTap = onTap
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitTabViewBottomAccessory(
                modifier: context.modifier,
                onClick: onTap
            ) {
                content().Compose(context: context.content())
            }
        }
        #else
        HStack(spacing: 10) {
            content()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial, in: Capsule())
        .onTapGesture { onTap?() }
        #endif
    }
}

/// Wrapping flow layout (`LiquidKitFlowLayout` on Android, custom SwiftUI `Layout` on Apple).
public struct LiquidFlowLayoutView<Content: View>: View {
    public let horizontalSpacing: Double
    public let verticalSpacing: Double
    private let content: () -> Content

    public init(
        horizontalSpacing: Double = 8,
        verticalSpacing: Double = 8,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitFlowLayout(
                horizontalSpacing: horizontalSpacing,
                verticalSpacing: verticalSpacing,
                modifier: context.modifier
            ) {
                content().Compose(context: context.content())
            }
        }
        #else
        _AppleFlowLayout(horizontalSpacing: horizontalSpacing, verticalSpacing: verticalSpacing) {
            content()
        }
        #endif
    }
}

#if !SKIP
private struct _AppleFlowLayout: Layout {
    let horizontalSpacing: Double
    let verticalSpacing: Double

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var lineH: CGFloat = 0
        var usedW: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxW {
                x = 0
                y += lineH + verticalSpacing
                lineH = 0
            }
            x += size.width + horizontalSpacing
            lineH = max(lineH, size.height)
            usedW = max(usedW, min(maxW, x - horizontalSpacing))
        }
        return CGSize(width: usedW, height: y + lineH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var lineH: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += lineH + verticalSpacing
                lineH = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + horizontalSpacing
            lineH = max(lineH, size.height)
        }
    }
}
#endif

/// Cross-platform redacted placeholder container (`SkipLiquidKitRedacted` on Android, `.redacted(reason: .placeholder)` on Apple).
public struct LiquidRedactedView<Content: View>: View {
    public let active: Bool
    public let cornerRadius: Double
    private let content: () -> Content

    public init(active: Bool, cornerRadius: Double = 8, @ViewBuilder content: @escaping () -> Content) {
        self.active = active
        self.cornerRadius = cornerRadius
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitRedacted(
                active: active,
                cornerRadius: cornerRadius,
                modifier: context.modifier
            ) {
                content().Compose(context: context.content())
            }
        }
        #else
        if active {
            content().redacted(reason: .placeholder)
        } else {
            content()
        }
        #endif
    }
}

public extension View {
    /// Applies a cross-platform shimmer/skeleton redaction placeholder matching `.redacted(reason: .placeholder)`.
    func liquidRedacted(active: Bool, cornerRadius: Double = 8) -> some View {
        LiquidRedactedView(active: active, cornerRadius: cornerRadius) { self }
    }
}

/// Long-press drag-to-reorder grid mirroring iOS 27 `.reorderContainer` / `.reorderable()`.
public struct LiquidReorderableGridView<Tile: View>: View {
    @Binding private var ids: [String]
    public let columns: Int
    public let tileHeight: Double
    public let spacing: Double
    private let tileContent: (String, Bool) -> Tile

    public init(
        ids: Binding<[String]>,
        columns: Int = 3,
        tileHeight: Double = 74,
        spacing: Double = 10,
        @ViewBuilder tileContent: @escaping (String, Bool) -> Tile
    ) {
        _ids = ids
        self.columns = max(1, columns)
        self.tileHeight = tileHeight
        self.spacing = spacing
        self.tileContent = tileContent
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitReorderableGrid(
                ids: ids,
                onReorder: { ids = $0 },
                columns: columns,
                tileHeight: tileHeight,
                spacing: spacing,
                modifier: context.modifier
            ) { id, dragging in
                tileContent(id, dragging).Compose(context: context.content())
            }
        }
        #else
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: spacing), count: columns),
            spacing: spacing
        ) {
            ForEach(ids, id: \.self) { id in
                tileContent(id, false)
                    .frame(height: tileHeight)
            }
        }
        #endif
    }
}

/// Flat descriptor for a node in `LiquidOutlineGroupView`.
public struct LiquidOutlineItem: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let parentId: String?
    public let systemImage: String?

    public init(id: String, title: String, parentId: String? = nil, systemImage: String? = nil) {
        self.id = id
        self.title = title
        self.parentId = parentId
        self.systemImage = systemImage
    }
}

/// Collapsible recursive hierarchy mirroring SwiftUI `OutlineGroup`.
public struct LiquidOutlineGroupView: View {
    public let items: [LiquidOutlineItem]
    public let tint: LiquidRGBA
    public let onSelectLeaf: ((String) -> Void)?

    public init(
        items: [LiquidOutlineItem],
        tint: LiquidRGBA = .accentBlue,
        onSelectLeaf: ((String) -> Void)? = nil
    ) {
        self.items = items
        self.tint = tint
        self.onSelectLeaf = onSelectLeaf
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitOutlineGroup(
                ids: items.map { $0.id },
                titles: items.map { $0.title },
                parentIds: items.map { $0.parentId },
                systemImages: items.map { $0.systemImage },
                tint: tint.composeColor,
                onSelectLeafId: onSelectLeaf,
                modifier: context.modifier
            )
        }
        #else
        VStack(alignment: .leading, spacing: 6) {
            ForEach(items) { item in
                HStack(spacing: 8) {
                    if item.parentId != nil {
                        Spacer().frame(width: 16)
                    }
                    if let sym = item.systemImage {
                        Image(systemName: sym).foregroundStyle(tint.swiftUIColor)
                    }
                    Text(item.title)
                    Spacer()
                }
                .contentShape(Rectangle())
                .onTapGesture { onSelectLeaf?(item.id) }
            }
        }
        #endif
    }
}

/// Sortable multi-column table mirroring SwiftUI `Table`.
public struct LiquidTableView: View {
    public let headers: [String]
    public let rows: [[String]]
    public let tint: LiquidRGBA

    public init(headers: [String], rows: [[String]], tint: LiquidRGBA = .accentBlue) {
        self.headers = headers
        self.rows = rows
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitTable(
                headers: headers,
                rows: rows,
                tint: tint.composeColor,
                modifier: context.modifier
            )
        }
        #else
        VStack(spacing: 0) {
            HStack {
                ForEach(Array(headers.enumerated()), id: \.offset) { _, header in
                    Text(header)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.vertical, 8)
            Divider()
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, cell in
                        Text(cell)
                            .font(.subheadline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.vertical, 8)
                Divider()
            }
        }
        #endif
    }
}

/// Series point shared by `LiquidBarChartView`.
public struct LiquidChartPoint: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let label: String
    public let series: String
    public let value: Double
    public let color: LiquidRGBA

    public init(
        id: String? = nil,
        label: String,
        series: String = "default",
        value: Double,
        color: LiquidRGBA = .accentBlue
    ) {
        self.id = id ?? "\(series):\(label)"
        self.label = label
        self.series = series
        self.value = value
        self.color = color
    }
}

/// Slice descriptor shared by `LiquidPieDonutChartView`.
public struct LiquidPieChartSlice: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let label: String
    public let value: Double
    public let color: LiquidRGBA

    public init(id: String? = nil, label: String, value: Double, color: LiquidRGBA) {
        self.id = id ?? label
        self.label = label
        self.value = value
        self.color = color
    }
}

/// Cross-platform bar chart (`SkipLiquidKitBarChart` on Android, SwiftUI capsule bars on Apple).
public struct LiquidBarChartView: View {
    public let points: [LiquidChartPoint]
    public let horizontal: Bool

    public init(points: [LiquidChartPoint], horizontal: Bool = false) {
        self.points = points
        self.horizontal = horizontal
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitBarChart(
                labels: points.map { $0.label },
                values: points.map { $0.value },
                colors: points.map { $0.color.composeColor },
                series: points.map { $0.series },
                horizontal: horizontal,
                modifier: context.modifier
            )
        }
        #else
        let maxVal = max(points.map(\.value).max() ?? 1, 1)
        if horizontal {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(points) { pt in
                    HStack(spacing: 8) {
                        Text(pt.label).font(.caption).frame(width: 54, alignment: .leading)
                        GeometryReader { geo in
                            RoundedRectangle(cornerRadius: 6)
                                .fill(pt.color.swiftUIColor)
                                .frame(width: geo.size.width * max(0, min(1, pt.value / maxVal)))
                        }
                        .frame(height: 16)
                    }
                }
            }
        } else {
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(points) { pt in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(pt.color.swiftUIColor)
                            .frame(height: max(8, 120 * (pt.value / maxVal)))
                        Text(pt.label).font(.caption2).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 144, alignment: .bottom)
        }
        #endif
    }
}

/// Cross-platform line/area chart with optional rule threshold and interactive scrub index.
public struct LiquidLineAreaChartView: View {
    public let labels: [String]
    public let values: [Double]
    public let lineColor: LiquidRGBA
    public let ruleValue: Double?
    public let ruleColor: LiquidRGBA
    @Binding private var selectedIndex: Int?

    public init(
        labels: [String],
        values: [Double],
        lineColor: LiquidRGBA = LiquidRGBA(red: 0, green: 0.78, blue: 0.75),
        ruleValue: Double? = nil,
        ruleColor: LiquidRGBA = LiquidRGBA(red: 1.0, green: 0.58, blue: 0.0),
        selectedIndex: Binding<Int?> = .constant(nil)
    ) {
        self.labels = labels
        self.values = values
        self.lineColor = lineColor
        self.ruleValue = ruleValue
        self.ruleColor = ruleColor
        _selectedIndex = selectedIndex
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitLineAreaChart(
                labels: labels,
                values: values,
                lineColor: lineColor.composeColor,
                ruleValue: ruleValue,
                ruleColor: ruleColor.composeColor,
                selectedIndex: selectedIndex,
                onSelectIndex: { selectedIndex = $0 },
                modifier: context.modifier
            )
        }
        #else
        Canvas { ctx, size in
            guard values.count >= 2 else { return }
            let maxVal = max(values.max() ?? 1, ruleValue ?? 0, 1) * 1.12
            var path = Path()
            for (i, v) in values.enumerated() {
                let x = size.width * CGFloat(i) / CGFloat(values.count - 1)
                let y = size.height * (1 - CGFloat(v / maxVal))
                if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
                else { path.addLine(to: CGPoint(x: x, y: y)) }
            }
            ctx.stroke(path, with: .color(lineColor.swiftUIColor), lineWidth: 2.5)
        }
        .frame(height: 180)
        #endif
    }
}

/// Cross-platform pie/donut chart (`SkipLiquidKitPieDonutChart` on Android, SwiftUI `Canvas` on Apple).
public struct LiquidPieDonutChartView<Center: View>: View {
    public let slices: [LiquidPieChartSlice]
    public let innerRadiusRatio: Double
    public let angularInsetDegrees: Double
    private let centerContent: () -> Center

    public init(
        slices: [LiquidPieChartSlice],
        innerRadiusRatio: Double = 0.58,
        angularInsetDegrees: Double = 2.0,
        @ViewBuilder centerContent: @escaping () -> Center = { EmptyView() }
    ) {
        self.slices = slices
        self.innerRadiusRatio = innerRadiusRatio
        self.angularInsetDegrees = angularInsetDegrees
        self.centerContent = centerContent
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitPieDonutChart(
                labels: slices.map { $0.label },
                values: slices.map { $0.value },
                colors: slices.map { $0.color.composeColor },
                innerRadiusRatio: innerRadiusRatio,
                angularInsetDegrees: angularInsetDegrees,
                modifier: context.modifier
            ) {
                centerContent().Compose(context: context.content())
            }
        }
        #else
        ZStack {
            Canvas { ctx, size in
                let total = slices.reduce(0.0) { $0 + max(0, $1.value) }
                guard total > 0 else { return }
                let radius = min(size.width, size.height) * 0.45
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                var startAngle = -Double.pi / 2
                for slice in slices {
                    let sweep = (max(0, slice.value) / total) * 2 * Double.pi
                    var p = Path()
                    p.addArc(
                        center: center,
                        radius: radius,
                        startAngle: .radians(startAngle),
                        endAngle: .radians(startAngle + sweep),
                        clockwise: false
                    )
                    let strokeWidth = radius * (1 - max(0, min(0.9, innerRadiusRatio)))
                    ctx.stroke(p, with: .color(slice.color.swiftUIColor), lineWidth: strokeWidth)
                    startAngle += sweep
                }
            }
            .frame(height: 180)
            centerContent()
        }
        #endif
    }
}

/// Cross-platform continuous math plot (`sin`/`cos` curves matching iOS 27 Swift Charts `LinePlot`).
public struct LiquidMathPlotChartView: View {
    public let frequency: Double

    public init(frequency: Double = 1.0) {
        self.frequency = frequency
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitMathPlotChart(frequency: frequency, modifier: context.modifier)
        }
        #else
        Canvas { ctx, size in
            let midY = size.height * 0.5
            let amp = size.height * 0.38
            var sinP = Path()
            var cosP = Path()
            let steps = 96
            for i in 0...steps {
                let t = Double(i) / Double(steps)
                let x = t * size.width
                let rad = t * 2 * Double.pi * frequency
                let sy = midY - sin(rad) * amp
                let cy = midY - cos(rad) * amp
                if i == 0 {
                    sinP.move(to: CGPoint(x: x, y: sy))
                    cosP.move(to: CGPoint(x: x, y: cy))
                } else {
                    sinP.addLine(to: CGPoint(x: x, y: sy))
                    cosP.addLine(to: CGPoint(x: x, y: cy))
                }
            }
            ctx.stroke(sinP, with: .color(.blue), lineWidth: 2.5)
            ctx.stroke(cosP, with: .color(.orange), lineWidth: 2.5)
        }
        .frame(height: 170)
        #endif
    }
}
