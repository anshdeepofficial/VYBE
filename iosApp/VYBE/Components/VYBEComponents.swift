import SwiftUI
import UIKit

struct VYBELogo: View {
    var height: CGFloat = 38

    var body: some View {
        Image("VYBEWordmark")
            .resizable()
            .scaledToFit()
            .frame(width: height * 2.18, height: height)
            .clipShape(RoundedRectangle(cornerRadius: height * 0.16, style: .continuous))
            .accessibilityLabel("VYBE")
    }
}

struct ArtworkView: View {
    let song: Song?
    var size: CGFloat
    var radius: CGFloat = 20
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        Group {
            if let image = library.artworkImage(for: song) {
                Image(uiImage: image).resizable().scaledToFill()
            } else if let artworkUrl = song?.artworkUrl, let url = URL(string: artworkUrl) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        .accessibilityHidden(true)
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(
                colors: [theme.palette.primaryContainer, theme.palette.primary.opacity(0.68)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Image(systemName: "music.note")
                .font(.system(size: size * 0.28, weight: .semibold, design: .rounded))
                .foregroundStyle(theme.palette.onPrimaryContainer)
        }
    }
}

struct CircleIconButton: View {
    let symbol: String
    var size: CGFloat = 48
    var selected = false
    var action: () -> Void
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: size * 0.42, weight: .semibold, design: .rounded))
                .frame(width: size, height: size)
                .foregroundStyle(selected ? theme.palette.onPrimaryContainer : theme.palette.text)
                .background(selected ? theme.palette.primaryContainer : theme.palette.surfaceHigh, in: Circle())
        }
        .buttonStyle(.plain)
    }
}

struct SongRow: View {
    let song: Song
    var showMenu = true
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        HStack(spacing: 10) {
            Button { player.play(song, in: library.songs) } label: {
                HStack(spacing: 15) {
                ArtworkView(song: song, size: 64, radius: 14)
                VStack(alignment: .leading, spacing: 5) {
                    Text(song.title)
                        .font(.system(.headline, design: .rounded, weight: .semibold))
                        .foregroundStyle(theme.palette.text)
                        .lineLimit(1)
                    Text(song.artist)
                        .font(.system(.subheadline, design: .rounded))
                        .foregroundStyle(theme.palette.textMuted)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                if player.currentSong?.id == song.id && player.isPlaying {
                    Image(systemName: "waveform")
                        .symbolEffect(.variableColor.iterative, options: .repeating)
                        .foregroundStyle(theme.palette.primary)
                }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }.buttonStyle(.plain)
            if showMenu {
                Menu {
                        Button(library.favorites.contains(song.id) ? "Remove from favorites" : "Add to favorites",
                               systemImage: library.favorites.contains(song.id) ? "heart.slash" : "heart") {
                            library.toggleFavorite(song)
                        }
                        ForEach(library.playlists) { playlist in
                            Button("Add to \(playlist.name)", systemImage: "text.badge.plus") {
                                library.add(song, to: playlist.id)
                            }
                        }
                        Button("Delete from VYBE", systemImage: "trash", role: .destructive) { library.delete(song) }
                } label: {
                        Image(systemName: "ellipsis")
                            .font(.title3.bold())
                            .frame(width: 44, height: 44)
                            .background(theme.palette.surfaceHigh, in: Circle())
                            .foregroundStyle(theme.palette.textMuted)
                }
            }
        }
        .padding(12)
        .vybeCard(theme.palette.surfaceHigh, radius: 26)
    }
}

struct MiniPlayer: View {
    @Binding var showsNowPlaying: Bool
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        if let song = player.currentSong {
            HStack(spacing: 12) {
                Button { showsNowPlaying = true } label: {
                    HStack(spacing: 12) {
                    ArtworkView(song: song, size: 54, radius: 15)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(song.title).font(.system(.headline, design: .rounded, weight: .bold)).lineLimit(1)
                        Text(song.artist).font(.system(.subheadline, design: .rounded)).opacity(0.72).lineLimit(1)
                    }
                    }
                }.buttonStyle(.plain)
                Spacer(minLength: 4)
                    Button(action: player.playOrPause) {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title3.bold()).frame(width: 50, height: 50)
                            .background(theme.palette.primary, in: Circle())
                            .foregroundStyle(theme.palette.onPrimaryContainer)
                    }
                    Button(action: player.next) {
                        Image(systemName: "forward.end.fill")
                            .font(.title3).frame(width: 46, height: 46)
                            .background(.white.opacity(0.09), in: Circle())
                    }
            }
            .padding(9)
            .foregroundStyle(theme.palette.text)
            .background(theme.palette.primaryContainer.opacity(0.94), in: RoundedRectangle(cornerRadius: 27, style: .continuous))
            .overlay(alignment: .bottomLeading) {
                    GeometryReader { proxy in
                        Capsule()
                            .fill(theme.palette.primary)
                            .frame(width: proxy.size.width * min(max(player.currentTime / max(player.duration, 1), 0), 1), height: 2)
                    }.frame(height: 2)
            }
            .padding(.horizontal, 10)
        }
    }
}

struct VYBETabBar: View {
    @Binding var selection: RootTab
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        HStack(spacing: 4) {
            ForEach(RootTab.allCases) { tab in
                Button { selection = tab } label: {
                    VStack(spacing: 6) {
                        Image(systemName: tab.symbol)
                            .font(.system(size: 21, weight: .semibold))
                            .frame(width: 60, height: 35)
                            .background(selection == tab ? theme.palette.primaryContainer : .clear, in: Capsule())
                        Text(tab.rawValue).font(.system(.caption, design: .rounded, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(selection == tab ? theme.palette.onPrimaryContainer : theme.palette.textMuted)
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 8).padding(.top, 8).padding(.bottom, 10)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 29, style: .continuous))
        .environment(\.colorScheme, theme.isDark ? .dark : .light)
        .padding(.horizontal, 10)
    }
}

struct ImportAudioButton: View {
    @Binding var presented: Bool
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        Button { presented = true } label: {
            Label("Import music", systemImage: "square.and.arrow.down")
                .font(.system(.headline, design: .rounded, weight: .bold))
                .padding(.horizontal, 22).frame(height: 54)
                .background(theme.palette.primary, in: Capsule())
                .foregroundStyle(theme.palette.onPrimaryContainer)
        }.buttonStyle(.plain)
    }
}

struct EmptyLibraryView: View {
    @Binding var presented: Bool
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "music.note.house.fill")
                .font(.system(size: 48)).foregroundStyle(theme.palette.primary)
                .frame(width: 108, height: 108)
                .background(theme.palette.primaryContainer, in: RoundedRectangle(cornerRadius: 38, style: .continuous))
            Text("Your music belongs here")
                .font(.system(.title2, design: .rounded, weight: .bold))
            Text("Choose audio files from Files. VYBE copies them into its private library for reliable offline and background playback.")
                .font(.system(.body, design: .rounded)).foregroundStyle(theme.palette.textMuted)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            ImportAudioButton(presented: $presented)
        }
        .foregroundStyle(theme.palette.text)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
    }
}
