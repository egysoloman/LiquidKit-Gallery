import SwiftUI

public enum FoldMetric {
    public static let control: CGFloat = 44
    public static let search: CGFloat = 44
    public static let fileRow: CGFloat = 60
    public static let icon: CGFloat = 22
    public static let gutter: CGFloat = 18
}

private struct GlassSurface<Surface: Shape>: ViewModifier {
    let shape: Surface
    let opacity: Double
    @Environment(\.colorScheme) private var theme

    func body(content: Content) -> some View {
        let backing = (theme == .dark ? Color.black : .white).opacity(max(0, min(1, opacity)) * 0.82)
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular.tint(backing), in: shape)
        } else {
            content.background(.regularMaterial, in: shape).background(backing, in: shape)
        }
    }
}

public extension View {
    /// Continuous backing for custom surfaces. Native navigation chrome uses the system material.
    func foldGlass<S: Shape>(in shape: S, opacity: Double = 0.5) -> some View {
        modifier(GlassSurface(shape: shape, opacity: opacity))
    }
}

public struct FoldSearchField: View {
    @Binding private var text: String
    @FocusState private var focused: Bool
    private let placeholder: String
    private let opacity: Double
    public init(_ placeholder: String, text: Binding<String>, opacity: Double = 0.5) {
        self.placeholder = placeholder
        _text = text
        self.opacity = opacity
    }
    public var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField(placeholder, text: $text)
                    .focused($focused).submitLabel(.search).onSubmit { focused = false }
                    .accessibilityIdentifier("search")
                if !text.isEmpty {
                    Button { text = "" } label: { Image(systemName: "xmark.circle.fill") }
                        .accessibilityLabel("清除搜索").foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 14).frame(minHeight: FoldMetric.search)
            .foldGlass(in: Capsule(), opacity: opacity)
            if focused {
                Button("取消") { text = ""; focused = false }
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.2), value: focused)
    }
}

public struct FoldToolbarButton: View {
    private let title: String
    private let symbol: String?
    private let action: () -> Void
    public init(_ title: String, symbol: String? = nil, action: @escaping () -> Void) {
        self.title = title; self.symbol = symbol; self.action = action
    }
    public var body: some View {
        Button(action: action) {
            if let symbol { Label(title, systemImage: symbol) } else { Text(title) }
        }.accessibilityLabel(title)
    }
}

/// SwiftUI supplies the opening transition, dismissal, submenus and accessibility actions.
public struct FoldMenu<Title: View, Items: View>: View {
    private let title: Title
    private let items: Items
    private let primary: (() -> Void)?
    public init(primaryAction: (() -> Void)? = nil, @ViewBuilder items: () -> Items, @ViewBuilder label: () -> Title) {
        primary = primaryAction; self.items = items(); title = label()
    }
    public var body: some View {
        if let primary {
            Menu(content: { items }, label: { title }, primaryAction: primary)
        } else {
            Menu(content: { items }, label: { title })
        }
    }
}

public struct FoldFileCell: View {
    private let title: String
    private let subtitle: String
    private let folder: Bool
    public init(_ title: String, subtitle: String, folder: Bool) {
        self.title = title; self.subtitle = subtitle; self.folder = folder
    }
    public var body: some View {
        HStack(spacing: 14) {
            Image(systemName: folder ? "folder.fill" : "doc.text")
                .symbolRenderingMode(.hierarchical).foregroundStyle(.tint)
                .font(.system(size: 29)).frame(width: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).foregroundStyle(.primary).lineLimit(1)
                Text(subtitle).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
        }.frame(minHeight: FoldMetric.fileRow).contentShape(Rectangle())
            .accessibilityElement(children: .combine)
    }
}

public struct FoldEmptyState: View {
    let title: String
    let symbol: String
    public init(_ title: String, symbol: String = "folder") { self.title = title; self.symbol = symbol }
    public var body: some View { ContentUnavailableView(title, systemImage: symbol) }
}

public struct FoldOpacityControl: View {
    @Binding private var value: Double
    public init(value: Binding<Double>) { _value = value }
    public var body: some View {
        Slider(value: $value, in: 0...1) { Text("玻璃不透明度") }
            minimumValueLabel: { Image(systemName: "square.on.square") }
            maximumValueLabel: { Image(systemName: "square.fill.on.square.fill") }
            .accessibilityValue(value.formatted(.percent.precision(.fractionLength(0))))
    }
}

public struct FoldSwitch: View {
    private let title: String
    @Binding private var isOn: Bool
    public init(_ title: String, isOn: Binding<Bool>) {
        self.title = title
        _isOn = isOn
    }
    public var body: some View { Toggle(title, isOn: $isOn).toggleStyle(.switch) }
}

public struct FoldSlider: View {
    private let title: String
    @Binding private var value: Double
    public init(_ title: String, value: Binding<Double>) {
        self.title = title
        _value = value
    }
    public var body: some View { Slider(value: $value, in: 0...1) { Text(title) } }
}

public struct FoldProgress: View {
    private let value: Double
    public init(_ value: Double) { self.value = value.isFinite ? min(1, max(0, value)) : 0 }
    public var body: some View {
        ProgressView(value: value).progressViewStyle(.linear).accessibilityLabel("当前进度")
    }
}
