import SwiftUI

struct CollectionDetailView: View {
    let title: String
    let subtitle: String
    let songs: [Song]
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ArtworkView(song: songs.first, size: 250, radius: subtitle == "Artist" ? 125 : 48)
                    .shadow(color: .black.opacity(0.22), radius: 22, y: 12).padding(.top, 16)
                Text(title).font(.system(size: 35, weight: .bold, design: .rounded)).multilineTextAlignment(.center)
                Text("\(subtitle) • \(songs.count) songs")
                    .font(.system(.subheadline, design: .rounded, weight: .medium)).foregroundStyle(theme.palette.textMuted)
                HStack(spacing: 12) {
                    Button { if let first = songs.first { player.play(first, in: songs) } } label: {
                        Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity).frame(height: 56)
                            .background(theme.palette.primary, in: Capsule()).foregroundStyle(theme.palette.onPrimaryContainer)
                    }
                    Button { let shuffled = songs.shuffled(); if let first = shuffled.first { player.play(first, in: shuffled) } } label: {
                        Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity).frame(height: 56)
                            .background(theme.palette.primaryContainer, in: Capsule())
                    }
                }.font(.system(.headline, design: .rounded, weight: .bold)).padding(.vertical, 10)
                ForEach(songs) { SongRow(song: $0) }
            }.padding(.horizontal, 14).padding(.bottom, 36)
        }
        .foregroundStyle(theme.palette.text)
        .background(LinearGradient(colors: [theme.palette.primaryContainer.opacity(0.46), theme.palette.background], startPoint: .top, endPoint: .center))
        .navigationBarTitleDisplayMode(.inline)
    }
}

