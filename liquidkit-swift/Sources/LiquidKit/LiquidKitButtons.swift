import SwiftUI

#if SKIP
// SKIP INSERT: import androidx.compose.ui.graphics.Color as ComposeColor
// SKIP INSERT: import io.liquidkit.skip.*
#endif

/// Button visual styles matching SwiftUI `.buttonStyle(...)` and Android `LiquidKitActionStyle`.
public enum LiquidButtonStyleKind: String, CaseIterable, Sendable {
    case plain
    case borderless
    case bordered
    case borderedProminent
    case glass
    case glassProminent
}

/// Control sizing matching SwiftUI `ControlSize` and Android `LiquidKitControlSize`.
public enum LiquidControlSizeKind: String, CaseIterable, Sendable {
    case mini
    case small
    case regular
    case large
    case extraLarge
}

/// Button border shape matching SwiftUI `ButtonBorderShape` and Android `LiquidKitButtonShape`.
public enum LiquidButtonBorderShapeKind: String, CaseIterable, Sendable {
    case capsule
    case circle
    case roundedRectangle
}

/// Cross-platform Liquid Glass button.
public struct LiquidButton: View {
    public let title: String
    public let systemImage: String?
    public let style: LiquidButtonStyleKind
    public let tint: LiquidRGBA?
    public let enabled: Bool
    public let controlSize: LiquidControlSizeKind
    public let shape: LiquidButtonBorderShapeKind
    public let destructive: Bool
    private let action: () -> Void

    public init(
        _ title: String,
        systemImage: String? = nil,
        style: LiquidButtonStyleKind = .glass,
        tint: LiquidRGBA? = nil,
        enabled: Bool = true,
        controlSize: LiquidControlSizeKind = .regular,
        shape: LiquidButtonBorderShapeKind = .capsule,
        destructive: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.style = style
        self.tint = tint
        self.enabled = enabled
        self.controlSize = controlSize
        self.shape = shape
        self.destructive = destructive
        self.action = action
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitButton(
                label: title,
                style: style.rawValue,
                systemImage: systemImage,
                enabled: enabled,
                tint: tint?.composeColor ?? ComposeColor.Unspecified,
                controlSize: controlSize.rawValue,
                shape: shape.rawValue,
                destructive: destructive,
                modifier: context.modifier,
                onClick: action
            )
        }
        #else
        styledButton
            .disabled(!enabled)
            .tint(destructive ? Color.red : tint?.swiftUIColor)
        #endif
    }

    #if !SKIP
    @ViewBuilder
    private var labelView: some View {
        if let systemImage {
            Label(title, systemImage: systemImage)
        } else {
            Text(title)
        }
    }

    @ViewBuilder
    private var styledButton: some View {
        switch style {
        case .plain:
            Button(action: action) { labelView }.buttonStyle(.plain)
        case .borderless:
            Button(action: action) { labelView }.buttonStyle(.borderless)
        case .bordered:
            Button(action: action) { labelView }.buttonStyle(.bordered)
        case .borderedProminent:
            Button(action: action) { labelView }.buttonStyle(.borderedProminent)
        case .glass:
            if #available(iOS 26.0, macOS 26.0, *) {
                Button(action: action) { labelView }.buttonStyle(.glass)
            } else {
                Button(action: action) { labelView }.buttonStyle(.bordered)
            }
        case .glassProminent:
            if #available(iOS 26.0, macOS 26.0, *) {
                Button(action: action) { labelView }.buttonStyle(.glassProminent)
            } else {
                Button(action: action) { labelView }.buttonStyle(.borderedProminent)
            }
        }
    }
    #endif
}

/// Menu item descriptor shared across iOS `Menu` and Android `LiquidAnchoredMenuHost`.
public struct LiquidMenuEntry: Identifiable, Equatable, Sendable {
    public let id: String
    public let title: String
    public let systemImage: String?
    public let checked: Bool
    public let destructive: Bool
    public let enabled: Bool

    public init(
        id: String,
        title: String,
        systemImage: String? = nil,
        checked: Bool = false,
        destructive: Bool = false,
        enabled: Bool = true
    ) {
        self.id = id
        self.title = title
        self.systemImage = systemImage
        self.checked = checked
        self.destructive = destructive
        self.enabled = enabled
    }
}

/// Anchored morphing Liquid Glass menu.
public struct LiquidMenuControl: View {
    public let title: String
    public let items: [LiquidMenuEntry]
    public let borderless: Bool
    public let tint: LiquidRGBA
    private let onSelect: (String) -> Void

    public init(
        _ title: String,
        items: [LiquidMenuEntry],
        borderless: Bool = false,
        tint: LiquidRGBA = .accentBlue,
        onSelect: @escaping (String) -> Void
    ) {
        self.title = title
        self.items = items
        self.borderless = borderless
        self.tint = tint
        self.onSelect = onSelect
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            let specs = items.map { item in
                SkipLiquidKitMenuItemSpec(
                    id: item.id,
                    title: item.title,
                    systemImage: item.systemImage,
                    checked: item.checked,
                    destructive: item.destructive,
                    enabled: item.enabled
                )
            }
            SkipLiquidKitMenu(
                title: title,
                items: specs,
                borderless: borderless,
                tint: tint.composeColor,
                modifier: context.modifier,
                onSelect: onSelect
            )
        }
        #else
        Menu(title) {
            ForEach(items) { item in
                Button(role: item.destructive ? .destructive : nil) {
                    onSelect(item.id)
                } label: {
                    if let sym = item.systemImage {
                        Label(item.title, systemImage: item.checked ? "checkmark" : sym)
                    } else if item.checked {
                        Label(item.title, systemImage: "checkmark")
                    } else {
                        Text(item.title)
                    }
                }
                .disabled(!item.enabled)
            }
        }
        .tint(tint.swiftUIColor)
        #endif
    }
}
