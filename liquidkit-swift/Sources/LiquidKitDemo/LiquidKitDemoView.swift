import SwiftUI
import LiquidKit

/// Single-source SwiftUI showcase demonstrating zero `#if SKIP` app code:
/// runs with native Liquid Glass on iOS 26/27 and maps to Android `io.liquidkit` via Skip.
public struct LiquidKitDemoView: View {
    @State private var opacity: Double = 0.5
    @State private var selectedTab: String = "browse"
    @State private var tabBarMinimized: Bool = false
    @State private var viewMode: String = "列表"
    @State private var tabFilter: String = "all"
    @State private var paletteId: String = "teal"
    @State private var inlineChoice: String = "按名称"
    @State private var showPreview: Bool = true
    @State private var progress: Double = 0.62
    @State private var steppedProgress: Double = 0.5
    @State private var count: Int = 3
    @State private var query: String = ""
    @State private var date = LiquidDateValue(year: 2026, month: 11, day: 24)
    @State private var accent = LiquidRGBA.foldTeal
    @State private var expanded = true
    @State private var showSheet = false

    private let tabs = [
        LiquidTabItem(id: "recent", title: "最近", systemImage: "clock.fill"),
        LiquidTabItem(id: "pinned", title: "固定", systemImage: "bookmark.fill"),
        LiquidTabItem(id: "browse", title: "浏览", systemImage: "folder.fill"),
    ]

    private let paletteOptions = [
        LiquidPaletteOptionItem(id: "teal", label: "青绿", color: .foldTeal),
        LiquidPaletteOptionItem(id: "blue", label: "蓝色", color: .accentBlue),
        LiquidPaletteOptionItem(id: "red", label: "珊瑚红", color: .destructiveRed),
    ]

    private let tabPickerOptions = [
        LiquidTabOptionItem(id: "all", label: "全部", systemImage: "square.grid.2x2"),
        LiquidTabOptionItem(id: "docs", label: "文档", systemImage: "doc.text"),
        LiquidTabOptionItem(id: "media", label: "媒体", systemImage: "photo"),
    ]

    private let menuEntries = [
        LiquidMenuEntry(id: "list", title: "列表", systemImage: "list.bullet", checked: true),
        LiquidMenuEntry(id: "grid", title: "网格", systemImage: "square.grid.2x2"),
        LiquidMenuEntry(id: "share", title: "分享", systemImage: "square.and.arrow.up"),
    ]

    public init() {}

    public var body: some View {
        LiquidKitRoot(opacity: opacity) {
            ZStack(alignment: .bottom) {
                ScrollView {
                    VStack(spacing: 16) {
                        LiquidSearchBar(query: $query, placeholder: "搜索组件", tint: accent)

                        LiquidGlassContainer(spacing: 8) {
                            HStack(spacing: 10) {
                                LiquidButton("玻璃按钮", systemImage: "sparkles", style: .glass, tint: accent) {
                                    count += 1
                                }
                                LiquidButton("强调玻璃", style: .glassProminent, tint: accent) {
                                    showSheet = true
                                }
                            }
                        }

                        LiquidGroupCard("交互控件") {
                            VStack(spacing: 12) {
                                LiquidSwitch("显示预览", isOn: $showPreview)
                                LiquidSwitch("最小化底栏", isOn: $tabBarMinimized)
                                LiquidSliderControl(value: $progress)
                                LiquidSliderControl(value: $steppedProgress, ticks: [0.0, 0.25, 0.5, 0.75, 1.0])
                                LiquidOpacityControl(opacity: $opacity)
                                LiquidStepperControl("数量", value: $count, in: 0...30, tint: accent)
                                LiquidSegmentedControl(options: ["列表", "网格", "分栏"], selection: $viewMode)
                                LiquidTabsPickerControl(options: tabPickerOptions, selection: $tabFilter, tint: accent)
                                LiquidPalettePickerControl(options: paletteOptions, selection: $paletteId, tint: accent)
                                LiquidInlinePickerControl("排序方式", options: ["按名称", "按修改时间", "按大小"], selection: $inlineChoice, tint: accent)
                                LiquidMenuPickerControl("排列方式", options: ["名称", "修改时间", "大小"], selection: $viewMode, tint: accent)
                                LiquidDatePickerControl("日期", date: $date, tint: accent)
                                LiquidColorPickerControl("主题色", color: $accent)
                            }
                        }

                        LiquidSafeAreaBarView(edge: .bottom) {
                            Text("iOS 27 SafeAreaBar")
                                .liquidGlass(variant: .regular, tint: accent, interactive: true)
                            Text("Identity")
                                .liquidGlass(variant: .identity)
                        }

                        LiquidGroupCard("状态与容器") {
                            VStack(spacing: 10) {
                                LiquidProgress(value: progress, tint: accent)
                                LiquidGaugeControl(value: progress, label: "占用", style: "linear", tint: accent)
                                LiquidDisclosure("更多选项", isExpanded: $expanded) {
                                    LiquidKeyValueRow("当前模式", value: viewMode)
                                    LiquidMenuControl("更多操作", items: menuEntries, tint: accent) { _ in }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 12)
                    .padding(.bottom, 96)
                }

                LiquidFloatingTabBar(
                    tabs: tabs,
                    selection: $selectedTab,
                    tint: accent,
                    minimized: tabBarMinimized,
                    onExpand: { tabBarMinimized = false },
                    searchAction: { query = "glass" }
                )
                .padding(.horizontal, 18)
                .padding(.bottom, 12)

                LiquidModalSheet(isPresented: $showSheet, title: "LiquidKit 详情", tint: accent) {
                    LiquidKeyValueRow("框架", value: "LiquidKit 1.0")
                    LiquidKeyValueRow("透明度", value: "\(Int(opacity * 100))%")
                    LiquidEmptyView("全部就绪", systemImage: "checkmark.seal", description: "同一份 SwiftUI 代码运行在 iOS 与 Android。")
                }
            }
        }
    }
}
