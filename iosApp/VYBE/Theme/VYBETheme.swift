import SwiftUI
import UIKit

struct VYBEPalette: Equatable {
    let background: Color
    let surface: Color
    let surfaceHigh: Color
    let primary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let text: Color
    let textMuted: Color

    static let dark = VYBEPalette(
        background: Color(red: 0.035, green: 0.047, blue: 0.075),
        surface: Color(red: 0.065, green: 0.075, blue: 0.105),
        surfaceHigh: Color(red: 0.10, green: 0.11, blue: 0.15),
        primary: Color(red: 0.64, green: 0.73, blue: 1.0),
        primaryContainer: Color(red: 0.27, green: 0.30, blue: 0.48),
        onPrimaryContainer: Color(red: 0.84, green: 0.87, blue: 1.0),
        text: Color(red: 0.91, green: 0.92, blue: 0.98),
        textMuted: Color(red: 0.66, green: 0.67, blue: 0.74)
    )

    static let light = VYBEPalette(
        background: Color(red: 0.97, green: 0.97, blue: 1.0),
        surface: .white,
        surfaceHigh: Color(red: 0.91, green: 0.91, blue: 0.96),
        primary: Color(red: 0.25, green: 0.36, blue: 0.72),
        primaryContainer: Color(red: 0.82, green: 0.85, blue: 1.0),
        onPrimaryContainer: Color(red: 0.08, green: 0.13, blue: 0.32),
        text: Color(red: 0.08, green: 0.09, blue: 0.13),
        textMuted: Color(red: 0.34, green: 0.35, blue: 0.41)
    )

    static func player(seed: UIColor, dark: Bool) -> VYBEPalette {
        var hue: CGFloat = 0
        var saturation: CGFloat = 0
        var brightness: CGFloat = 0
        var alpha: CGFloat = 0
        seed.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha)
        let chroma = max(saturation, 0.28)
        if dark {
            return VYBEPalette(
                background: Color(hue: Double(hue), saturation: Double(chroma * 0.58), brightness: 0.30),
                surface: Color(hue: Double(hue), saturation: Double(chroma * 0.64), brightness: 0.23),
                surfaceHigh: Color(hue: Double(hue), saturation: Double(chroma * 0.55), brightness: 0.35),
                primary: Color(hue: Double(hue), saturation: Double(chroma * 0.45), brightness: 0.94),
                primaryContainer: Color(hue: Double(hue), saturation: Double(chroma * 0.55), brightness: 0.46),
                onPrimaryContainer: .white.opacity(0.92),
                text: .white.opacity(0.94),
                textMuted: .white.opacity(0.66)
            )
        }
        return .light
    }
}

@MainActor
final class VYBEThemeStore: ObservableObject {
    enum Mode: String, CaseIterable, Identifiable {
        case system = "System"
        case light = "Light"
        case dark = "Dark"
        case amoled = "AMOLED"
        var id: String { rawValue }
    }

    @Published var mode: Mode { didSet { defaults.set(mode.rawValue, forKey: "themeMode") } }
    @Published var artworkPlayerTheme: Bool { didSet { defaults.set(artworkPlayerTheme, forKey: "artworkPlayerTheme") } }
    @Published var showRecentlyPlayed: Bool { didSet { defaults.set(showRecentlyPlayed, forKey: "showRecentlyPlayed") } }
    @Published var showYourMix: Bool { didSet { defaults.set(showYourMix, forKey: "showYourMix") } }
    @Published var compactLibraryHeader: Bool { didSet { defaults.set(compactLibraryHeader, forKey: "compactLibraryHeader") } }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        mode = Mode(rawValue: defaults.string(forKey: "themeMode") ?? "") ?? .system
        artworkPlayerTheme = defaults.object(forKey: "artworkPlayerTheme") as? Bool ?? true
        showRecentlyPlayed = defaults.object(forKey: "showRecentlyPlayed") as? Bool ?? true
        showYourMix = defaults.object(forKey: "showYourMix") as? Bool ?? true
        compactLibraryHeader = defaults.object(forKey: "compactLibraryHeader") as? Bool ?? false
    }

    var colorScheme: ColorScheme? {
        switch mode {
        case .system: nil
        case .light: .light
        case .dark, .amoled: .dark
        }
    }

    var isDark: Bool { mode != .light }

    var palette: VYBEPalette {
        if mode == .amoled {
            let base = VYBEPalette.dark
            return VYBEPalette(
                background: .black,
                surface: Color(white: 0.035),
                surfaceHigh: Color(white: 0.075),
                primary: base.primary,
                primaryContainer: base.primaryContainer,
                onPrimaryContainer: base.onPrimaryContainer,
                text: base.text,
                textMuted: base.textMuted
            )
        }
        return mode == .light ? .light : .dark
    }
}

extension View {
    func vybeCard(_ color: Color, radius: CGFloat = 28) -> some View {
        self.background(color, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}
