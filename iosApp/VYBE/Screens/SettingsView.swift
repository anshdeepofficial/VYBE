import SwiftUI

struct SettingsView: View {
    @Binding var showsImporter: Bool
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack {
                    Image("VYBELogo").resizable().scaledToFit().frame(width: 140, height: 74)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    VStack(alignment: .leading) {
                        Text("VYBE").font(.system(.title, design: .rounded, weight: .bold))
                        Text("Music, your way.").foregroundStyle(theme.palette.textMuted)
                    }
                }
                settingsSection("Library") {
                    settingsButton("Import music", symbol: "square.and.arrow.down") { showsImporter = true }
                    settingsValue("Songs", symbol: "music.note", value: "\(library.songs.count)")
                    settingsValue("Albums", symbol: "square.stack", value: "\(library.albums.count)")
                    settingsValue("Artists", symbol: "person.2", value: "\(library.artists.count)")
                }
                settingsSection("Appearance") {
                    HStack {
                        Label("Theme", systemImage: "paintpalette.fill")
                        Spacer()
                        Picker("Theme", selection: $theme.mode) {
                            ForEach(VYBEThemeStore.Mode.allCases) { Text($0.rawValue).tag($0) }
                        }.labelsHidden()
                    }.settingsRow()
                    Toggle("Artwork player colors", isOn: $theme.artworkPlayerTheme).settingsRow(symbol: "photo.fill")
                    Toggle("Compact library title", isOn: $theme.compactLibraryHeader).settingsRow(symbol: "textformat.size.smaller")
                }
                settingsSection("Home") {
                    Toggle("Show Your Mix", isOn: $theme.showYourMix).settingsRow(symbol: "wand.and.stars")
                    Toggle("Show recently played", isOn: $theme.showRecentlyPlayed).settingsRow(symbol: "clock.arrow.circlepath")
                }
                settingsSection("Playback") {
                    settingsValue("Audio engine", symbol: "waveform", value: "AVFoundation")
                    settingsValue("Background audio", symbol: "iphone.and.arrow.forward", value: "Enabled")
                    settingsValue("AirPlay", symbol: "airplayaudio", value: "Available")
                }
                settingsSection("About") {
                    settingsValue("Version", symbol: "info.circle", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0")
                    Link(destination: URL(string: "https://github.com/anshdeepofficial/VYBE")!) {
                        HStack { Label("Source code", systemImage: "chevron.left.forwardslash.chevron.right"); Spacer(); Image(systemName: "arrow.up.right") }.settingsRow()
                    }
                    Text("The iPhone app uses the new VYBE logo supplied by the project owner. Android and iPhone sources remain separate and ship together from one release workflow.")
                        .font(.system(.footnote, design: .rounded)).foregroundStyle(theme.palette.textMuted).padding(6)
                }
            }.padding(18).padding(.bottom, 30)
        }
        .foregroundStyle(theme.palette.text).background(theme.palette.background)
        .navigationTitle("Settings").navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder private func settingsSection<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.uppercased()).font(.system(.caption, design: .rounded, weight: .bold)).foregroundStyle(theme.palette.primary).padding(.leading, 8)
            VStack(spacing: 2) { content() }.padding(8).vybeCard(theme.palette.surface, radius: 28)
        }
    }

    private func settingsButton(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { HStack { Label(title, systemImage: symbol); Spacer(); Image(systemName: "chevron.right") }.settingsRow() }.buttonStyle(.plain)
    }

    private func settingsValue(_ title: String, symbol: String, value: String) -> some View {
        HStack { Label(title, systemImage: symbol); Spacer(); Text(value).foregroundStyle(theme.palette.textMuted) }.settingsRow()
    }
}

private extension View {
    func settingsRow(symbol: String? = nil) -> some View {
        Group {
            if let symbol { HStack { Image(systemName: symbol).frame(width: 24); self } } else { self }
        }
        .font(.system(.body, design: .rounded, weight: .medium))
        .frame(minHeight: 48).padding(.horizontal, 10)
    }
}

