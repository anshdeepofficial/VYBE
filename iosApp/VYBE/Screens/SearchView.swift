import SwiftUI

struct SearchView: View {
    @Binding var path: [AppRoute]
    @State private var query = ""
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var theme: VYBEThemeStore

    private var matches: [Song] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return library.songs.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.artist.localizedCaseInsensitiveContains(query) ||
            $0.album.localizedCaseInsensitiveContains(query) ||
            $0.genre.localizedCaseInsensitiveContains(query)
        }
    }

    private var genres: [(String, String, Color)] {
        let symbols = ["guitars.fill", "waveform", "pianokeys", "music.mic", "sparkles", "headphones", "music.quarternote.3", "radio.fill"]
        let colors: [Color] = [.indigo, .pink, .orange, .cyan, .purple, .mint, .blue, .red]
        let names = Array(Set(library.songs.map(\.genre))).filter { $0 != "Unknown Genre" }.sorted()
        let displayed = names.isEmpty ? ["Pop", "Rock", "Electronic", "Hip-Hop", "Indie", "Acoustic", "Jazz", "Classical"] : names
        return displayed.enumerated().map { ($0.element, symbols[$0.offset % symbols.count], colors[$0.offset % colors.count]) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                Text("Search")
                    .font(.system(size: 50, weight: .bold, design: .rounded))
                    .padding(.horizontal, 20).padding(.top, 14)
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass").font(.title2.bold())
                    TextField("Songs, artists, albums and genres", text: $query)
                        .font(.system(.body, design: .rounded, weight: .medium))
                        .textInputAutocapitalization(.never)
                    if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") } }
                }
                .padding(.horizontal, 18).frame(height: 62)
                .vybeCard(theme.palette.surfaceHigh, radius: 28)
                .padding(.horizontal, 18)

                if query.isEmpty { genreGrid } else { results }
            }
        }
        .foregroundStyle(theme.palette.text)
        .background(theme.palette.background)
        .scrollDismissesKeyboard(.interactively)
    }

    private var results: some View {
        LazyVStack(spacing: 10) {
            if matches.isEmpty {
                ContentUnavailableView("No matches", systemImage: "music.note.slash", description: Text("Try another song, artist, album or genre."))
                    .foregroundStyle(theme.palette.textMuted).padding(.top, 80)
            } else {
                ForEach(matches) { SongRow(song: $0) }
            }
        }.padding(.horizontal, 14)
    }

    private var genreGrid: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Browse genres").font(.system(.title2, design: .rounded, weight: .bold)).padding(.horizontal, 20)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(Array(genres.enumerated()), id: \.offset) { _, item in
                    let genre = item.0
                    let symbol = item.1
                    let color = item.2
                    Button { query = genre } label: {
                        ZStack(alignment: .bottomLeading) {
                            LinearGradient(colors: [color.opacity(0.9), color.opacity(0.46)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            Image(systemName: symbol).font(.system(size: 46, weight: .bold)).rotationEffect(.degrees(12)).offset(x: 92, y: -30).opacity(0.48)
                            Text(genre).font(.system(.title3, design: .rounded, weight: .bold)).padding(18)
                        }
                        .frame(height: 122).clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                        .foregroundStyle(.white)
                    }.buttonStyle(.plain)
                }
            }.padding(.horizontal, 14)
        }
    }
}
