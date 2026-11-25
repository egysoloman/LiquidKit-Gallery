import SwiftUI

#if SKIP
// SKIP INSERT: import androidx.compose.ui.graphics.Color as ComposeColor
// SKIP INSERT: import io.liquidkit.skip.*
#endif

/// Optical variant matching iOS 26/27 Liquid Glass `.regular`, `.clear`, and `.identity`.
public enum LiquidGlassVariant: String, CaseIterable, Sendable {
    case regular
    case clear
    case identity
}

/// Platform-neutral RGBA color model that converts cleanly to SwiftUI `Color` and Compose `Color`.
public struct LiquidRGBA: Equatable, Hashable, Sendable {
    public let red: Double
    public let green: Double
    public let blue: Double
    public let alpha: Double

    public init(red: Double, green: Double, blue: Double, alpha: Double = 1.0) {
        self.red = min(1.0, max(0.0, red))
        self.green = min(1.0, max(0.0, green))
        self.blue = min(1.0, max(0.0, blue))
        self.alpha = min(1.0, max(0.0, alpha))
    }

    public static let accentBlue = LiquidRGBA(red: 0.0, green: 0.478, blue: 1.0)
    public static let foldTeal = LiquidRGBA(red: 0.19, green: 0.64, blue: 0.65)
    public static let destructiveRed = LiquidRGBA(red: 1.0, green: 0.231, blue: 0.188)

    public var swiftUIColor: Color {
        Color(red: red, green: green, blue: blue, opacity: alpha)
    }

    #if SKIP
    public var composeColor: ComposeColor {
        skipLiquidKitColor(red, green, blue, alpha)
    }
    #endif
}

/// Spring tokens shared between iOS SwiftUI (`Animation.spring`) and Android LiquidKit (`GlassSpring`).
public struct LiquidSpringToken: Equatable, Sendable {
    public let response: Double
    public let dampingFraction: Double

    public init(response: Double, dampingFraction: Double) {
        self.response = max(0.05, response)
        self.dampingFraction = min(1.0, max(0.05, dampingFraction))
    }

    public static let snappy = LiquidSpringToken(response: 0.25, dampingFraction: 0.85)
    public static let smooth = LiquidSpringToken(response: 0.32, dampingFraction: 1.0)
    public static let bouncy = LiquidSpringToken(response: 0.35, dampingFraction: 0.70)

    public var animation: Animation {
        .spring(response: response, dampingFraction: dampingFraction)
    }
}

private struct LiquidKitOpacityKey: EnvironmentKey {
    static let defaultValue: Double = 0.5
}

public extension EnvironmentValues {
    var liquidKitOpacity: Double {
        get { self[LiquidKitOpacityKey.self] }
        set { self[LiquidKitOpacityKey.self] = min(1.0, max(0.0, newValue)) }
    }
}

/// Root container providing ambient Liquid Glass appearance and backdrop capture.
/// - On iOS/macOS: propagates `liquidKitOpacity` and optional color scheme into native SwiftUI.
/// - On Android (Skip): wraps the subtree in `io.liquidkit.skip.SkipLiquidKitRoot` so every child
///   LiquidKit control samples the live scene backdrop automatically.
public struct LiquidKitRoot<Content: View>: View {
    public let opacity: Double
    public let darkOverride: Bool?
    private let content: () -> Content

    public init(
        opacity: Double = 0.5,
        dark: Bool? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.opacity = min(1.0, max(0.0, opacity))
        self.darkOverride = dark
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitRoot(
                modifier: context.modifier,
                opacity: opacity,
                darkOverride: darkOverride
            ) {
                content().Compose(context: context.content())
            }
        }
        #else
        Group {
            if let darkOverride {
                content().preferredColorScheme(darkOverride ? .dark : .light)
            } else {
                content()
            }
        }
        .environment(\.liquidKitOpacity, opacity)
        #endif
    }
}

/// Standalone Liquid Glass surface bridging iOS 26/27 `.glassEffect` (`.regular` / `.clear` / `.identity` + `.tint` + `.interactive`) and Android `LiquidKitGlassSurface`.
public struct LiquidGlassSurfaceView<Content: View>: View {
    public let variant: LiquidGlassVariant
    public let cornerRadius: Double?
    public let tint: LiquidRGBA?
    public let interactive: Bool
    private let onClick: (() -> Void)?
    private let content: () -> Content
    @Environment(\.liquidKitOpacity) private var opacity
    @Environment(\.colorScheme) private var colorScheme

    public init(
        variant: LiquidGlassVariant = .regular,
        cornerRadius: Double? = nil,
        tint: LiquidRGBA? = nil,
        interactive: Bool = false,
        onClick: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.variant = variant
        self.cornerRadius = cornerRadius
        self.tint = tint
        self.interactive = interactive
        self.onClick = onClick
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitGlassSurface(
                modifier: context.modifier,
                variant: variant.rawValue,
                cornerRadius: cornerRadius,
                tint: tint?.composeColor ?? ComposeColor.Unspecified,
                interactive: interactive,
                onClick: onClick
            ) {
                content().Compose(context: context.content())
            }
        }
        #else
        let bodyContent = Group {
            if let onClick {
                Button(action: onClick) { content() }.buttonStyle(.plain)
            } else {
                content()
            }
        }
        if variant == .identity {
            bodyContent
        } else {
            let backing = tint?.swiftUIColor ?? (colorScheme == .dark ? Color.black : Color.white).opacity(opacity * 0.42)
            if let radius = cornerRadius {
                let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
                if #available(iOS 26.0, macOS 26.0, *) {
                    let base: Glass = variant == .clear ? .clear : .regular.tint(backing)
                    let glass = interactive ? base.interactive() : base
                    bodyContent.glassEffect(glass, in: shape)
                } else {
                    bodyContent.background(.regularMaterial, in: shape)
                }
            } else {
                if #available(iOS 26.0, macOS 26.0, *) {
                    let base: Glass = variant == .clear ? .clear : .regular.tint(backing)
                    let glass = interactive ? base.interactive() : base
                    bodyContent.glassEffect(glass, in: Capsule())
                } else {
                    bodyContent.background(.regularMaterial, in: Capsule())
                }
            }
        }
        #endif
    }
}

/// Proximity-fusing container matching iOS 26/27 `GlassEffectContainer` and Android `LiquidKitGlassEffectContainer`.
public struct LiquidGlassContainer<Content: View>: View {
    public let spacing: Double
    private let content: () -> Content

    public init(spacing: Double = 8.0, @ViewBuilder content: @escaping () -> Content) {
        self.spacing = max(0.0, spacing)
        self.content = content
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitGlassContainer(modifier: context.modifier, spacing: spacing) {
                content().Compose(context: context.content())
            }
        }
        #else
        if #available(iOS 26.0, macOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) {
                content()
            }
        } else {
            content()
        }
        #endif
    }
}

public extension View {
    /// Applies a Liquid Glass surface behind this view on both iOS 26/27 and Android (via Skip).
    func liquidGlass(
        variant: LiquidGlassVariant = .regular,
        cornerRadius: Double? = nil,
        tint: LiquidRGBA? = nil,
        interactive: Bool = false
    ) -> some View {
        LiquidGlassSurfaceView(variant: variant, cornerRadius: cornerRadius, tint: tint, interactive: interactive) {
            self
        }
    }
}
