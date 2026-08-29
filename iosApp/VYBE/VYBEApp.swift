import SwiftUI

@main
struct VYBEApp: App {
    @StateObject private var library = MusicLibrary()
    @StateObject private var player = AudioPlayer()
    @StateObject private var theme = VYBEThemeStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(library)
                .environmentObject(player)
                .environmentObject(theme)
                .preferredColorScheme(theme.colorScheme)
                .tint(theme.palette.primary)
                .task {
                    player.connect(to: library)
                    await library.load()
                }
        }
    }
}

