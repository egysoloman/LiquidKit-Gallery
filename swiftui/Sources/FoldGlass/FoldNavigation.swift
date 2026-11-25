import SwiftUI

public enum FoldDestination: String, CaseIterable, Identifiable {
    case recent, pinned, browse
    public var id: Self { self }
    public var title: String {
        switch self { case .recent: "最近"; case .pinned: "固定"; case .browse: "浏览" }
    }
    public var symbol: String {
        switch self { case .recent: "clock"; case .pinned: "bookmark"; case .browse: "folder" }
    }
}

/// A native tab stack on compact screens and a sidebar/detail split on regular screens.
/// Callers own their paths so changing width or switching tabs preserves the selected document.
public struct FoldNavigation<Sidebar: View, Detail: View>: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Binding private var selection: FoldDestination
    private let sidebar: () -> Sidebar
    private let detail: (FoldDestination) -> Detail
    public init(selection: Binding<FoldDestination>, @ViewBuilder sidebar: @escaping () -> Sidebar,
                @ViewBuilder detail: @escaping (FoldDestination) -> Detail) {
        _selection = selection; self.sidebar = sidebar; self.detail = detail
    }
    public var body: some View {
        if sizeClass == .regular {
            NavigationSplitView { sidebar() } detail: { detail(selection) }
        } else {
            TabView(selection: $selection) {
                ForEach(FoldDestination.allCases) { destination in
                    Tab(destination.title, systemImage: destination.symbol, value: destination) { detail(destination) }
                }
            }
        }
    }
}
