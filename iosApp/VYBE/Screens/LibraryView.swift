import SwiftUI

struct LibraryView: View {
    @Binding var path: [AppRoute]
    @Binding var showsImporter: Bool
    @State private var tab: LibraryTab = .songs
    @State private var newPlaylistName = ""
    @State private var showsNewPlaylist = false
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        VStack(spacing: 0) {
            header
            tabPicker
            ScrollView {
                if library.songs.isEmpty {
                    EmptyLibraryView(presented: $showsImporter)
                } else {
                    content.padding(.horizontal, 14).padding(.top, 16).padding(.bottom, 30)
                }
            }.scrollIndicators(.hidden)
        }
        .foregroundStyle(theme.palette.text)
        .background(
            LinearGradient(colors: [theme.palette.primaryContainer.opacity(0.46), theme.palette.background], startPoint: .top, endPoint: .center)
        )
        .alert("New playlist", isPresented: $showsNewPlaylist) {
            TextField("Playlist name", text: $newPlaylistName)
            Button("Cancel", role: .cancel) { newPlaylistName = "" }
            Button("Create") { library.createPlaylist(name: newPlaylistName); newPlaylistName = "" }
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            Text("Library")
                .font(.system(size: theme.compactLibraryHeader ? 39 : 52, weight: .bold, design: .rounded))
            Spacer()
            CircleIconButton(symbol: "plus") { showsImporter = true }
            CircleIconButton(symbol: "gearshape.fill") { path.append(.settings) }
        }.padding(.horizontal, 18).padding(.top, 12).padding(.bottom, 16)
    }

    private var tabPicker: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 10) {
                ForEach(LibraryTab.allCases) { item in
                    Button { withAnimation(.snappy) { tab = item } } label: {
                        Text(item.rawValue.uppercased())
                            .font(.system(.subheadline, design: .rounded, weight: .bold))
                            .padding(.horizontal, 23).frame(height: 54)
                            .background(tab == item ? theme.palette.primary : theme.palette.surface, in: Capsule())
                            .foregroundStyle(tab == item ? theme.palette.onPrimaryContainer : theme.palette.text)
                    }.buttonStyle(.plain)
                }
            }.padding(.horizontal, 14)
        }.scrollIndicators(.hidden)
    }

    @ViewBuilder private var content: some View {
        switch tab {
        case .songs: songList(library.songs)
        case .favorites: songList(library.favoriteSongs)
        case .albums:
            collectionGrid(items: library.albums.keys.sorted(), type: "Album") { path.append(.album($0)) }
        case .artists:
            collectionGrid(items: library.artists.keys.sorted(), type: "Artist") { path.append(.artist($0)) }
        case .playlists: playlistGrid
        }
    }

    private func songList(_ songs: [Song]) -> some View {
        LazyVStack(spacing: 11) {
            HStack {
                Button { if let first = songs.shuffled().first { player.play(first, in: songs.shuffled()) } } label: {
                    Label("Shuffle", systemImage: "shuffle")
                        .font(.system(.headline, design: .rounded, weight: .bold))
                        .padding(.horizontal, 20).frame(height: 52)
                        .background(theme.palette.primaryContainer, in: Capsule())
                }.buttonStyle(.plain)
                Spacer()
                Text("\(songs.count) songs").foregroundStyle(theme.palette.textMuted)
            }.padding(.horizontal, 4).padding(.bottom, 5)
            if songs.isEmpty {
                ContentUnavailableView("Nothing here yet", systemImage: tab == .favorites ? "heart" : "music.note", description: Text(tab == .favorites ? "Favorite songs appear here." : "Import some music to begin."))
                    .padding(.top, 60)
            }
            ForEach(songs) { SongRow(song: $0) }
        }
    }

    private func collectionGrid(items: [String], type: String, action: @escaping (String) -> Void) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
            ForEach(items, id: \.self) { item in
                let songs = type == "Album" ? (library.albums[item] ?? []) : (library.artists[item] ?? [])
                Button { action(item) } label: {
                    VStack(alignment: .leading, spacing: 9) {
                        ArtworkView(song: songs.first, size: 160, radius: type == "Artist" ? 80 : 28)
                            .frame(maxWidth: .infinity)
                        Text(item).font(.system(.headline, design: .rounded, weight: .bold)).lineLimit(1)
                        Text("\(songs.count) songs").font(.system(.caption, design: .rounded)).foregroundStyle(theme.palette.textMuted)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.buttonStyle(.plain)
            }
        }
    }

    private var playlistGrid: some View {
        LazyVStack(spacing: 12) {
            Button { showsNewPlaylist = true } label: {
                Label("Create playlist", systemImage: "plus")
                    .font(.system(.headline, design: .rounded, weight: .bold))
                    .frame(maxWidth: .infinity).frame(height: 62)
                    .background(theme.palette.primaryContainer, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            }.buttonStyle(.plain)
            ForEach(library.playlists) { playlist in
                Button { path.append(.playlist(playlist.id)) } label: {
                    HStack(spacing: 16) {
                        Image(systemName: "music.note.list")
                            .font(.title.bold()).frame(width: 70, height: 70)
                            .background(theme.palette.surfaceHigh, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        VStack(alignment: .leading) {
                            Text(playlist.name).font(.system(.headline, design: .rounded, weight: .bold))
                            Text("\(playlist.songIDs.count) songs").foregroundStyle(theme.palette.textMuted)
                        }
                        Spacer(); Image(systemName: "chevron.right")
                    }.padding(12).vybeCard(theme.palette.surface, radius: 26)
                }.buttonStyle(.plain)
            }
        }
    }
}

