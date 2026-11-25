import SwiftUI
import Charts

struct WeatherCity: Identifiable {
    let id: String
    let name: String
    let tempC: Int
    let highC: Int
    let lowC: Int
    let condition: String
    let aqi: Int
    let humidity: Double
    let skyLight: [Color]
    let skyDark: [Color]
    let hourly: [(String, String, Int)]
    let daily: [(String, String, Int, Int)]
}

private let sampleCities: [WeatherCity] = [
    WeatherCity(
        id: "shanghai", name: "上海", tempC: 22, highC: 25, lowC: 16, condition: "晴转多云", aqi: 32, humidity: 0.54,
        skyLight: [Color(red: 0.09, green: 0.35, blue: 0.73), Color(red: 0.22, green: 0.56, blue: 0.89), Color(red: 0.41, green: 0.70, blue: 0.94)],
        skyDark: [Color(red: 0.04, green: 0.10, blue: 0.20), Color(red: 0.08, green: 0.20, blue: 0.38), Color(red: 0.12, green: 0.27, blue: 0.48)],
        hourly: [("现在", "sun.max.fill", 22), ("14时", "sun.max.fill", 24), ("15时", "sun.max.fill", 25),
                 ("16时", "sparkles", 24), ("17时", "moon.fill", 21), ("18时", "moon.fill", 19)],
        daily: [("今天", "sun.max.fill", 16, 25), ("周四", "sun.max.fill", 17, 26), ("周五", "drop.halffull", 15, 22),
                ("周六", "sun.max.fill", 14, 23), ("周日", "sun.max.fill", 16, 24)]
    ),
    WeatherCity(
        id: "tokyo", name: "东京", tempC: 19, highC: 21, lowC: 13, condition: "晚霞微风", aqi: 24, humidity: 0.48,
        skyLight: [Color(red: 0.27, green: 0.16, blue: 0.48), Color(red: 0.73, green: 0.27, blue: 0.48), Color(red: 0.94, green: 0.54, blue: 0.29)],
        skyDark: [Color(red: 0.12, green: 0.07, blue: 0.24), Color(red: 0.32, green: 0.12, blue: 0.28), Color(red: 0.44, green: 0.20, blue: 0.16)],
        hourly: [("现在", "sun.max.fill", 19), ("14时", "sun.max.fill", 21), ("15时", "sparkles", 20),
                 ("16时", "moon.fill", 18), ("17时", "moon.fill", 16), ("18时", "moon.fill", 15)],
        daily: [("今天", "sun.max.fill", 13, 21), ("周四", "drop.halffull", 12, 18), ("周五", "sun.max.fill", 13, 20),
                ("周六", "sun.max.fill", 14, 22), ("周日", "sparkles", 13, 20)]
    ),
    WeatherCity(
        id: "sf", name: "旧金山", tempC: 16, highC: 18, lowC: 11, condition: "海雾清朗", aqi: 18, humidity: 0.68,
        skyLight: [Color(red: 0.07, green: 0.31, blue: 0.43), Color(red: 0.15, green: 0.52, blue: 0.61), Color(red: 0.36, green: 0.70, blue: 0.76)],
        skyDark: [Color(red: 0.04, green: 0.13, blue: 0.20), Color(red: 0.08, green: 0.24, blue: 0.31), Color(red: 0.12, green: 0.33, blue: 0.40)],
        hourly: [("现在", "drop.halffull", 16), ("14时", "sun.max.fill", 18), ("15时", "sun.max.fill", 18),
                 ("16时", "sun.max.fill", 17), ("17时", "moon.fill", 14), ("18时", "moon.fill", 13)],
        daily: [("今天", "drop.halffull", 11, 18), ("周四", "sun.max.fill", 12, 19), ("周五", "sun.max.fill", 11, 18),
                ("周六", "drop.halffull", 10, 16), ("周日", "sun.max.fill", 12, 19)]
    ),
]

@main
struct GlassWeatherApp: App {
    var body: some Scene {
        WindowGroup {
            GlassWeatherRootView()
        }
    }
}

struct GlassWeatherRootView: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var cityIndex = 0
    @State private var useFahrenheit = false
    @State private var showUnits = false

    private var city: WeatherCity { sampleCities[cityIndex] }

    private func fmt(_ c: Int) -> String {
        let v = useFahrenheit ? Int(round(Double(c) * 9.0 / 5.0 + 32.0)) : c
        return "\(v)°"
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                colors: colorScheme == .dark ? city.skyDark : city.skyLight,
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    headerView
                        .padding(.top, 18)

                    hourlyCard

                    tempCurveCard

                    HStack(spacing: 12) {
                        aqiCard
                        humidityCard
                    }

                    dailyCard
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 94)
            }

            bottomCityBar
                .padding(.horizontal, 16)
                .padding(.bottom, 10)
        }
        .preferredColorScheme(nil)
    }

    private var headerView: some View {
        VStack(spacing: 4) {
            Text(city.name)
                .font(.largeTitle.weight(.medium))
                .foregroundStyle(.white)
            Text(fmt(city.tempC))
                .font(.system(size: 76, weight: .thin, design: .rounded))
                .foregroundStyle(.white)
            Text(city.condition)
                .font(.headline.weight(.medium))
                .foregroundStyle(.white.opacity(0.92))
            Text("最高 \(fmt(city.highC))   最低 \(fmt(city.lowC))")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.white.opacity(0.84))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
    }

    private var hourlyCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("每小时预报", systemImage: "clock")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.78))
            HStack(spacing: 0) {
                ForEach(city.hourly, id: \.0) { slot in
                    VStack(spacing: 8) {
                        Text(slot.0).font(.caption.weight(.medium)).foregroundStyle(.white.opacity(0.9))
                        Image(systemName: slot.1)
                            .font(.system(size: 18))
                            .foregroundStyle(slot.1 == "sun.max.fill" ? .yellow : .white)
                        Text(fmt(slot.2)).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(16)
        .glassEffect(.clear, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var tempCurveCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("温度趋势", systemImage: "chart.bar.xaxis")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.78))
            Chart {
                ForEach(Array(city.hourly.enumerated()), id: \.offset) { idx, item in
                    AreaMark(
                        x: .value("时间", item.0),
                        y: .value("温度", item.2)
                    )
                    .foregroundStyle(LinearGradient(colors: [.white.opacity(0.35), .clear], startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.catmullRom)

                    LineMark(
                        x: .value("时间", item.0),
                        y: .value("温度", item.2)
                    )
                    .foregroundStyle(.white)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                    .interpolationMethod(.catmullRom)
                }
            }
            .chartYScale(domain: 10...28)
            .chartXAxis {
                AxisMarks { _ in
                    AxisValueLabel().foregroundStyle(.white.opacity(0.8))
                }
            }
            .chartYAxis(.hidden)
            .frame(height: 112)
        }
        .padding(16)
        .glassEffect(.clear, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var aqiCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("空气质量", systemImage: "sparkles")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.78))
            Text("\(city.aqi)")
                .font(.title.bold())
                .foregroundStyle(.white)
            Text("优 · 空气清新")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.white.opacity(0.88))
            Gauge(value: Double(city.aqi), in: 0...150) {
                EmptyView()
            }
            .gaugeStyle(.accessoryLinearCapacity)
            .tint(Gradient(colors: [.green, .yellow, .orange]))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassEffect(.clear, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var humidityCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("相对湿度", systemImage: "drop.halffull")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.78))
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(Int(city.humidity * 100))%")
                        .font(.title.bold())
                        .foregroundStyle(.white)
                    Text("露点 12°")
                        .font(.footnote)
                        .foregroundStyle(.white.opacity(0.84))
                }
                Spacer()
                Gauge(value: city.humidity) {
                    Image(systemName: "drop.halffull")
                        .font(.caption)
                } currentValueLabel: {
                    Text("\(Int(city.humidity * 100))")
                        .font(.caption2.bold())
                }
                .gaugeStyle(.accessoryCircularCapacity)
                .tint(.cyan)
                .foregroundStyle(.white)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassEffect(.clear, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var dailyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("5 日预报", systemImage: "calendar")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.78))
            ForEach(city.daily, id: \.0) { row in
                HStack(spacing: 12) {
                    Text(row.0)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white)
                        .frame(width: 44, alignment: .leading)
                    Image(systemName: row.1)
                        .font(.system(size: 16))
                        .foregroundStyle(row.1 == "sun.max.fill" ? .yellow : .white)
                        .frame(width: 24)
                    Text(fmt(row.2))
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.76))
                        .frame(width: 34, alignment: .trailing)
                    Capsule()
                        .fill(LinearGradient(colors: [.cyan, .yellow, .orange], startPoint: .leading, endPoint: .trailing))
                        .frame(height: 5)
                    Text(fmt(row.3))
                        .font(.subheadline.monospacedDigit().weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(width: 34, alignment: .trailing)
                }
            }
        }
        .padding(16)
        .glassEffect(.clear, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var bottomCityBar: some View {
        GlassEffectContainer(spacing: 10) {
            HStack(spacing: 10) {
                ForEach(Array(sampleCities.enumerated()), id: \.element.id) { idx, c in
                    Button {
                        withAnimation(.snappy) { cityIndex = idx }
                    } label: {
                        Text(c.name)
                            .font(.subheadline.weight(idx == cityIndex ? .bold : .medium))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.glass)
                    .tint(idx == cityIndex ? .white.opacity(0.28) : nil)
                }
                Spacer()
                Button {
                    showUnits.toggle()
                } label: {
                    Text(useFahrenheit ? "°F" : "°C")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.glass)
                .popover(isPresented: $showUnits) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("温标设置").font(.headline)
                        Toggle("使用华氏度 (°F)", isOn: $useFahrenheit)
                    }
                    .padding(16)
                    .frame(width: 220)
                    .presentationCompactAdaptation(.popover)
                }
            }
        }
    }
}
