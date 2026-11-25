import SwiftUI

@main
struct GlassControlApp: App {
    var body: some Scene {
        WindowGroup {
            GlassControlRootView()
        }
    }
}

struct GlassControlRootView: View {
    @State private var activeScene = "专注"
    @State private var room = "客厅"
    @State private var mainLightOn = true
    @State private var brightness = 0.72
    @State private var acTemp = 23
    @State private var fanMode = "自动"
    @State private var ambientColor = Color(red: 1.0, green: 0.58, blue: 0.0)
    @State private var scheduleDate = Date()
    @State private var showAddSheet = false
    @State private var showAwayAlert = false

    private let scenes: [(String, String, Color)] = [
        ("专注", "lightbulb.max.fill", .orange),
        ("影院", "sparkles", .purple),
        ("睡眠", "moon.fill", .indigo),
        ("离家", "bell", .red),
    ]
    private let rooms = ["客厅", "卧室", "书房"]
    private let fanModes = ["自动", "低速", "中速", "强劲"]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    sceneBanner

                    Picker("房间", selection: $room) {
                        ForEach(rooms, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    lightingCard

                    climateCard

                    energyCard
                }
                .padding(.horizontal, 16)
                .padding(.top, 6)
                .padding(.bottom, 36)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("智能中枢")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        showAwayAlert = true
                    } label: {
                        Image(systemName: "bell")
                    }
                    Button {
                        showAddSheet = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .alert("启用离家布防？", isPresented: $showAwayAlert) {
                Button("取消", role: .cancel) {}
                Button("确认离家", role: .destructive) {
                    activeScene = "离家"
                    mainLightOn = false
                }
            } message: {
                Text("将关闭全部客厅与书房灯光，并切换空调至节能待机模式。")
            }
            .sheet(isPresented: $showAddSheet) {
                addDeviceSheet
                    .presentationDetents([.medium])
            }
        }
        .tint(.orange)
    }

    private var sceneBanner: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: [Color(red: 1.0, green: 0.58, blue: 0.0), Color(red: 0.96, green: 0.30, blue: 0.45), Color(red: 0.35, green: 0.34, blue: 0.84)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(room) · 当前场景")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.86))
                        Text("\(activeScene)模式")
                            .font(.title2.bold())
                            .foregroundStyle(.white)
                    }
                    Spacer()
                    Text("\(acTemp)°C · \(Int(brightness * 100))%")
                        .font(.caption.monospacedDigit().weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .glassEffect(.clear, in: .capsule)
                }

                GlassEffectContainer(spacing: 8) {
                    HStack(spacing: 8) {
                        ForEach(scenes, id: \.0) { item in
                            let selected = activeScene == item.0
                            Button {
                                if item.0 == "离家" {
                                    showAwayAlert = true
                                } else {
                                    withAnimation(.snappy) { activeScene = item.0 }
                                }
                            } label: {
                                Label(item.0, systemImage: item.1)
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 4)
                            }
                            .buttonStyle(.glass)
                            .tint(selected ? item.2 : nil)
                        }
                    }
                }
            }
            .padding(16)
        }
        .frame(height: 152)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var lightingCard: some View {
        VStack(spacing: 14) {
            HStack {
                Text("灯光与氛围").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
            }
            Toggle("主灯开关", isOn: $mainLightOn)
                .tint(.orange)
            Divider()
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("环境光亮度").font(.body)
                    Spacer()
                    Text("\(Int(brightness * 100))%").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
                }
                Slider(value: $brightness, in: 0...1)
                    .tint(.orange)
            }
            Divider()
            ColorPicker("氛围灯色", selection: $ambientColor)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var climateCard: some View {
        VStack(spacing: 14) {
            HStack {
                Text("恒温与定时").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
            }
            Stepper("空调温度 \(acTemp)°C", value: $acTemp, in: 16...30)
            Divider()
            HStack {
                Text("风速模式")
                Spacer()
                Picker("风速模式", selection: $fanMode) {
                    ForEach(fanModes, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.menu)
            }
            Divider()
            DatePicker("定时开启", selection: $scheduleDate, displayedComponents: .date)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var energyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("今日能耗").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                Text("6.8 kWh · 优").font(.subheadline.monospacedDigit().weight(.semibold)).foregroundStyle(.green)
            }
            Gauge(value: 0.42) { EmptyView() }
                .gaugeStyle(.accessoryLinearCapacity)
                .tint(.green)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var addDeviceSheet: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: "lightbulb.max.fill")
                    .font(.system(size: 42))
                    .foregroundStyle(.orange)
                    .padding(.top, 12)
                Text("发现附近 Matter 设备").font(.headline)
                Text("将手机靠近智能吸顶灯或温湿度传感器即可快速配网。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Spacer()
            }
            .padding(20)
            .navigationTitle("添加设备")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { showAddSheet = false }
                }
            }
        }
    }
}
