import SwiftUI

struct HomeView: View {
    @Binding var path: [AppRoute]
    @Binding var showsImporter: Bool
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    private var mix: [Song] {
        let source = library.recentlyPlayed.isEmpty ? library.songs : library.recentlyPlayed
        return Array(source.prefix(12))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 28) {
                topBar
                if library.songs.isEmpty {
                    EmptyLibraryView(presented: $showsImporter)
                } else {
                    if theme.showYourMix { yourMix }
                    if theme.showRecentlyPlayed { recentlyPlayed }
                    albums
                }
            }.padding(.bottom, 30)
        }
        .scrollIndicators(.hidden)
        .background(theme.palette.background)
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            VYBELogo(height: 42)
            Spacer()
            CircleIconButton(symbol: "chart.bar.xaxis") { path.append(.stats) }
            CircleIconButton(symbol: "gearshape.fill") { path.append(.settings) }
        }.padding(.horizontal, 18).padding(.top, 10)
    }

    private var yourMix: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: -5) {
                    Text("Your")
                    Text("Mix")
                }
                .font(.system(size: 70, weight: .bold, design: .rounded))
                .minimumScaleFactor(0.78)
                Spacer()
                Button { if let first = mix.first { player.play(first, in: mix) } } label: {
                    Image(systemName: "play.fill")
                        .font(.system(size: 34, weight: .bold))
                        .frame(width: 108, height: 108)
                        .background(theme.palette.primary, in: Circle())
                        .foregroundStyle(theme.palette.onPrimaryContainer)
                }.buttonStyle(.plain)
            }
            Text("Today's Mix for you")
                .font(.system(.title3, design: .rounded, weight: .medium))
                .foregroundStyle(theme.palette.textMuted)

            ZStack {
                if mix.indices.contains(0) {
                    ArtworkView(song: mix[0], size: 190, radius: 70).rotationEffect(.degrees(18)).offset(x: 30)
                }
                if mix.indices.contains(1) {
                    ArtworkView(song: mix[1], size: 86, radius: 43).offset(x: -125, y: -45)
                }
                if mix.indices.contains(2) {
                    ArtworkView(song: mix[2], size: 80, radius: 40).offset(x: 125, y: 70)
                }
            }.frame(maxWidth: .infinity).frame(height: 265)
        }
        .foregroundStyle(theme.palette.text)
        .padding(.horizontal, 26)
    }

    private var recentlyPlayed: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("Recently played")
            ScrollView(.horizontal) {
                LazyHStack(spacing: 14) {
                    ForEach(Array((library.recentlyPlayed.isEmpty ? library.songs : library.recentlyPlayed).prefix(12))) { song in
                        Button { player.play(song, in: library.songs) } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                ArtworkView(song: song, size: 145, radius: 32)
                                Text(song.title).font(.system(.headline, design: .rounded, weight: .bold)).lineLimit(1)
                                Text(song.artist).font(.system(.caption, design: .rounded)).foregroundStyle(theme.palette.textMuted).lineLimit(1)
                            }.frame(width: 145, alignment: .leading)
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 18)
            }.scrollIndicators(.hidden)
        }.foregroundStyle(theme.palette.text)
    }

    private var albums: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("Albums")
            ScrollView(.horizontal) {
                LazyHStack(spacing: 14) {
                    ForEach(library.albums.keys.sorted(), id: \.self) { album in
                        let songs = library.albums[album] ?? []
                        Button { path.append(.album(album)) } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                ArtworkView(song: songs.first, size: 166, radius: 30)
                                Text(album).font(.system(.headline, design: .rounded, weight: .bold)).lineLimit(1)
                                Text(songs.first?.artist ?? "Unknown Artist").font(.system(.caption, design: .rounded)).foregroundStyle(theme.palette.textMuted).lineLimit(1)
                            }.frame(width: 166, alignment: .leading)
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 18)
            }.scrollIndicators(.hidden)
        }.foregroundStyle(theme.palette.text)
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(.system(.title2, design: .rounded, weight: .bold)).padding(.horizontal, 20)
    }
}

