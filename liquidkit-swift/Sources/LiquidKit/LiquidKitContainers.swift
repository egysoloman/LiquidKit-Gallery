import SwiftUI

#if SKIP
// SKIP INSERT: import io.liquidkit.skip.*
#endif

/// Cross-platform text / password / multiline input (`TextField`/`SecureField` on iOS, `LiquidKitTextField` on Android).
public struct LiquidTextInput: View {
    public let placeholder: String
    @Binding private var text: String
    public let secret: Bool
    public let multiline: Bool
    public let roundedBorder: Bool
    public let tint: LiquidRGBA

    public init(
        _ placeholder: String,
        text: Binding<String>,
        secret: Bool = false,
        multiline: Bool = false,
        roundedBorder: Bool = false,
        tint: LiquidRGBA = .accentBlue
    ) {
        self.placeholder = placeholder
        _text = text
        self.secret = secret
        self.multiline = multiline
        self.roundedBorder = roundedBorder
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitTextField(
                value: text,
                placeholder: placeholder,
                secret: secret,
                multiline: multiline,
                roundedBorder: roundedBorder,
                tint: tint.composeColor,
                modifier: context.modifier,
                onValueChange: { text = $0 }
            )
        }
        #else
        Group {
            if secret {
                SecureField(placeholder, text: $text)
            } else if multiline {
                TextField(placeholder, text: $text, axis: .vertical)
                    .lineLimit(3...6)
            } else {
                TextField(placeholder, text: $text)
            }
        }
        .tint(tint.swiftUIColor)
        #endif
    }
}

/// Floating glass search bar (`LiquidKitSearchField` on Android, glass capsule `TextField` on iOS).
public struct LiquidSearchBar: View {
    @Binding private var query: String
    public let placeholder: String
    public let cancelLabel: String?
    public let tint: LiquidRGBA

    public init(
        query: Binding<String>,
        placeholder: String = "搜索",
        cancelLabel: String? = "取消",
        tint: LiquidRGBA = .accentBlue
    ) {
        _query = query
        self.placeholder = placeholder
        self.cancelLabel = cancelLabel
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSearchField(
                query: query,
                placeholder: placeholder,
                cancelLabel: cancelLabel,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                query = next
            }
        }
        #else
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField(placeholder, text: $query)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 44)
        .liquidGlass(variant: .regular)
        #endif
    }
}

/// Progress bar & 12-spoke Apple-style indeterminate spinner.
public struct LiquidProgress: View {
    public let value: Double?
    public let label: String?
    public let tint: LiquidRGBA

    public init(value: Double? = nil, label: String? = nil, tint: LiquidRGBA = .accentBlue) {
        self.value = value.map { min(1.0, max(0.0, $0)) }
        self.label = label
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitProgressView(
                value: value,
                label: label,
                tint: tint.composeColor,
                modifier: context.modifier
            )
        }
        #else
        if let value {
            ProgressView(value: value)
                .progressViewStyle(.linear)
                .tint(tint.swiftUIColor)
        } else if let label {
            ProgressView(label)
        } else {
            ProgressView()
        }
        #endif
    }
}

/// Circular or linear gauge (`Gauge` on iOS, `LiquidKitGauge` / `LiquidKitLinearGauge` on Android).
public struct LiquidGaugeControl: View {
    public let value: Double
    public let label: String?
    public let style: String
    public let tint: LiquidRGBA

    public init(value: Double, label: String? = nil, style: String = "circular", tint: LiquidRGBA = .accentBlue) {
        self.value = min(1.0, max(0.0, value))
        self.label = label
        self.style = style
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitGauge(
                value: value,
                label: label,
                style: style,
                tint: tint.composeColor,
                modifier: context.modifier
            )
        }
        #else
        Gauge(value: value) {
            if let label { Text(label) }
        }
        .tint(tint.swiftUIColor)
        #endif
    }
}

/// Key-value row (`LabeledContent` on iOS, `LiquidKitLabeledContent` on Android).
public struct LiquidKeyValueRow: View {
    public let label: String
    public let value: String

    public init(_ label: String, value: String) {
        self.label = label
        self.value = value
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitLabeledContent(label: label, value: value, modifier: context.modifier)
        }
        #else
        LabeledContent(label, value: value)
        #endif
    }
}

/// Expandable disclosure container (`DisclosureGroup` on iOS, `LiquidKitDisclosureGroup` on Android).
public struct LiquidDisclosure<Content: View>: View {
    public let title: String
    @Binding private var isExpanded: Bool
    private let content: () -> Content

    public init(_ title: String, isExpanded: Binding<Bool>, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        _isExpanded = isExpanded
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitDisclosureGroup(
                title: title,
                expanded: isExpanded,
                modifier: context.modifier,
                onExpandedChange: { isExpanded = $0 }
            ) {
                content().Compose(context: context.content())
            }
        }
        #else
        DisclosureGroup(title, isExpanded: $isExpanded) {
            content()
        }
        #endif
    }
}

/// Tinted inset card container (`GroupBox` on iOS, `LiquidKitGroupBox` on Android).
public struct LiquidGroupCard<Content: View>: View {
    public let title: String?
    private let content: () -> Content

    public init(_ title: String? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitGroupBox(title: title, modifier: context.modifier) {
                content().Compose(context: context.content())
            }
        }
        #else
        if let title {
            GroupBox(title) { content() }
        } else {
            GroupBox { content() }
        }
        #endif
    }
}

/// Empty / unavailable state view (`ContentUnavailableView` on iOS, `LiquidKitUnavailableView` on Android).
public struct LiquidEmptyView: View {
    public let title: String
    public let systemImage: String
    public let description: String?

    public init(_ title: String, systemImage: String = "folder", description: String? = nil) {
        self.title = title
        self.systemImage = systemImage
        self.description = description
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitUnavailableView(
                title: title,
                systemImage: systemImage,
                description: description,
                modifier: context.modifier
            )
        }
        #else
        if let description {
            ContentUnavailableView(title, systemImage: systemImage, description: Text(description))
        } else {
            ContentUnavailableView(title, systemImage: systemImage)
        }
        #endif
    }
}

/// Edge placement for iOS 27 `LiquidSafeAreaBarView` (`.safeAreaBar(edge:)`).
public enum LiquidSafeAreaEdgeKind: String, CaseIterable, Sendable {
    case top
    case bottom
}

/// Edge-attached progressive-blur Liquid Glass bar (`safeAreaBar(edge:)` on iOS 27, `LiquidKitSafeAreaBar` on Android).
public struct LiquidSafeAreaBarView<Content: View>: View {
    public let edge: LiquidSafeAreaEdgeKind
    public let blurRadius: Double
    private let content: () -> Content

    public init(
        edge: LiquidSafeAreaEdgeKind = .bottom,
        blurRadius: Double = 16.0,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.edge = edge
        self.blurRadius = max(0.0, blurRadius)
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSafeAreaBar(
                edge: edge.rawValue,
                blurRadius: blurRadius,
                modifier: context.modifier
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
        .frame(maxWidth: .infinity)
        .background(.ultraThinMaterial)
        #endif
    }
}

/// Capsule count/status badge (`.badge(...)` on iOS, `LiquidKitBadge` on Android).
public struct LiquidBadgeView: View {
    public let text: String
    public let tint: LiquidRGBA

    public init(_ text: String, tint: LiquidRGBA = .destructiveRed) {
        self.text = text
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitBadge(text: text, tint: tint.composeColor, modifier: context.modifier)
        }
        #else
        Text(text)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(tint.swiftUIColor, in: Capsule())
        #endif
    }
}

/// Inline onboarding tip card (`TipKit` `TipView` on iOS, `LiquidKitTipCard` on Android).
public struct LiquidTipBannerView: View {
    public let title: String
    public let message: String
    public let systemImage: String
    public let tint: LiquidRGBA
    private let onDismiss: (() -> Void)?

    public init(
        _ title: String,
        message: String,
        systemImage: String = "sparkles",
        tint: LiquidRGBA = .accentBlue,
        onDismiss: (() -> Void)? = nil
    ) {
        self.title = title
        self.message = message
        self.systemImage = systemImage
        self.tint = tint
        self.onDismiss = onDismiss
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitTipCard(
                title: title,
                message: message,
                systemImage: systemImage,
                tint: tint.composeColor,
                onDismiss: onDismiss,
                modifier: context.modifier
            )
        }
        #else
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: systemImage).foregroundStyle(tint.swiftUIColor)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(message).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            if let onDismiss {
                Button(action: onDismiss) {
                    Image(systemName: "xmark").font(.caption)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(tint.swiftUIColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        #endif
    }
}
