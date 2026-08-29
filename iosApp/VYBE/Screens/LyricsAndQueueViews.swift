import SwiftUI

struct LyricsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var synced = true
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    private var lines: [String] {
        let text = player.currentSong?.lyrics?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty ? ["Lyrics aren't embedded in this song.", "Add lyrics in a future metadata editor or import a track that includes them."] : text.components(separatedBy: .newlines).filter { !$0.isEmpty }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Picker("Lyrics style", selection: $synced) {
                        Text("Synced").tag(true); Text("Static").tag(false)
                    }.pickerStyle(.segmented).padding(.bottom, 18)
                    ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                        Text(line)
                            .font(.system(size: 29, weight: index == 0 ? .bold : .semibold, design: .rounded))
                            .foregroundStyle(index == 0 ? theme.palette.text : theme.palette.textMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 7)
                    }
                }.padding(22).padding(.bottom, 100)
            }
            .background(theme.palette.primaryContainer.opacity(0.48).ignoresSafeArea())
            .navigationTitle("Lyrics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.large])
    }
}

struct QueueView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(Array(player.queue.enumerated()), id: \.element.id) { index, song in
                        Button { player.play(song, in: player.queue); dismiss() } label: {
                            HStack(spacing: 12) {
                                ArtworkView(song: song, size: 54, radius: 13)
                                VStack(alignment: .leading) {
                                    Text(song.title).font(.system(.headline, design: .rounded, weight: .bold)).lineLimit(1)
                                    Text(song.artist).font(.system(.caption, design: .rounded)).foregroundStyle(theme.palette.textMuted)
                                }
                                Spacer()
                                if index == player.queueIndex { Image(systemName: "waveform").foregroundStyle(theme.palette.primary) }
                            }.padding(10).vybeCard(theme.palette.surfaceHigh, radius: 22)
                        }.buttonStyle(.plain)
                    }
                }.padding(14)
            }
            .foregroundStyle(theme.palette.text).background(theme.palette.background)
            .navigationTitle("Up next")
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Done") { dismiss() } } }
        }
    }
}

