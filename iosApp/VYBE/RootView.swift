import SwiftUI
import UniformTypeIdentifiers

struct RootView: View {
    @AppStorage("completedSetup") private var completedSetup = true
    @State private var tab: RootTab = .home
    @State private var path: [AppRoute] = []
    @State private var showsNowPlaying = false
    @State private var showsImporter = false
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        Group {
            if completedSetup {
                mainContent
            } else {
                SetupView(isComplete: $completedSetup, showsImporter: $showsImporter)
            }
        }
        .background(theme.palette.background.ignoresSafeArea())
        .fileImporter(
            isPresented: $showsImporter,
            allowedContentTypes: [.audio, .mp3, .mpeg4Audio, .wav, .aiff],
            allowsMultipleSelection: true
        ) { result in
            if case let .success(urls) = result { Task { await library.importAudio(from: urls) } }
            if case let .failure(error) = result { library.importMessage = error.localizedDescription }
        }
        .alert("VYBE Library", isPresented: Binding(
            get: { library.importMessage != nil },
            set: { if !$0 { library.importMessage = nil } }
        )) { Button("OK") { library.importMessage = nil } } message: {
            Text(library.importMessage ?? "")
        }
    }

    private var mainContent: some View {
        NavigationStack(path: $path) {
            ZStack(alignment: .bottom) {
                Group {
                    switch tab {
                    case .home: HomeView(path: $path, showsImporter: $showsImporter)
                    case .search: SearchView(path: $path)
                    case .library: LibraryView(path: $path, showsImporter: $showsImporter)
                    }
                }
                .padding(.bottom, 168)

                VStack(spacing: 8) {
                    MiniPlayer(showsNowPlaying: $showsNowPlaying)
                    VYBETabBar(selection: $tab)
                }
            }
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .settings: SettingsView(showsImporter: $showsImporter)
                case .stats: StatsView()
                case let .album(name): CollectionDetailView(title: name, subtitle: "Album", songs: library.albums[name] ?? [])
                case let .artist(name): CollectionDetailView(title: name, subtitle: "Artist", songs: library.artists[name] ?? [])
                case let .playlist(id):
                    if let playlist = library.playlists.first(where: { $0.id == id }) {
                        CollectionDetailView(title: playlist.name, subtitle: "Playlist", songs: library.songs(in: playlist))
                    }
                }
            }
        }
        .fullScreenCover(isPresented: $showsNowPlaying) { NowPlayingView(isPresented: $showsNowPlaying) }
    }
}

