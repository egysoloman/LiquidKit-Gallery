import SwiftUI

#if SKIP
// SKIP INSERT: import io.liquidkit.skip.*
#endif

/// Tab entry shared between native SwiftUI `TabView` and Android `LiquidKitTabBar`.
public struct LiquidTabItem: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let systemImage: String

    public init(id: String, title: String, systemImage: String) {
        self.id = id
        self.title = title
        self.systemImage = systemImage
    }
}

/// Floating Liquid Glass tab bar bridged to `LiquidKitTabBar` on Android,
/// supporting iOS 27 detached search orb (`searchAction`) and scroll-minimized compact mode (`minimized`).
public struct LiquidFloatingTabBar: View {
    public let tabs: [LiquidTabItem]
    @Binding private var selectedId: String
    public let tint: LiquidRGBA
    public let minimized: Bool
    private let onExpand: (() -> Void)?
    private let searchAction: (() -> Void)?
    public let searchLabel: String

    public init(
        tabs: [LiquidTabItem],
        selection: Binding<String>,
        tint: LiquidRGBA = .accentBlue,
        minimized: Bool = false,
        onExpand: (() -> Void)? = nil,
        searchAction: (() -> Void)? = nil,
        searchLabel: String = "搜索"
    ) {
        self.tabs = tabs
        _selectedId = selection
        self.tint = tint
        self.minimized = minimized
        self.onExpand = onExpand
        self.searchAction = searchAction
        self.searchLabel = searchLabel
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            let specs = tabs.map { SkipLiquidKitTabSpec(id: $0.id, title: $0.title, systemImage: $0.systemImage) }
            SkipLiquidKitTabBar(
                tabs: specs,
                selectedId: selectedId,
                tint: tint.composeColor,
                minimized: minimized,
                onExpandFromMinimized: onExpand,
                searchAction: searchAction,
                searchLabel: searchLabel,
                modifier: context.modifier
            ) { next in
                selectedId = next
            }
        }
        #else
        let activeTab = tabs.first { $0.id == selectedId } ?? tabs.first
        HStack(spacing: 10) {
            if minimized, let activeTab {
                Button {
                    onExpand?()
                } label: {
                    Image(systemName: activeTab.systemImage)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(tint.swiftUIColor)
                        .frame(width: 62, height: 62)
                }
                .buttonStyle(.plain)
                .liquidGlass(variant: .regular, interactive: true)
            } else {
                HStack(spacing: 4) {
                    ForEach(tabs) { tab in
                        let active = tab.id == selectedId
                        Button {
                            withAnimation(LiquidSpringToken.snappy.animation) {
                                selectedId = tab.id
                            }
                        } label: {
                            VStack(spacing: 3) {
                                Image(systemName: tab.systemImage)
                                    .font(.system(size: 20, weight: .semibold))
                                Text(tab.title)
                                    .font(.system(size: 10, weight: .semibold))
                            }
                            .foregroundStyle(active ? tint.swiftUIColor : .primary)
                            .frame(minWidth: 74, minHeight: 50)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .liquidGlass(variant: .regular)
            }

            if minimized && searchAction != nil {
                Spacer(minLength: 0)
            }

            if let searchAction {
                Button(action: searchAction) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 62, height: 62)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(searchLabel)
                .liquidGlass(variant: .regular, interactive: true)
            }
        }
        #endif
    }
}

/// Role mapping for alerts and confirmation dialogs.
public enum LiquidDialogRole: String, Sendable {
    case `default`
    case primary
    case cancel
    case destructive
}

/// Action descriptor shared between `.alert`/`.confirmationDialog` and Android `LiquidKitAlert`/`LiquidKitActionSheet`.
public struct LiquidDialogAction: Identifiable {
    public let id: String
    public let label: String
    public let role: LiquidDialogRole
    public let enabled: Bool
    public let handler: () -> Void

    public init(
        id: String? = nil,
        label: String,
        role: LiquidDialogRole = .default,
        enabled: Bool = true,
        handler: @escaping () -> Void
    ) {
        self.id = id ?? label
        self.label = label
        self.role = role
        self.enabled = enabled
        self.handler = handler
    }
}

/// View transition style matching iOS 27 `CrossFade`, `Zoom`, and `Slide`.
public enum LiquidTransitionKind: String, CaseIterable, Sendable {
    case crossFade
    case zoom
    case slide
}

/// Lightweight cross-platform view switcher avoiding full-screen blur FBO allocations.
public struct LiquidViewSwitcher<Content: View>: View {
    public let activeKey: String
    public let style: LiquidTransitionKind
    private let content: (String) -> Content

    public init(
        key: String,
        style: LiquidTransitionKind = .crossFade,
        @ViewBuilder content: @escaping (String) -> Content
    ) {
        self.activeKey = key
        self.style = style
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitViewTransition(
                key: activeKey,
                style: style.rawValue,
                modifier: context.modifier
            ) { displayed in
                content(displayed).Compose(context: context.content())
            }
        }
        #else
        content(activeKey)
            .id(activeKey)
            .transition(style == .zoom ? .scale(scale: 0.94).combined(with: .opacity) : .opacity)
            .animation(LiquidSpringToken.smooth.animation, value: activeKey)
        #endif
    }
}

/// Cross-platform modal sheet (`LiquidKitSheet` on Android with predictive back, `.sheet` on iOS).
public struct LiquidModalSheet<Content: View>: View {
    @Binding private var isPresented: Bool
    public let title: String?
    public let doneLabel: String?
    public let mediumDetent: Bool
    public let largeDetent: Bool
    public let tint: LiquidRGBA
    private let content: () -> Content

    public init(
        isPresented: Binding<Bool>,
        title: String? = nil,
        doneLabel: String? = "完成",
        mediumDetent: Bool = true,
        largeDetent: Bool = true,
        tint: LiquidRGBA = .accentBlue,
        @ViewBuilder content: @escaping () -> Content
    ) {
        _isPresented = isPresented
        self.title = title
        self.doneLabel = doneLabel
        self.mediumDetent = mediumDetent
        self.largeDetent = largeDetent
        self.tint = tint
        self.content = content
    }

    public var body: some View {
        #if SKIP
        if isPresented {
            ComposeView { context in
                SkipLiquidKitSheet(
                    title: title,
                    doneLabel: doneLabel,
                    mediumDetent: mediumDetent,
                    largeDetent: largeDetent,
                    tint: tint.composeColor,
                    onDismiss: { isPresented = false }
                ) {
                    content().Compose(context: context.content())
                }
            }
        }
        #else
        Color.clear
            .frame(width: 0, height: 0)
            .sheet(isPresented: $isPresented) {
                NavigationStack {
                    ScrollView {
                        VStack(spacing: 12) {
                            content()
                        }
                        .padding()
                    }
                    .navigationTitle(title ?? "")
                    .toolbar {
                        if let doneLabel {
                            ToolbarItem(placement: .confirmationAction) {
                                Button(doneLabel) { isPresented = false }
                            }
                        }
                    }
                }
                .presentationDetents(detentSet)
            }
        #endif
    }

    #if !SKIP
    private var detentSet: Set<PresentationDetent> {
        var set = Set<PresentationDetent>()
        if mediumDetent { set.insert(.medium) }
        if largeDetent || set.isEmpty { set.insert(.large) }
        return set
    }
    #endif
}

/// Cross-platform modal alert (`LiquidKitAlert` on Android, `.alert` on Apple).
public struct LiquidAlertModal: View {
    @Binding private var isPresented: Bool
    public let title: String
    public let message: String?
    public let actions: [LiquidDialogAction]
    public let tint: LiquidRGBA

    public init(
        isPresented: Binding<Bool>,
        title: String,
        message: String? = nil,
        actions: [LiquidDialogAction],
        tint: LiquidRGBA = .accentBlue
    ) {
        _isPresented = isPresented
        self.title = title
        self.message = message
        self.actions = actions
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        if isPresented {
            ComposeView { _ in
                SkipLiquidKitAlert(
                    title: title,
                    message: message,
                    actionLabels: actions.map { $0.label },
                    actionRoles: actions.map { $0.role.rawValue },
                    actionEnabled: actions.map { $0.enabled },
                    onAction: { idx in
                        if idx >= 0 && idx < actions.count {
                            actions[idx].handler()
                        }
                        isPresented = false
                    },
                    onDismiss: { isPresented = false },
                    tint: tint.composeColor
                )
            }
        }
        #else
        Color.clear
            .frame(width: 0, height: 0)
            .alert(title, isPresented: $isPresented) {
                ForEach(actions) { action in
                    Button(role: action.role == .destructive ? .destructive : (action.role == .cancel ? .cancel : nil)) {
                        action.handler()
                    } label: {
                        Text(action.label)
                    }
                    .disabled(!action.enabled)
                }
            } message: {
                if let message {
                    Text(message)
                }
            }
        #endif
    }
}

/// Cross-platform confirmation action sheet (`LiquidKitActionSheet` on Android, `.confirmationDialog` on Apple).
public struct LiquidActionSheetModal: View {
    @Binding private var isPresented: Bool
    public let title: String?
    public let message: String?
    public let cancelLabel: String
    public let actions: [LiquidDialogAction]
    public let tint: LiquidRGBA

    public init(
        isPresented: Binding<Bool>,
        title: String? = nil,
        message: String? = nil,
        cancelLabel: String = "取消",
        actions: [LiquidDialogAction],
        tint: LiquidRGBA = .accentBlue
    ) {
        _isPresented = isPresented
        self.title = title
        self.message = message
        self.cancelLabel = cancelLabel
        self.actions = actions
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        if isPresented {
            ComposeView { _ in
                SkipLiquidKitActionSheet(
                    title: title,
                    message: message,
                    cancelLabel: cancelLabel,
                    actionLabels: actions.map { $0.label },
                    actionRoles: actions.map { $0.role.rawValue },
                    actionEnabled: actions.map { $0.enabled },
                    onAction: { idx in
                        if idx >= 0 && idx < actions.count {
                            actions[idx].handler()
                        }
                        isPresented = false
                    },
                    onDismiss: { isPresented = false },
                    tint: tint.composeColor
                )
            }
        }
        #else
        Color.clear
            .frame(width: 0, height: 0)
            .confirmationDialog(title ?? "", isPresented: $isPresented, titleVisibility: title == nil ? .hidden : .visible) {
                ForEach(actions) { action in
                    Button(role: action.role == .destructive ? .destructive : (action.role == .cancel ? .cancel : nil)) {
                        action.handler()
                    } label: {
                        Text(action.label)
                    }
                    .disabled(!action.enabled)
                }
                Button(cancelLabel, role: .cancel) {}
            } message: {
                if let message {
                    Text(message)
                }
            }
        #endif
    }
}

/// Cross-platform anchored popover (`LiquidKitPopover` on Android, `.popover` on Apple).
public struct LiquidPopoverView<Content: View>: View {
    @Binding private var isPresented: Bool
    public let maxWidth: Double
    private let content: () -> Content

    public init(
        isPresented: Binding<Bool>,
        maxWidth: Double = 360,
        @ViewBuilder content: @escaping () -> Content
    ) {
        _isPresented = isPresented
        self.maxWidth = maxWidth
        self.content = content
    }

    public var body: some View {
        #if SKIP
        if isPresented {
            ComposeView { context in
                SkipLiquidKitPopover(
                    onDismiss: { isPresented = false },
                    maxWidth: maxWidth,
                    modifier: context.modifier
                ) {
                    content().Compose(context: context.content())
                }
            }
        }
        #else
        Color.clear
            .frame(width: 0, height: 0)
            .popover(isPresented: $isPresented) {
                VStack(alignment: .leading, spacing: 10) {
                    content()
                }
                .padding()
                .frame(maxWidth: maxWidth)
            }
        #endif
    }
}
