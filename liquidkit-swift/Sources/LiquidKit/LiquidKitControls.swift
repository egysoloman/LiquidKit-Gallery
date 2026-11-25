import SwiftUI

#if SKIP
// SKIP INSERT: import androidx.compose.ui.graphics.Color as ComposeColor
// SKIP INSERT: import io.liquidkit.skip.*
#endif

/// Cross-platform switch (`Toggle` on iOS, `LiquidKitToggle` on Android).
public struct LiquidSwitch: View {
    public let title: String?
    @Binding private var isOn: Bool

    public init(_ title: String? = nil, isOn: Binding<Bool>) {
        self.title = title
        _isOn = isOn
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitToggle(title: title, isOn: isOn, modifier: context.modifier) { next in
                isOn = next
            }
        }
        #else
        if let title {
            Toggle(title, isOn: $isOn)
        } else {
            Toggle("", isOn: $isOn).labelsHidden()
        }
        #endif
    }
}

/// Cross-platform refractive slider (`Slider` with optional iOS 26/27 `SliderTick` marks & `neutralValue` on iOS, `LiquidKitSlider` on Android).
public struct LiquidSliderControl: View {
    @Binding private var value: Double
    public let bounds: ClosedRange<Double>
    public let tapToSeek: Bool
    public let ticks: [Double]
    public let neutralValue: Double?

    public init(
        value: Binding<Double>,
        in bounds: ClosedRange<Double> = 0...1,
        tapToSeek: Bool = true,
        ticks: [Double] = [],
        neutralValue: Double? = nil
    ) {
        _value = value
        self.bounds = bounds
        self.tapToSeek = tapToSeek
        self.ticks = ticks
        self.neutralValue = neutralValue
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSlider(
                value: value,
                minValue: bounds.lowerBound,
                maxValue: bounds.upperBound,
                tapToSeek: tapToSeek,
                ticks: ticks,
                neutralValue: neutralValue,
                modifier: context.modifier
            ) { next in
                value = next
            }
        }
        #else
        Slider(value: $value, in: bounds)
        #endif
    }
}

/// Pill toggle button (`Toggle.toggleStyle(.button)` on iOS, `LiquidKitToggleButton` on Android).
public struct LiquidTogglePill: View {
    public let title: String
    public let systemImage: String?
    @Binding private var isOn: Bool
    public let tint: LiquidRGBA

    public init(_ title: String, systemImage: String? = nil, isOn: Binding<Bool>, tint: LiquidRGBA = .accentBlue) {
        self.title = title
        self.systemImage = systemImage
        _isOn = isOn
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitToggleButton(
                label: title,
                checked: isOn,
                systemImage: systemImage,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                isOn = next
            }
        }
        #else
        Group {
            if let systemImage {
                Toggle(title, systemImage: systemImage, isOn: $isOn)
            } else {
                Toggle(title, isOn: $isOn)
            }
        }
        .toggleStyle(.button)
        .tint(tint.swiftUIColor)
        #endif
    }
}

/// Glass material density slider (`0.0` clear to `1.0` dense).
public struct LiquidOpacityControl: View {
    @Binding private var opacity: Double
    public let label: String
    public let showIcons: Bool
    private let onFinished: () -> Void

    public init(
        opacity: Binding<Double>,
        label: String = "玻璃不透明度",
        showIcons: Bool = true,
        onFinished: @escaping () -> Void = {}
    ) {
        _opacity = opacity
        self.label = label
        self.showIcons = showIcons
        self.onFinished = onFinished
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitOpacitySlider(
                value: opacity,
                label: label,
                showIcons: showIcons,
                modifier: context.modifier,
                onValueChange: { opacity = $0 },
                onFinished: onFinished
            )
        }
        #else
        Slider(value: $opacity, in: 0...1) {
            Text(label)
        } minimumValueLabel: {
            if showIcons { Image(systemName: "square.on.square") }
        } maximumValueLabel: {
            if showIcons { Image(systemName: "square.fill.on.square.fill") }
        } onEditingChanged: { editing in
            if !editing { onFinished() }
        }
        #endif
    }
}

/// Cross-platform stepper (`Stepper` on iOS, `LiquidKitStepper` on Android).
public struct LiquidStepperControl: View {
    public let label: String
    @Binding private var value: Int
    public let range: ClosedRange<Int>
    public let tint: LiquidRGBA

    public init(
        _ label: String,
        value: Binding<Int>,
        in range: ClosedRange<Int> = 0...100,
        tint: LiquidRGBA = .accentBlue
    ) {
        self.label = label
        _value = value
        self.range = range
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitStepper(
                label: label,
                value: value,
                minValue: range.lowerBound,
                maxValue: range.upperBound,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                value = next
            }
        }
        #else
        Stepper("\(label): \(value)", value: $value, in: range)
            .tint(tint.swiftUIColor)
        #endif
    }
}

/// Segmented refractive lens picker (`Picker(.segmented)` on iOS, `LiquidKitSegmentedPicker` on Android).
public struct LiquidSegmentedControl: View {
    public let options: [String]
    @Binding private var selection: String

    public init(options: [String], selection: Binding<String>) {
        self.options = options
        _selection = selection
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitSegmentedPicker(
                options: options,
                selected: selection,
                modifier: context.modifier
            ) { next in
                selection = next
            }
        }
        #else
        Picker("", selection: $selection) {
            ForEach(options, id: \.self) { option in
                Text(option).tag(option)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        #endif
    }
}

/// Menu-style picker (`Picker(.menu)` on iOS, `LiquidKitMenuPicker` on Android).
public struct LiquidMenuPickerControl: View {
    public let label: String
    public let options: [String]
    @Binding private var selection: String
    public let tint: LiquidRGBA

    public init(
        _ label: String,
        options: [String],
        selection: Binding<String>,
        tint: LiquidRGBA = .accentBlue
    ) {
        self.label = label
        self.options = options
        _selection = selection
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitMenuPicker(
                label: label,
                options: options,
                selected: selection,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                selection = next
            }
        }
        #else
        Picker(label, selection: $selection) {
            ForEach(options, id: \.self) { option in
                Text(option).tag(option)
            }
        }
        .pickerStyle(.menu)
        .tint(tint.swiftUIColor)
        #endif
    }
}

/// Calendar date value shared across SwiftUI and Android `LiquidKitDatePicker`.
public struct LiquidDateValue: Equatable, Hashable, Sendable {
    public var year: Int
    public var month: Int
    public var day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = min(12, max(1, month))
        self.day = min(31, max(1, day))
    }

    public var foundationDate: Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        return Calendar.current.date(from: components) ?? Date()
    }

    public static func from(_ date: Date) -> LiquidDateValue {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return LiquidDateValue(year: c.year ?? 2026, month: c.month ?? 1, day: c.day ?? 1)
    }
}

/// Cross-platform date picker (`DatePicker` on iOS, `LiquidKitDatePicker` on Android).
public struct LiquidDatePickerControl: View {
    public let label: String
    @Binding private var date: LiquidDateValue
    public let style: String
    public let tint: LiquidRGBA

    public init(
        _ label: String,
        date: Binding<LiquidDateValue>,
        style: String = "compact",
        tint: LiquidRGBA = .accentBlue
    ) {
        self.label = label
        _date = date
        self.style = style
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitDatePicker(
                label: label,
                year: date.year,
                month: date.month,
                day: date.day,
                style: style,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { y, m, d in
                date = LiquidDateValue(year: y, month: m, day: d)
            }
        }
        #else
        let binding = Binding<Date>(
            get: { date.foundationDate },
            set: { date = LiquidDateValue.from($0) }
        )
        DatePicker(label, selection: binding, displayedComponents: .date)
            .tint(tint.swiftUIColor)
        #endif
    }
}

/// Cross-platform color picker (`ColorPicker` on iOS, `LiquidKitColorPicker` on Android).
public struct LiquidColorPickerControl: View {
    public let label: String
    @Binding private var color: LiquidRGBA
    public let supportsOpacity: Bool

    public init(_ label: String, color: Binding<LiquidRGBA>, supportsOpacity: Bool = true) {
        self.label = label
        _color = color
        self.supportsOpacity = supportsOpacity
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitColorPicker(
                label: label,
                color: color.composeColor,
                supportsOpacity: supportsOpacity,
                modifier: context.modifier
            ) { next in
                color = LiquidRGBA(
                    red: Double(next.red),
                    green: Double(next.green),
                    blue: Double(next.blue),
                    alpha: Double(next.alpha)
                )
            }
        }
        #else
        let binding = Binding<Color>(
            get: { color.swiftUIColor },
            set: { _ in }
        )
        ColorPicker(label, selection: binding, supportsOpacity: supportsOpacity)
        #endif
    }
}

/// Inline grouped checkmark picker (`Picker(.inline)` on iOS, `LiquidKitInlinePicker` on Android).
public struct LiquidInlinePickerControl: View {
    public let title: String?
    public let options: [String]
    @Binding private var selection: String
    public let tint: LiquidRGBA

    public init(
        _ title: String? = nil,
        options: [String],
        selection: Binding<String>,
        tint: LiquidRGBA = .accentBlue
    ) {
        self.title = title
        self.options = options
        _selection = selection
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            SkipLiquidKitInlinePicker(
                options: options,
                selected: selection,
                title: title,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                selection = next
            }
        }
        #else
        Picker(title ?? "", selection: $selection) {
            ForEach(options, id: \.self) { option in
                Text(option).tag(option)
            }
        }
        .pickerStyle(.inline)
        .tint(tint.swiftUIColor)
        #endif
    }
}

/// Swatch/icon option for `LiquidPalettePickerControl` (`Picker(.palette)`).
public struct LiquidPaletteOptionItem: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let label: String
    public let color: LiquidRGBA?
    public let systemImage: String?

    public init(id: String, label: String, color: LiquidRGBA? = nil, systemImage: String? = nil) {
        self.id = id
        self.label = label
        self.color = color
        self.systemImage = systemImage
    }
}

/// Compact palette picker (`Picker(.palette)` on iOS, `LiquidKitPalettePicker` on Android).
public struct LiquidPalettePickerControl: View {
    public let options: [LiquidPaletteOptionItem]
    @Binding private var selection: String
    public let tint: LiquidRGBA

    public init(
        options: [LiquidPaletteOptionItem],
        selection: Binding<String>,
        tint: LiquidRGBA = .accentBlue
    ) {
        self.options = options
        _selection = selection
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            let specs = options.map { item in
                SkipLiquidKitPaletteOptionSpec(
                    id: item.id,
                    label: item.label,
                    color: item.color?.composeColor ?? ComposeColor.Unspecified,
                    systemImage: item.systemImage
                )
            }
            SkipLiquidKitPalettePicker(
                options: specs,
                selectedId: selection,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                selection = next
            }
        }
        #else
        HStack(spacing: 12) {
            ForEach(options) { item in
                let active = item.id == selection
                Button {
                    withAnimation(LiquidSpringToken.snappy.animation) {
                        selection = item.id
                    }
                } label: {
                    ZStack {
                        Circle()
                            .fill(item.color?.swiftUIColor ?? Color.secondary.opacity(0.2))
                            .frame(width: 30, height: 30)
                        if let systemImage = item.systemImage {
                            Image(systemName: systemImage)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(item.color == nil ? (active ? tint.swiftUIColor : .primary) : .white)
                        }
                    }
                    .padding(4)
                    .overlay(
                        Circle().stroke((item.color ?? tint).swiftUIColor, lineWidth: active ? 2.2 : 0)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.label)
            }
        }
        #endif
    }
}

/// Option item for iOS 27 `LiquidTabsPickerControl` (`Picker(.tabs)`).
public struct LiquidTabOptionItem: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let label: String
    public let systemImage: String?

    public init(id: String, label: String, systemImage: String? = nil) {
        self.id = id
        self.label = label
        self.systemImage = systemImage
    }
}

/// Horizontal capsule tabs picker (`Picker(.tabs)` on iOS 27, `LiquidKitTabsPicker` on Android).
public struct LiquidTabsPickerControl: View {
    public let options: [LiquidTabOptionItem]
    @Binding private var selection: String
    public let tint: LiquidRGBA

    public init(
        options: [LiquidTabOptionItem],
        selection: Binding<String>,
        tint: LiquidRGBA = .accentBlue
    ) {
        self.options = options
        _selection = selection
        self.tint = tint
    }

    public var body: some View {
        #if SKIP
        ComposeView { context in
            let specs = options.map { SkipLiquidKitTabOptionSpec(id: $0.id, label: $0.label, systemImage: $0.systemImage) }
            SkipLiquidKitTabsPicker(
                options: specs,
                selectedId: selection,
                tint: tint.composeColor,
                modifier: context.modifier
            ) { next in
                selection = next
            }
        }
        #else
        Picker("", selection: $selection) {
            ForEach(options) { item in
                if let systemImage = item.systemImage {
                    Label(item.label, systemImage: systemImage).tag(item.id)
                } else {
                    Text(item.label).tag(item.id)
                }
            }
        }
        .pickerStyle(.segmented)
        .tint(tint.swiftUIColor)
        #endif
    }
}
