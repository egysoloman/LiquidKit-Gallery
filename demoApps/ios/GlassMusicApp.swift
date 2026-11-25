import SwiftUI

struct AlbumItem: Identifiable {
    let id: String
    let title: String
    let artist: String
    let genre: String
    let colors: [Color]
    let symbol: String
    let duration: String
}

private let sampleAlbums: [AlbumItem] = [
    AlbumItem(id: "aurora", title: "极光协奏", artist: "林屿 · 空间音频", genre: "精选",
              colors: [Color(red: 0.00, green: 0.78, blue: 0.75), Color(red: 0.35, green: 0.34, blue: 0.84)],
              symbol: "sparkles", duration: "4:18"),
    AlbumItem(id: "ocean", title: "深海回声", artist: "青鲸乐队", genre: "精选",
              colors: [Color(red: 0.00, green: 0.48, blue: 1.00), Color(red: 0.19, green: 0.69, blue: 0.78)],
              symbol: "drop.halffull", duration: "3:52"),
    AlbumItem(id: "tokyo", title: "东京雨夜", artist: "Sora Takahashi", genre: "歌单",
              colors: [Color(red: 1.00, green: 0.18, blue: 0.33), Color(red: 0.69, green: 0.32, blue: 0.87)],
              symbol: "moon.fill", duration: "5:04"),
    AlbumItem(id: "drift", title: "失重漫游", artist: "Nova Collective", genre: "歌单",
              colors: [Color(red: 1.00, green: 0.58, blue: 0.00), Color(red: 1.00, green: 0.23, blue: 0.19)],
              symbol: "sun.max.fill", duration: "3:41"),
    AlbumItem(id: "amber", title: "琥珀爵士", artist: "Quartet Noir", genre: "无损",
              colors: [Color(red: 0.85, green: 0.52, blue: 0.16), Color(red: 0.48, green: 0.24, blue: 0.62)],
              symbol: "star.fill", duration: "6:12"),
    AlbumItem(id: "orbit", title: "星际航线", artist: "Kairo Synth", genre: "无损",
              colors: [Color(red: 0.20, green: 0.78, blue: 0.35), Color(red: 0.00, green: 0.48, blue: 1.00)],
              symbol: "globe", duration: "4:45"),
]

@main
struct GlassMusicApp: App {
    var body: some Scene {
        WindowGroup {
            GlassMusicRootView()
        }
    }
}

enum MusicTab: String, Hashable {
    case listen, browse, library, search
}

struct GlassMusicRootView: View {
    @State private var selectedTab: MusicTab = .listen
    @State private var filter = "精选"
    @State private var currentTrack = sampleAlbums[0]
    @State private var isPlaying = true
    @State private var progress = 0.38
    @State private var spatialAudio = true
    @State private var showPlayer = false
    @State private var searchText = ""

    private let accent = Color(red: 1.0, green: 0.176, blue: 0.333)
    private let filters = ["精选", "歌单", "无损"]

    private var filteredAlbums: [AlbumItem] {
        if filter == "精选" { return sampleAlbums }
        return sampleAlbums.filter { $0.genre == filter } + sampleAlbums.filter { $0.genre != filter }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("立即收听", systemImage: "play.fill", value: MusicTab.listen) {
                NavigationStack {
                    listenContent
                        .navigationTitle("流光音乐")
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button {
                                    showPlayer = true
                                } label: {
                                    Image(systemName: "slider.horizontal.3")
                                }
                            }
                        }
                }
            }
            Tab("浏览", systemImage: "square.grid.2x2", value: MusicTab.browse) {
                NavigationStack {
                    listenContent
                        .navigationTitle("浏览")
                }
            }
            Tab("资料库", systemImage: "star.fill", value: MusicTab.library) {
                NavigationStack {
                    listenContent
                        .navigationTitle("资料库")
                }
            }
            Tab("搜索", systemImage: "magnifyingglass", value: MusicTab.search, role: .search) {
                NavigationStack {
                    listenContent
                        .navigationTitle("搜索音乐")
                        .searchable(text: $searchText, prompt: "艺人、专辑与无损歌单")
                }
            }
        }
        .tint(accent)
        .sheet(isPresented: $showPlayer) {
            nowPlayingSheet
                .presentationDetents([.medium, .large])
        }
    }

    private var listenContent: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: 16) {
                    Picker("分类", selection: $filter) {
                        ForEach(filters, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    heroCard

                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 14) {
                        ForEach(filteredAlbums) { album in
                            albumCard(album)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 6)
                .padding(.bottom, 92)
            }
            .background(Color(.systemGroupedBackground))

            miniPlayerBar
                .padding(.horizontal, 16)
                .padding(.bottom, 10)
        }
    }

    private var heroCard: some View {
        ZStack(alignment: .bottomLeading) {
            MeshGradient(
                width: 3, height: 3,
                points: [
                    [0.0, 0.0], [0.5, 0.0], [1.0, 0.0],
                    [0.0, 0.5], [0.55, 0.45], [1.0, 0.5],
                    [0.0, 1.0], [0.5, 1.0], [1.0, 1.0],
                ],
                colors: [
                    Color(red: 1.00, green: 0.18, blue: 0.33), Color(red: 0.69, green: 0.32, blue: 0.87), Color(red: 0.35, green: 0.34, blue: 0.84),
                    Color(red: 1.00, green: 0.58, blue: 0.00), Color(red: 0.96, green: 0.25, blue: 0.48), Color(red: 0.00, green: 0.48, blue: 1.00),
                    Color(red: 0.16, green: 0.12, blue: 0.32), Color(red: 0.32, green: 0.18, blue: 0.58), Color(red: 0.00, green: 0.78, blue: 0.75),
                ]
            )

            VStack(alignment: .leading, spacing: 10) {
                Text("空间音频精选 · 24-bit ALAC")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.92))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .glassEffect(.clear, in: .capsule)

                Text("午夜霓虹 · 2026")
                    .font(.title2.bold())
                    .foregroundStyle(.white)

                Text("沉浸杜比全景声与高解析母带精选，呈现通透玻璃质感声场。")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.88))

                GlassEffectContainer(spacing: 10) {
                    HStack(spacing: 10) {
                        Button {
                            currentTrack = sampleAlbums[0]
                            isPlaying = true
                        } label: {
                            Label("立即播放", systemImage: "play.fill")
                                .font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 4)
                        }
                        .buttonStyle(.glassProminent)
                        .tint(accent)

                        Button {
                            currentTrack = sampleAlbums.randomElement() ?? sampleAlbums[0]
                            isPlaying = true
                        } label: {
                            Label("随机播放", systemImage: "sparkles")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 4)
                        }
                        .buttonStyle(.glass)
                    }
                }
            }
            .padding(18)
        }
        .frame(height: 210)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func albumCard(_ album: AlbumItem) -> some View {
        Button {
            currentTrack = album
            isPlaying = true
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                ZStack(alignment: .bottomTrailing) {
                    LinearGradient(colors: album.colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                    Image(systemName: album.symbol)
                        .font(.system(size: 32, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.92))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    Text(album.duration)
                        .font(.caption2.monospacedDigit().weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(.black.opacity(0.28), in: Capsule())
                        .padding(8)
                }
                .frame(height: 126)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(album.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text(album.artist)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 4)
            }
            .padding(8)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var miniPlayerBar: some View {
        HStack(spacing: 12) {
            ZStack {
                LinearGradient(colors: currentTrack.colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                Image(systemName: currentTrack.symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 38, height: 38)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 1) {
                Text(currentTrack.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(currentTrack.artist)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Button {
                isPlaying.toggle()
            } label: {
                Image(systemName: isPlaying ? "sparkles" : "play.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(accent)
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .glassEffect(.regular.interactive(), in: .capsule)
        .onTapGesture { showPlayer = true }
    }

    private var nowPlayingSheet: some View {
        NavigationStack {
            VStack(spacing: 18) {
                ZStack {
                    LinearGradient(colors: currentTrack.colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                    Image(systemName: currentTrack.symbol)
                        .font(.system(size: 56, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(height: 170)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))

                VStack(spacing: 4) {
                    Text(currentTrack.title).font(.title3.bold())
                    Text(currentTrack.artist).font(.subheadline).foregroundStyle(.secondary)
                }

                VStack(spacing: 6) {
                    Slider(value: $progress, in: 0...1)
                        .tint(accent)
                    HStack {
                        Text("1:38").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        Spacer()
                        Text(currentTrack.duration).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    }
                }

                Toggle("空间音频头部追踪", isOn: $spatialAudio)
                    .tint(accent)

                Spacer()
            }
            .padding(20)
            .navigationTitle("正在播放")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { showPlayer = false }
                }
            }
        }
    }
}
