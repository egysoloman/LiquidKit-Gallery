import SwiftUI
import FoldGlass

@main
struct FoldReferenceApp: App {
    var body: some Scene { WindowGroup { LibraryView() } }
}

private let originals = [
    "路线手记": ["河岸步行.md", "车站路线.txt"],
    "印刷样张": ["用纸.csv"],
    "影像目录": ["拍摄顺序.txt"]
]
private let documents = ["馆藏索引.tsv", "河岸采集.md", "取景清单.csv", "色彩索引.json", "展签草稿.txt"]

struct LibraryView: View {
    @State private var tab: FoldDestination = .browse
    @State private var paths: [FoldDestination: [String]] = [:]
    @State private var pinned = ["路线手记", "印刷样张"]
    @State private var recent: [String] = []
    @State private var folders = originals.merging([
        "材质校对": (1...28).map { String(format: "样张 %02d.txt", $0) }
    ]) { original, _ in original }
    @AppStorage("opacity") private var opacity = 0.5
    @State private var settings = false

    func path(for destination: FoldDestination) -> Binding<[String]> {
        Binding { paths[destination] ?? [] } set: { value in
            paths[destination] = value
            if let last = value.last, !last.isEmpty, folders[last] == nil {
                recent.removeAll { $0 == last }; recent.insert(last, at: 0)
            }
        }
    }

    var body: some View {
        FoldNavigation(selection: $tab) {
            List {
                Section("文库") {
                    ForEach(FoldDestination.allCases) { entry in
                        Button { tab = entry } label: { Label(entry.title, systemImage: entry.symbol) }
                    }
                }
                Section("固定") {
                    ForEach(pinned, id: \.self) { folder in
                        Button { tab = .browse; paths[.browse] = [folder] } label: { Label(folder, systemImage: "bookmark") }
                    }
                }
            }.navigationTitle("折页")
        } detail: { destination in
            NavigationStack(path: path(for: destination)) {
                page(nil, destination: destination)
                    .navigationDestination(for: String.self) { name in page(name, destination: destination) }
            }
        }
        .tint(Color(red: 0.19, green: 0.64, blue: 0.65))
        .sheet(isPresented: $settings) { ComponentSheet(opacity: $opacity) }

    }

    @ViewBuilder func page(_ name: String?, destination: FoldDestination) -> some View {
        let title = name ?? (destination == .browse ? "本机文库" : destination == .recent ? "最近打开" : "固定")
        let rows = name.flatMap { folders[$0] } ?? (destination == .browse
            ? ["路线手记", "印刷样张", "影像目录"] + folders.keys.filter { originals[$0] == nil }.sorted() + documents
            : destination == .recent ? recent : pinned)
        if let name, folders[name] == nil {
            List { FoldFileCell(name, subtitle: "文档", folder: false) }
                .navigationTitle(name).navigationBarTitleDisplayMode(.inline)
        } else {
            BrowserPage(title: title, rows: rows, folders: folders, opacity: opacity, open: {
                paths[destination, default: []].append($0)
                if folders[$0] == nil { let name = $0; recent.removeAll { $0 == name }; recent.insert(name, at: 0) }
            }, pin: { label in
                if pinned.contains(label) { pinned.removeAll { $0 == label } } else { pinned.append(label) }
            }, create: { folder in
                guard folders[folder] == nil else { return }
                folders[folder] = []
                if let name { folders[name, default: []].append(folder) }
            }, settings: { settings = true })
        }
    }
}

private struct BrowserPage: View {
    let title: String
    let rows: [String]
    let folders: [String: [String]]
    let opacity: Double
    let open: (String) -> Void
    let pin: (String) -> Void
    let create: (String) -> Void
    let settings: () -> Void
    @State private var search = ""
    @State private var selecting = false
    @State private var selected = Set<String>()
    @State private var grid = false
    @State private var sort = "名称"
    @State private var creating = false
    @State private var newName = ""

    var visible: [String] {
        let matching = rows.filter { search.isEmpty || $0.localizedCaseInsensitiveContains(search) }
        if sort == "名称" { return matching }
        return matching.sorted { sort == "大小" ? ($0.utf8.count, $0) < ($1.utf8.count, $1) : $0 > $1 }
    }
    private var order: some View {
        ForEach(["名称", "修改时间", "大小"], id: \.self) { option in
            Button { sort = option } label: {
                if sort == option { Label(option, systemImage: "checkmark") } else { Text(option) }
            }
        }
    }
    @ViewBuilder func actions(_ row: String) -> some View {
        Button { open(row) } label: { Label("打开", systemImage: "arrow.right") }
        if folders[row] != nil {
            Button { pin(row) } label: { Label("切换固定", systemImage: "bookmark") }
        }
    }

    var body: some View {
        List {
            HStack {
                FoldMenu { order } label: { Label(sort, systemImage: "chevron.down").labelStyle(.titleAndIcon) }
                    .buttonStyle(.borderless).accessibilityIdentifier("sort")
                Spacer()
                Text("\(visible.count) 项").font(.caption).foregroundStyle(.secondary)
            }.listRowSeparator(.hidden)
            if visible.isEmpty { FoldEmptyState(search.isEmpty ? "空文件夹" : "没有匹配的文件").listRowSeparator(.hidden) }
            else if grid {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))]) {
                    ForEach(visible, id: \.self) { name in
                        Button { open(name) } label: { VStack { Image(systemName: folders[name] == nil ? "doc.text" : "folder.fill"); Text(name) }.frame(minHeight: 76) }
                            .contextMenu { actions(name) }
                    }
                }
            } else {
                ForEach(visible, id: \.self) { name in
                    Button {
                        if selecting { if !selected.insert(name).inserted { selected.remove(name) } }
                        else { open(name) }
                    } label: {
                        HStack {
                            FoldFileCell(name, subtitle: folders[name].map { "\($0.count) 项" } ?? "文档", folder: folders[name] != nil)
                            if selecting { Image(systemName: selected.contains(name) ? "checkmark.circle.fill" : "circle") }
                        }
                    }
                    .buttonStyle(.plain).accessibilityIdentifier("row." + name)
                    .contextMenu { actions(name) }.listRowInsets(.init(top: 0, leading: 18, bottom: 0, trailing: 18))
                }
            }
        }
        .listStyle(.plain)
        .safeAreaInset(edge: .top, spacing: 0) {
            FoldSearchField("搜索", text: $search, opacity: opacity)
                .padding(.horizontal, FoldMetric.gutter).padding(.vertical, 3)
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                FoldToolbarButton(selecting ? "完成" : "选择") { selecting.toggle(); selected.removeAll() }
                    .accessibilityIdentifier("select")
            }
            if #available(iOS 26.0, *) { ToolbarSpacer(.fixed, placement: .topBarTrailing) }
            ToolbarItemGroup(placement: .topBarTrailing) {
                FoldToolbarButton("新建文件夹", symbol: "folder.badge.plus") { newName = ""; creating = true }
                FoldMenu {
                    Menu("显示方式") {
                        Button("列表") { grid = false }
                        Button("网格") { grid = true }
                    }
                    Menu("排序方式") { order }
                    Divider()
                    Button("组件目录", action: settings)
                } label: { Image(systemName: "list.bullet") }
                .accessibilityLabel("显示选项").accessibilityIdentifier("options")
            }
        }
        .alert("新建文件夹", isPresented: $creating) {
            TextField("名称", text: $newName)
            Button("取消", role: .cancel) {}
            Button("创建") { if !newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { create(newName) } }
        }
    }
}

private struct ComponentSheet: View {
    @Binding var opacity: Double
    @Environment(\.dismiss) var dismiss
    @State private var progress = 0.5
    @State private var enabled = true
    @State private var showInfo = false
    @State private var choice = "名称"
    var body: some View {
        NavigationStack {
            List {
                Section { NavigationLink { FoldComponentGallery() } label: { Label("全部组件", systemImage: "square.grid.2x2") } }
                Section("玻璃材质") {
                    ZStack {
                        LinearGradient(colors: [.teal, .indigo], startPoint: .leading, endPoint: .trailing)
                        Text("折页文库").font(.largeTitle.bold())
                        Text("搜索").frame(width: 160, height: FoldMetric.control).foldGlass(in: Capsule(), opacity: opacity)
                    }.frame(height: 92).clipShape(.rect(cornerRadius: 16))
                    FoldOpacityControl(value: $opacity)
                }
                Section("菜单") {
                    HStack {
                        Label("玻璃按钮", systemImage: "capsule")
                        Spacer()
                        if #available(iOS 26.0, *) {
                            menuButton.buttonStyle(.glass).controlSize(.large)
                        } else { menuButton.buttonStyle(.bordered) }
                    }
                    HStack {
                        Text("无边框")
                        Spacer()
                        FoldMenu { choices } label: { Text(choice); Image(systemName: "chevron.down") }
                            .buttonStyle(.borderless).accessibilityLabel("无边框菜单")
                    }
                    Label("长按预览", systemImage: "square.grid.2x2")
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle()).contextMenu { choices }
                }
                Section("控件") {
                    FoldSwitch("显示预览", isOn: $enabled)
                    FoldSlider("调整进度", value: $progress).accessibilityIdentifier("progress-slider")
                    if enabled {
                        HStack { FoldProgress(progress); Text(progress, format: .percent.precision(.fractionLength(0))) }
                    }
                    Button("查看信息") { showInfo = true }
                    FoldMenu { Button("查看信息") { showInfo = true } } label: { Label("更多操作", systemImage: "ellipsis") }
                }
            }.navigationTitle("组件目录")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
                    ToolbarItem(placement: .bottomBar) {
                        FoldMenu { choices } label: { Label("底部菜单", systemImage: "ellipsis") }
                    }
                }
                .alert("折页组件", isPresented: $showInfo) { Button("完成") {} }
        }
    }

    private var menuButton: some View {
        FoldMenu { choices } label: { Image(systemName: "ellipsis").frame(width: 25, height: 22) }
            .accessibilityLabel("玻璃菜单")
    }
    private var choices: some View {
        ForEach(["名称", "日期", "大小"], id: \.self) { key in
            Button { choice = key } label: {
                if key == choice { Label(key, systemImage: "checkmark") } else { Text(key) }
            }
        }
    }
}
