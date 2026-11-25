import SwiftUI
import UIKit.UIGestureRecognizerSubclass

public enum FoldComponentFamily: String, CaseIterable, Identifiable {
    case buttons = "按钮与链接", choices = "选择与数值", input = "文本与输入"
    case status = "状态与进度", shapes = "图像与形状", containers = "列表与容器"
    case presentation = "导航与弹层", glass = "玻璃材质", press = "按压与回弹"
    public var id: Self { self }
    public var symbol: String {
        switch self {
        case .buttons: "hand.tap"
        case .choices: "slider.horizontal.3"
        case .input: "text.cursor"
        case .status: "gauge.with.dots.needle.50percent"
        case .shapes: "square.on.circle"
        case .containers: "list.bullet.rectangle"
        case .presentation: "menucard"
        case .glass: "circle.lefthalf.filled"
        case .press: "hand.draw"
        }
    }
}

/// Interactive examples rendered by the system at the current text size and appearance.
public struct FoldComponentGallery: View {
    public init() {}
    @State private var showingPressSample = false
    public var body: some View {
        List(FoldComponentFamily.allCases) { family in
            let label = Label(family.rawValue, systemImage: family.symbol)
            Group {
                if family == .press {
                    Button { showingPressSample = true } label: { label }
                } else {
                    NavigationLink { FoldComponentExamples(family) } label: { label }
                }
            }.accessibilityIdentifier("family." + family.rawValue)
        }.navigationTitle("全部组件").navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(isPresented: $showingPressSample) {
            NavigationStack {
                FoldComponentExamples(.press).toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("完成") { showingPressSample = false }
                    }
                }
            }
        }
    }
}

public struct FoldComponentExamples: View {
    @State private var archive = ""
    let family: FoldComponentFamily
    public init(_ family: FoldComponentFamily) { self.family = family }
    @State private var value = 0.5
    @State private var selected = "列表"
    @State private var enabled = true
    @State private var count = 2
    @State private var date = Date()
    @State private var dates = Set<DateComponents>()
    @State private var color = Color.teal
    @State private var text = ""
    @State private var secret = ""
    @State private var notes = ""
    @State private var expanded = true
    @State private var prompt: Prompt?
    @State private var notice = ""
    private enum Prompt: String, Identifiable { case alert, deleteSample, changes, confirm, sheet, popover, rename; var id: Self { self } }
    private func showing(_ p: Prompt) -> Binding<Bool> {
        Binding(get: { prompt == p }, set: { if !$0 { prompt = nil } })
    }
    public var body: some View {
        Group {
            switch family {
            case .buttons: buttons
            case .choices: choices
            case .input: inputs
            case .status: status
            case .shapes: images
            case .containers: containers
            case .presentation: presentation
            case .glass: material
            case .press: FoldPressSample()
            }
        }.navigationTitle(family.rawValue).navigationBarTitleDisplayMode(.inline)
        .alert("折页参照", isPresented: showing(.alert)) { Button("完成") {} } message: { Text(notice) }
        .alert("重命名", isPresented: showing(.rename)) {
            TextField("名称", text: $text)
            Button("保存") { notice = text }
            Button("取消", role: .cancel) {}
        }
        .alert("移除样张？", isPresented: showing(.deleteSample)) {
            Button("取消", role: .cancel) {}
            Button("移除", role: .destructive) { notice = "已移除样张。" }
        } message: { Text("仅影响本次演示。") }
        .alert("保存更改？", isPresented: showing(.changes)) {
            Button("保存") { notice = "已保存更改。" }
            Button("放弃更改", role: .destructive) { notice = "已放弃更改。" }
            Button("取消", role: .cancel) {}
        }
        .confirmationDialog("显示方式", isPresented: showing(.confirm), titleVisibility: .visible) {
            ForEach(["列表", "网格"], id: \.self) { key in Button(key) { selected = key } }
        }
        .sheet(isPresented: showing(.sheet)) {
            NavigationStack { Form { TextField("名称", text: $text) }.navigationTitle("文件信息")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { prompt = nil } } } }
                .presentationDetents([.medium, .large])
        }
    }
    private var buttons: some View {
        Form {
            NavigationLink("工具栏过渡") { FoldToolbarTransitions() }
            Section("按钮样式") {
                Button("普通按钮") { count += 1 }.buttonStyle(.plain)
                Button("无边框按钮") { count += 1 }.buttonStyle(.borderless)
                Button("带底色按钮") { count += 1 }.buttonStyle(.bordered)
                Button("强调按钮") { count += 1 }.buttonStyle(.borderedProminent)
                if #available(iOS 26.0, *) {
                    Button("玻璃按钮") { count += 1 }.buttonStyle(.glass)
                    Button("强调玻璃") { count += 1 }.buttonStyle(.glassProminent)
                }
                LabeledContent("点击计数", value: String(count))
            }
            Section("内容操作") {
                EditButton()
                PasteButton(payloadType: String.self) { strings in text = strings.first ?? "" }
                Label(text.isEmpty ? "项目名称" : text, systemImage: "doc")
                    .contextMenu { RenameButton() }.renameAction { prompt = .rename }
                Link("组件使用说明", destination: URL(string: "foldreference://help")!)
                    .environment(\.openURL, OpenURLAction { _ in notice = "可切换系统外观，并在材质页比较透明度。"; prompt = .alert; return .handled })
                ShareLink(item: "折页组件样张")
            }
        }
    }
    private var options: some View {
        ForEach(["列表", "网格", "分栏"], id: \.self) { Text($0).tag($0) }
    }
    private var choices: some View {
        Form {
            Section("数值") {
                Toggle("显示预览", isOn: $enabled)
                Slider(value: $value).accessibilityLabel("调整进度")
                Stepper("数量  " + String(count), value: $count, in: 0...30)
            }
            Section("选择器") {
                Picker("菜单", selection: $selected) { options }.pickerStyle(.menu)
                Picker("分段", selection: $selected) { options }.pickerStyle(.segmented)
                DatePicker("日期", selection: $date, displayedComponents: .date)
                ColorPicker("标记颜色", selection: $color)
                NavigationLink("滚轮选择器") { Picker("显示方式", selection: $selected) { options }.pickerStyle(.wheel).navigationTitle("滚轮选择器") }
                NavigationLink("多日期选择") { MultiDatePicker("选取日期", selection: $dates).padding().navigationTitle("多日期选择") }
            }
        }
    }
    private var inputs: some View {
        Form {
            Section("文本") { Text("折页文库").font(.title2.bold()); Label("馆藏索引", systemImage: "doc.text"); LabeledContent("格式", value: "纯文本") }
            Section("输入") {
                TextField("名称", text: $text); SecureField("示例口令", text: $secret)
                TextEditor(text: $notes).frame(minHeight: 100).accessibilityLabel("备注")
            }
        }
    }
    private var status: some View {
        Form {
            Section("进度") {
                Slider(value: $value).accessibilityLabel("调整进度")
                ProgressView(value: value) { Text("读取进度") }.accessibilityValue(Text(value, format: .percent))
                ProgressView("正在读取").progressViewStyle(.circular)
                Gauge(value: value) { Text("占用") } currentValueLabel: { Text(value, format: .percent) }
                Gauge(value: value) { Image(systemName: "internaldrive") } currentValueLabel: { Text(value, format: .percent) }
                    .gaugeStyle(.accessoryCircularCapacity)
            }
            ContentUnavailableView("空文件夹", systemImage: "folder")
        }
    }
    private var material: some View {
        Form {
            Section {
                ZStack {
                    LinearGradient(colors: [.indigo, .teal, .orange], startPoint: .topLeading, endPoint: .bottomTrailing)
                    VStack { ForEach(0..<4) { n in Text("Fold   " + String(n + 1) + "   材质校对").bold().frame(maxWidth: .infinity) } }
                    HStack {
                        if #available(iOS 26.0, *) {
                            Image(systemName: "circle.grid.2x2").frame(width: 48, height: 48).glassEffect(.regular, in: Circle())
                        }
                        Label("搜索", systemImage: "magnifyingglass").frame(maxWidth: .infinity, minHeight: 48).foldGlass(in: Capsule(), opacity: value)
                    }.padding(12)
                }.frame(height: 154).clipShape(.rect(cornerRadius: 18))
                FoldOpacityControl(value: $value)
                LabeledContent("应用衬底", value: value.formatted(.percent.precision(.fractionLength(0))))
            }
            if #available(iOS 26.0, *) {
                Section {
                    ZStack(alignment: .top) {
                        HStack(spacing: 0) {
                            Color(red: 1, green: 144/255, blue: 32/255)
                            Color.white.frame(width: 12)
                            Color(red: 56/255, green: 87/255, blue: 128/255)
                        }
                        Menu {
                            menuOptions
                        } label: {
                            HStack {
                                Text("材质菜单")
                                Spacer()
                                Image(systemName: "ellipsis")
                            }.frame(width: 180, height: 32)
                        }
                        .buttonStyle(.glass)
                        .accessibilityIdentifier("material-menu")
                        .padding(.top, 12)
                    }.frame(height: 212).clipShape(.rect(cornerRadius: 18))
                }
            }
            if #available(iOS 26.0, *) {
                Section("系统玻璃") {
                    ZStack {
                        LinearGradient(colors: [.orange, .teal, .indigo], startPoint: .leading, endPoint: .trailing)
                        HStack {
                            Text("Regular").padding(12).glassEffect(.regular)
                            Text("Clear").padding(12).glassEffect(.clear)
                        }
                    }.frame(height: 75).clipShape(.rect(cornerRadius: 15))
                }
            }
        }
    }
    private var images: some View {
        List {
            Section("图像") {
                Label("系统符号", systemImage: "folder.fill")
                HStack { Text("异步图像"); Spacer()
                    AsyncImage(url: sampleImageURL) { phase in
                        switch phase {
                        case .success(let image): image.resizable().interpolation(.none)
                        case .failure: Image(systemName: "photo.badge.exclamationmark")
                        default: ProgressView()
                        }
                    }.frame(width: 48, height: 32).accessibilityLabel("色板")
                }
            }
            Section("形状") {
                shapeRow("矩形", Rectangle()); shapeRow("圆角矩形", RoundedRectangle(cornerRadius: 9))
                shapeRow("独立圆角", UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 2, bottomTrailingRadius: 14, topTrailingRadius: 2))
                shapeRow("圆", Circle()); shapeRow("椭圆", Ellipse()); shapeRow("胶囊", Capsule())
            }
        }
    }
    private func shapeRow<S: Shape>(_ title: String, _ shape: S) -> some View {
        HStack { Text(title); Spacer(); shape.fill(.tint).frame(width: 60, height: 32) }.padding(.vertical, 3)
    }
    private var containers: some View {
        List {
            Section("分组") {
                DisclosureGroup("文件夹", isExpanded: $expanded) { Label("路线手记", systemImage: "folder"); Label("用纸.csv", systemImage: "doc") }
                GroupBox("馆藏") { LabeledContent("项目", value: "2") }
                ControlGroup { Button("增加") { count += 1 }; Button("减少") { count = max(0,count-1) } }
                LabeledContent("数量", value: String(count))
            }
            NavigationLink("表格") {
                Table(SampleFile.rows) {
                    TableColumn("名称", value: \.name); TableColumn("类型", value: \.kind)
                }.navigationTitle("表格")
            }
            NavigationLink("滚动与分隔线") {
                ScrollView { LazyVStack(alignment: .leading) { ForEach(1...24, id: \.self) { n in Text("样张 " + String(n)).padding(.vertical, 8); Divider() } }.padding() }.navigationTitle("样张")
            }
        }
    }
    private var presentation: some View {
        Form {
            Section("菜单") {
                Menu("按钮菜单") { menuOptions }.buttonStyle(.bordered)
                Menu("无边框菜单") { menuOptions }.buttonStyle(.borderless)
                Label("长按项目", systemImage: "doc").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle()).contextMenu { menuOptions }
                Menu("长菜单") {
                    Menu("往期样张") { archiveOptions("往期", count: 72) }
                    archiveOptions("样张", count: 64)
                }
                if !archive.isEmpty { Text(archive) }
            }
            Section("呈现") {
                Button("提示框") { notice = "已读取样张。"; prompt = .alert }
                Button("双操作提示") { prompt = .deleteSample }
                Button("三操作提示") { prompt = .changes }
                Button("输入提示") { prompt = .rename }
                Button("操作列表") { prompt = .confirm }
                Button("半屏面板") { prompt = .sheet }
                Button("浮层") { prompt = .popover }
                    .popover(isPresented: showing(.popover)) { Text("馆藏索引").padding(24).presentationCompactAdaptation(.popover) }
                NavigationLink("标签页") { FoldTabSample() }
            }
        }
    }
    @ViewBuilder private var menuOptions: some View {
        ForEach(["列表", "网格", "分栏"], id: \.self) { key in Button { selected = key } label: { if key == selected { Label(key, systemImage: "checkmark") } else { Text(key) } } }
    }
    @ViewBuilder private func archiveOptions(_ prefix: String, count: Int) -> some View {
        ForEach(1...count, id: \.self) { number in
            let title = String(format: "%@ %02d", prefix, number)
            Button(title) { archive = title }
        }
    }
}

private struct FoldTabSample: View {
    @State private var selected: FoldDestination = .browse
    @State private var showBands = false

    private func title(_ destination: FoldDestination) -> String {
        switch destination {
        case .recent: "最近打开"
        case .pinned: "固定"
        case .browse: "本机文库"
        }
    }
    var body: some View {
        TabView(selection: $selected) {
            ForEach(FoldDestination.allCases) { destination in
                Tab(destination.title, systemImage: destination.symbol, value: destination) {
                    ZStack {
                        Color(uiColor: .systemBackground)
                        if showBands {
                            HStack(spacing: 0) {
                                Color(red: 35/255, green: 55/255, blue: 86/255)
                                Color(red: 99/255, green: 197/255, blue: 183/255).frame(width: 92)
                                Color(red: 220/255, green: 139/255, blue: 77/255)
                            }
                            .frame(maxHeight: 180)
                            .frame(maxHeight: .infinity, alignment: .bottom)
                            .accessibilityHidden(true)
                        }
                        VStack(spacing: 16) {
                            Text(title(destination)).accessibilityIdentifier("tabSample." + destination.rawValue)
                            Toggle("对照色带", isOn: $showBands).fixedSize()
                        }
                    }
                    .ignoresSafeArea()
                }
            }
        }
        .environment(\.horizontalSizeClass, .compact)
        .navigationTitle(title(selected))
    }
}

private struct FoldPressSample: View {
    @State private var mode = "圆钮"
    @State private var gap = 11.0
    @State private var stagger = false
    @State private var result = ""
    @State private var taps = 0

    private var icon: some View {
        Image(systemName: "ellipsis").font(.system(size: 19, weight: .semibold))
            .frame(width: 34, height: 34)
    }

    private var sample: some View {
        Group {
            if mode == "菜单" {
                Menu {
                    ForEach(1...5, id: \.self) { i in
                        Button("项目 \(i)") { result = "项目 \(i)" }
                    }
                } label: { icon }
                .accessibilityLabel("展开样张菜单").accessibilityIdentifier("probe.menu")
            } else {
                Button { taps += 1; result = "点击 \(taps)" } label: { icon }
                    .accessibilityLabel("样张圆钮").accessibilityIdentifier("probe.button")
            }
        }
    }

    private func capsule(_ title: String, width: CGFloat) -> some View {
        let button = Button {
            taps += 1
            result = "\(title) · \(taps)"
        } label: {
            Text(title).font(.body.weight(.medium)).frame(width: width, height: 44)
                .contentShape(Capsule(style: .continuous))
        }.buttonStyle(.plain).accessibilityIdentifier("probe." + title)
        return Group {
            if #available(iOS 26, *) {
                button.glassEffect(.regular.interactive(), in: Capsule(style: .continuous))
            } else {
                button.background(.regularMaterial, in: Capsule(style: .continuous))
            }
        }
    }

    @ViewBuilder private var pair: some View {
        let buttons = HStack(spacing: gap) {
            capsule("选择", width: 66)
            capsule("操作", width: 110).offset(y: stagger ? 8 : 0)
        }
        if #available(iOS 26, *) { GlassEffectContainer(spacing: 7) { buttons } }
        else { buttons }
    }

    var body: some View {
        VStack(spacing: 12) {
            Picker("组件", selection: $mode) {
                ForEach(["圆钮", "菜单", "短胶囊", "长胶囊", "相邻"], id: \.self) { Text($0) }
            }.pickerStyle(.segmented)
            if mode == "相邻" {
                HStack {
                    Text("间距 \(Int(gap.rounded()))").monospacedDigit()
                    Slider(value: $gap, in: 0...28).accessibilityLabel("间距")
                    Toggle("错开", isOn: $stagger).fixedSize()
                }.font(.footnote)
            }
            GeometryReader { geo in
                ZStack {
                    Color(red: 0.13, green: 0.26, blue: 0.38)
                    Group {
                        if mode == "相邻" {
                            pair
                        } else if mode == "短胶囊" || mode == "长胶囊" {
                            capsule(mode, width: mode == "短胶囊" ? 66 : 110)
                        } else if #available(iOS 26, *) {
                            sample.buttonStyle(.glass).buttonBorderShape(.circle)
                        } else {
                            sample.buttonStyle(.bordered).buttonBorderShape(.capsule)
                        }
                    }
                    .position(x: geo.size.width * (mode == "相邻" ? 0.5 : 0.6), y: geo.size.height * 0.34)
                }
            }
            HStack {
                FoldContactIndicator().frame(width: 12, height: 12)
                Text("触点")
                Spacer()
                Text(result)
            }.font(.footnote).frame(height: 20)
        }.padding(.horizontal)
    }
}

// Observe without recognizing a gesture. Menus may appear in a second UIKit window.
private struct FoldContactIndicator: UIViewRepresentable {
    private static let tracing = ProcessInfo.processInfo.arguments.contains("--record-press")
    private static var traceURL: URL {
        URL.cachesDirectory.appending(path: "FoldPressTrace.jsonl")
    }
    func makeUIView(context: Context) -> Indicator {
        if Self.tracing { try? Data().write(to: Self.traceURL) }
        return Indicator()
    }
    func updateUIView(_ uiView: Indicator, context: Context) {}

    final class Indicator: UIView {
        private var observers: [ContactObserver] = []
        private var contacts = 0
        override func didMoveToWindow() {
            super.didMoveToWindow()
            NotificationCenter.default.removeObserver(self)
            for observer in observers { observer.view?.removeGestureRecognizer(observer) }
            observers.removeAll()
            layer.cornerRadius = 6
            backgroundColor = .systemGray
            isAccessibilityElement = true
            accessibilityIdentifier = "probe.contact"
            accessibilityLabel = "触点次数"
            accessibilityValue = String(contacts)
            guard window != nil else { return }
            attach()
            NotificationCenter.default.addObserver(self, selector: #selector(attach),
                name: UIWindow.didBecomeVisibleNotification, object: nil)
        }
        @objc private func attach() {
            for candidate in window?.windowScene?.windows ?? [] {
                guard !observers.contains(where: { $0.view === candidate }) else { continue }
                let observer = ContactObserver(target: nil, action: nil)
                observer.cancelsTouchesInView = false
                observer.delaysTouchesBegan = false
                observer.delaysTouchesEnded = false
                observer.contact = { [weak self] held in
                    self?.backgroundColor = held ? UIColor(red: 1, green: 0, blue: 0.55, alpha: 1) : .systemGray
                    if let self, held { contacts += 1; accessibilityValue = String(contacts) }
                }
                candidate.addGestureRecognizer(observer)
                observers.append(observer)
            }
        }
    }
    private final class ContactObserver: UIGestureRecognizer {
        var contact: ((Bool) -> Void)?
        private var began: TimeInterval?
        private var origin = CGPoint.zero
        private var firstMove: TimeInterval?
        private var lastMove: TimeInterval?
        private var path: [[Double]] = []
        override func canPrevent(_ other: UIGestureRecognizer) -> Bool { false }
        override func canBePrevented(by other: UIGestureRecognizer) -> Bool { false }
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            guard let touch = touches.first else { return }
            began = touch.timestamp
            origin = touch.preciseLocation(in: view)
            firstMove = nil
            lastMove = nil
            path.removeAll(keepingCapacity: true)
            contact?(true)
        }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            guard let touch = touches.first, let began else { return }
            let time = touch.timestamp
            if firstMove == nil { firstMove = time }
            lastMove = time
            if FoldContactIndicator.tracing {
                let point = touch.preciseLocation(in: view)
                path.append([time - began, point.x - origin.x, point.y - origin.y])
            }
        }
        private func finish(_ touch: UITouch?) {
            defer { began = nil; contact?(false); state = .failed }
            guard FoldContactIndicator.tracing, let touch, let began,
                  let file = try? FileHandle(forWritingTo: FoldContactIndicator.traceURL) else { return }
            defer { try? file.close() }
            let end = touch.preciseLocation(in: view)
            let row: [String: Any] = [
                "x": origin.x, "y": origin.y, "dx": end.x - origin.x, "dy": end.y - origin.y,
                "firstMove": (firstMove ?? began) - began, "lastMove": (lastMove ?? began) - began,
                "duration": touch.timestamp - began, "endWallTime": Date().timeIntervalSince1970,
                "path": path
            ]
            if let data = try? JSONSerialization.data(withJSONObject: row) {
                _ = try? file.seekToEnd()
                try? file.write(contentsOf: data + Data([10]))
            }
        }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { finish(touches.first) }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { finish(touches.first) }
    }
}

private struct SampleFile: Identifiable {
    var id: String { name }
    let name: String, kind: String
    static let rows = [Self(name: "路线手记", kind: "文件夹"), Self(name: "用纸.csv", kind: "文档")]
}
private let sampleImageURL = URL(string: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAHUlEQVR4nGPQ6Jr4/1BhaFQNQ9/RE/+vzW6dugsAZl8LAEx4NqkAAAAASUVORK5CYII=")

/// The navigation stack owns the bar and the transition between these two routes.
private struct FoldToolbarTransitions: View {
    @State private var example = "圆钮换页"
    @State private var showingDestination = false

    var body: some View {
        FoldToolbarStage(example: $example, destination: false) { showingDestination = true }
            .navigationDestination(isPresented: $showingDestination) {
                FoldToolbarStage(example: $example, destination: true) { showingDestination = false }
            }
    }
}

private struct FoldToolbarStage: View {
    @Binding var example: String
    let destination: Bool
    let toggle: () -> Void
    private var symbols: [String] {
        switch example {
        case "双钮到头像": return destination ? ["avatar"] : ["square.and.arrow.up", "ellipsis"]
        case "双钮到三钮": return destination ? ["bell", "hand.thumbsup", "arrow.down.circle"] : ["magnifyingglass", "arrow.clockwise"]
        default: return ["ellipsis"]
        }
    }
    var body: some View {
        VStack(spacing: 24) {
            if !destination {
                Picker("过渡样例", selection: $example) {
                    ForEach(["圆钮换页", "双钮到头像", "双钮到三钮"], id: \.self) { Text($0) }
                }.pickerStyle(.segmented)
            }
            Button(destination ? "返回起始状态" : "切换工具栏", action: toggle)
            Spacer()
        }
        .padding()
        .navigationTitle("工具栏过渡").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                ForEach(symbols, id: \.self) { symbol in
                    Button(action: toggle) {
                        if symbol == "avatar" {
                            Canvas { context, size in
                                let u = size.width
                                context.fill(Path(ellipseIn: CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.42, green: 0.58, blue: 0.62)))
                                for circle in [CGRect(x: 0.35*u, y: 0.21*u, width: 0.30*u, height: 0.30*u),
                                               CGRect(x: 0.17*u, y: 0.61*u, width: 0.66*u, height: 0.66*u)] {
                                    context.fill(Path(ellipseIn: circle), with: .color(Color(red: 0.88, green: 0.91, blue: 0.82)))
                                }
                            }
                            .frame(width: 38, height: 38)
                            .clipShape(Circle())
                            .frame(width: 22, height: 22)
                        } else {
                            Image(systemName: symbol)
                        }
                    }.accessibilityLabel("切换样例 " + String((symbols.firstIndex(of: symbol) ?? 0) + 1))
                }
            }
        }
    }
}
