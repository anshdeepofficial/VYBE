import SwiftUI

struct SetupView: View {
    @Binding var isComplete: Bool
    @Binding var showsImporter: Bool
    @State private var page = 0
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var theme: VYBEThemeStore

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VYBELogo(height: 44)
                Spacer()
                Text("\(page + 1) / 3")
                    .font(.system(.subheadline, design: .rounded, weight: .bold))
                    .foregroundStyle(theme.palette.textMuted)
            }.padding(24)

            TabView(selection: $page) {
                welcome.tag(0)
                importPage.tag(1)
                appearance.tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            HStack(spacing: 12) {
                if page > 0 {
                    Button("Back") { withAnimation { page -= 1 } }
                        .buttonStyle(VYBESecondaryButtonStyle())
                }
                Button(page == 2 ? "Start listening" : "Continue") {
                    if page == 2 { isComplete = true } else { withAnimation { page += 1 } }
                }
                .buttonStyle(VYBEPrimaryButtonStyle())
            }.padding(24)
        }
        .foregroundStyle(theme.palette.text)
        .background(theme.palette.background.ignoresSafeArea())
    }

    private var welcome: some View {
        VStack(spacing: 24) {
            Image("VYBELogo")
                .resizable().scaledToFit().frame(maxWidth: 330)
                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                .shadow(color: .black.opacity(0.2), radius: 25, y: 12)
            Text("Music, your way.")
                .font(.system(size: 42, weight: .bold, design: .rounded))
            Text("The expressive VYBE player is now built for iPhone, with offline music, background playback, favorites, playlists and lyrics.")
                .font(.system(.title3, design: .rounded)).foregroundStyle(theme.palette.textMuted)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
        }.padding(24)
    }

    private var importPage: some View {
        VStack(spacing: 26) {
            Image(systemName: "folder.badge.plus")
                .font(.system(size: 64, weight: .semibold))
                .foregroundStyle(theme.palette.onPrimaryContainer)
                .frame(width: 142, height: 142)
                .background(theme.palette.primaryContainer, in: RoundedRectangle(cornerRadius: 48, style: .continuous))
            Text("Bring your library")
                .font(.system(size: 38, weight: .bold, design: .rounded))
            Text("iPhone protects the filesystem, so VYBE uses the native Files picker. Select music from iCloud Drive, On My iPhone or a connected provider.")
                .font(.system(.title3, design: .rounded)).foregroundStyle(theme.palette.textMuted)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            ImportAudioButton(presented: $showsImporter)
            if library.isImporting { ProgressView("Importing…").tint(theme.palette.primary) }
        }.padding(24)
    }

    private var appearance: some View {
        VStack(spacing: 24) {
            Image(systemName: "paintpalette.fill")
                .font(.system(size: 58)).foregroundStyle(theme.palette.primary)
            Text("Make VYBE yours")
                .font(.system(size: 38, weight: .bold, design: .rounded))
            VStack(spacing: 10) {
                ForEach(VYBEThemeStore.Mode.allCases) { mode in
                    Button { theme.mode = mode } label: {
                        HStack {
                            Image(systemName: mode == .system ? "circle.lefthalf.filled" : mode == .light ? "sun.max.fill" : mode == .amoled ? "circle.fill" : "moon.stars.fill")
                            Text(mode.rawValue).font(.system(.headline, design: .rounded))
                            Spacer()
                            if theme.mode == mode { Image(systemName: "checkmark.circle.fill") }
                        }.padding(18).vybeCard(theme.mode == mode ? theme.palette.primaryContainer : theme.palette.surfaceHigh, radius: 22)
                    }.buttonStyle(.plain)
                }
            }
        }.padding(24)
    }
}

private struct VYBEPrimaryButtonStyle: ButtonStyle {
    @EnvironmentObject private var theme: VYBEThemeStore
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.headline, design: .rounded, weight: .bold))
            .frame(maxWidth: .infinity).frame(height: 58)
            .background(theme.palette.primary.opacity(configuration.isPressed ? 0.72 : 1), in: Capsule())
            .foregroundStyle(theme.palette.onPrimaryContainer)
    }
}

private struct VYBESecondaryButtonStyle: ButtonStyle {
    @EnvironmentObject private var theme: VYBEThemeStore
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.headline, design: .rounded, weight: .bold))
            .frame(width: 100, height: 58)
            .background(theme.palette.surfaceHigh, in: Capsule())
            .foregroundStyle(theme.palette.text)
    }
}

