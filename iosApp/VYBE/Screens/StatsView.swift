import SwiftUI

struct StatsView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var theme: VYBEThemeStore

    private var topSongs: [Song] { library.songs.sorted { $0.playCount > $1.playCount }.prefix(5).map { $0 } }
    private var topArtist: String { Dictionary(grouping: library.songs, by: \.artist).max { $0.value.reduce(0) { $0 + $1.playCount } < $1.value.reduce(0) { $0 + $1.playCount } }?.key ?? "—" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Your listening, at a glance.")
                    .font(.system(size: 38, weight: .bold, design: .rounded))
                HStack(spacing: 12) {
                    metric("Plays", value: "\(library.songs.reduce(0) { $0 + $1.playCount })", symbol: "play.fill")
                    metric("Time", value: formatDuration(library.totalListeningTime), symbol: "clock.fill")
                }
                metric("Top artist", value: topArtist, symbol: "person.wave.2.fill")
                Text("Top songs").font(.system(.title2, design: .rounded, weight: .bold)).padding(.top, 10)
                if topSongs.allSatisfy({ $0.playCount == 0 }) {
                    ContentUnavailableView("No listening history yet", systemImage: "chart.bar", description: Text("Play some music and your VYBE stats will appear here."))
                        .foregroundStyle(theme.palette.textMuted).padding(.top, 40)
                } else {
                    ForEach(topSongs) { song in
                        HStack(spacing: 12) {
                            ArtworkView(song: song, size: 58, radius: 14)
                            VStack(alignment: .leading) {
                                Text(song.title).font(.system(.headline, design: .rounded, weight: .bold)).lineLimit(1)
                                Text(song.artist).foregroundStyle(theme.palette.textMuted).lineLimit(1)
                            }
                            Spacer(); Text("\(song.playCount)").font(.title3.bold()).foregroundStyle(theme.palette.primary)
                        }.padding(10).vybeCard(theme.palette.surfaceHigh, radius: 22)
                    }
                }
            }.padding(18).padding(.bottom, 30)
        }
        .foregroundStyle(theme.palette.text).background(theme.palette.background)
        .navigationTitle("Stats")
    }

    private func metric(_ title: String, value: String, symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: symbol).font(.title2).foregroundStyle(theme.palette.primary)
            Text(value).font(.system(.title2, design: .rounded, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
            Text(title).font(.system(.subheadline, design: .rounded)).foregroundStyle(theme.palette.textMuted)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(18).vybeCard(theme.palette.surfaceHigh, radius: 26)
    }

    private func formatDuration(_ seconds: Double) -> String {
        let hours = Int(seconds) / 3600
        return hours > 0 ? "\(hours)h" : "\(Int(seconds) / 60)m"
    }
}
