import SwiftUI

struct SearchView: View {
    @Binding var path: [AppRoute]
    @State private var query = ""
    @State private var onlineResults: [Song] = []
    @State private var isSearching = false
    @State private var searchTask: Task<Void, Never>?
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore
    @ObservedObject private var online = OnlineMusicService.shared

    private var localMatches: [Song] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return library.songs.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.artist.localizedCaseInsensitiveContains(query) ||
            $0.album.localizedCaseInsensitiveContains(query) ||
            $0.genre.localizedCaseInsensitiveContains(query)
        }
    }

    private var displayResults: [Song] {
        if !onlineResults.isEmpty {
            return onlineResults
        }
        return localMatches
    }

    private let quickTags = [
        "Top Hits", "Latest Releases", "Punjabi Hits", "Bollywood", "Pop", "Hip-Hop", "Acoustic", "Chill"
    ]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                Text("Search")
                    .font(.system(size: 50, weight: .bold, design: .rounded))
                    .padding(.horizontal, 20)
                    .padding(.top, 14)

                searchBar

                if query.isEmpty {
                    quickTagsScroll
                    bestForYouSection
                    genreGrid
                } else {
                    resultsSection
                }
            }
        }
        .foregroundStyle(theme.palette.text)
        .background(theme.palette.background)
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: query) { _, newQuery in
            triggerSearch(query: newQuery)
        }
    }

    private var searchBar: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.title2.bold())
                .foregroundStyle(theme.palette.primary)
            TextField("Search songs, artists, and albums", text: $query)
                .font(.system(.body, design: .rounded, weight: .medium))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
            if isSearching {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(theme.palette.primary)
            } else if !query.isEmpty {
                Button {
                    query = ""
                    onlineResults = []
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(theme.palette.textMuted)
                }
            }
        }
        .padding(.horizontal, 18)
        .frame(height: 62)
        .vybeCard(theme.palette.surfaceHigh, radius: 28)
        .padding(.horizontal, 18)
    }

    private var quickTagsScroll: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(quickTags, id: \.self) { tag in
                    Button {
                        query = tag
                    } label: {
                        Text(tag)
                            .font(.system(.subheadline, design: .rounded, weight: .semibold))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(theme.palette.surfaceHigh, in: Capsule())
                            .foregroundStyle(theme.palette.text)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 18)
        }
        .scrollIndicators(.hidden)
    }

    private var bestForYouSection: some View {
        let bestSongs = !online.trendingTracks.isEmpty ? Array(online.trendingTracks.prefix(8)) : Array(library.recentlyPlayed.prefix(8))
        return Group {
            if !bestSongs.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Best for You")
                            .font(.system(.title2, design: .rounded, weight: .bold))
                        Spacer()
                    }
                    .padding(.horizontal, 20)

                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 12) {
                            ForEach(bestSongs) { song in
                                Button {
                                    player.play(song, in: bestSongs)
                                } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        ArtworkView(song: song, size: 120, radius: 22)
                                        Text(song.title)
                                            .font(.system(.subheadline, design: .rounded, weight: .semibold))
                                            .lineLimit(1)
                                        Text(song.artist)
                                            .font(.system(.caption, design: .rounded))
                                            .foregroundStyle(theme.palette.textMuted)
                                            .lineLimit(1)
                                    }
                                    .frame(width: 120, alignment: .leading)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 18)
                    }
                    .scrollIndicators(.hidden)
                }
            }
        }
    }

    private var resultsSection: some View {
        LazyVStack(spacing: 10) {
            if isSearching && displayResults.isEmpty {
                VStack(spacing: 14) {
                    ProgressView()
                    Text("Searching online catalog...")
                        .font(.system(.subheadline, design: .rounded))
                        .foregroundStyle(theme.palette.textMuted)
                }
                .padding(.top, 60)
            } else if displayResults.isEmpty {
                ContentUnavailableView(
                    "No matches",
                    systemImage: "music.note.slash",
                    description: Text("Try searching for another song, artist, or album.")
                )
                .foregroundStyle(theme.palette.textMuted)
                .padding(.top, 60)
            } else {
                ForEach(displayResults) { song in
                    Button {
                        player.play(song, in: displayResults)
                    } label: {
                        HStack(spacing: 14) {
                            ArtworkView(song: song, size: 60, radius: 14)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(song.title)
                                    .font(.system(.headline, design: .rounded, weight: .semibold))
                                    .lineLimit(1)
                                    .foregroundStyle(theme.palette.text)
                                Text(song.artist)
                                    .font(.system(.subheadline, design: .rounded))
                                    .lineLimit(1)
                                    .foregroundStyle(theme.palette.textMuted)
                            }
                            Spacer()
                            if player.currentSong?.id == song.id && player.isPlaying {
                                Image(systemName: "waveform")
                                    .symbolEffect(.variableColor.iterative, options: .repeating)
                                    .foregroundStyle(theme.palette.primary)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .vybeCard(theme.palette.surface, radius: 20)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 14)
    }

    private var genreGrid: some View {
        let genres: [(String, String, Color)] = [
            ("Pop", "music.quarternote.3", .pink),
            ("Hip-Hop", "waveform", .orange),
            ("Bollywood", "sparkles", .purple),
            ("Punjabi", "headphones", .indigo),
            ("Rock", "guitars.fill", .red),
            ("Acoustic", "pianokeys", .mint),
            ("Electronic", "radio.fill", .cyan),
            ("Chill", "music.mic", .blue)
        ]

        return VStack(alignment: .leading, spacing: 14) {
            Text("Browse genres")
                .font(.system(.title2, design: .rounded, weight: .bold))
                .padding(.horizontal, 20)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(genres, id: \.0) { item in
                    Button {
                        query = item.0
                    } label: {
                        ZStack(alignment: .bottomLeading) {
                            LinearGradient(
                                colors: [item.2.opacity(0.9), item.2.opacity(0.46)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            Image(systemName: item.1)
                                .font(.system(size: 46, weight: .bold))
                                .rotationEffect(.degrees(12))
                                .offset(x: 92, y: -30)
                                .opacity(0.48)
                            Text(item.0)
                                .font(.system(.title3, design: .rounded, weight: .bold))
                                .padding(18)
                        }
                        .frame(height: 110)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .foregroundStyle(.white)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
        }
    }

    private func triggerSearch(query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            onlineResults = []
            isSearching = false
            return
        }

        isSearching = true
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000) // 350ms debounce
            guard !Task.isCancelled else { return }
            let results = await online.search(query: trimmed)
            guard !Task.isCancelled else { return }
            self.onlineResults = results
            self.isSearching = false
        }
    }
}
