import SwiftUI
import CoreImage

struct NowPlayingView: View {
    @Binding var isPresented: Bool
    @State private var showsLyrics = false
    @State private var showsQueue = false
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var theme: VYBEThemeStore

    private var palette: VYBEPalette {
        guard theme.artworkPlayerTheme, let image = library.artworkImage(for: player.currentSong) else { return theme.palette }
        return .player(seed: image.averageColor ?? .systemIndigo, dark: theme.isDark)
    }

    var body: some View {
        ZStack {
            palette.background.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                Spacer(minLength: 12)
                ArtworkView(song: player.currentSong, size: min(UIScreen.main.bounds.width - 72, 390), radius: 42)
                    .shadow(color: .black.opacity(0.25), radius: 30, y: 18)
                Spacer(minLength: 20)
                metadata
                seekBar
                transport
                secondaryControls
                Spacer(minLength: 18)
            }.padding(.horizontal, 24)
        }
        .foregroundStyle(palette.text)
        .sheet(isPresented: $showsLyrics) { LyricsView() }
        .sheet(isPresented: $showsQueue) { QueueView() }
    }

    private var header: some View {
        HStack(spacing: 14) {
            Button { isPresented = false } label: {
                Image(systemName: "chevron.down").font(.title2.bold()).frame(width: 52, height: 52)
                    .background(.black.opacity(0.22), in: Circle())
            }
            Text("Now Playing").font(.system(.title3, design: .rounded, weight: .bold))
            Spacer()
            Button { showsLyrics = true } label: {
                Image(systemName: "quote.bubble.fill").font(.title2).frame(width: 52, height: 52)
                    .background(.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            Button { showsQueue = true } label: {
                Image(systemName: "text.line.last.and.arrowtriangle.forward").font(.title2).frame(width: 52, height: 52)
                    .background(.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }.padding(.top, 8)
    }

    private var metadata: some View {
        VStack(spacing: 6) {
            Text(player.currentSong?.title ?? "Nothing playing")
                .font(.system(size: 28, weight: .bold, design: .rounded)).lineLimit(1).minimumScaleFactor(0.65)
            Text(player.currentSong?.artist ?? "VYBE")
                .font(.system(.title3, design: .rounded, weight: .medium)).foregroundStyle(palette.textMuted).lineLimit(1)
        }.padding(.horizontal, 8)
    }

    private var seekBar: some View {
        VStack(spacing: 8) {
            WavyProgressSlider(value: Binding(get: { player.currentTime }, set: player.seek), range: 0...max(player.duration, 1), tint: palette.primary)
                .frame(height: 32)
            HStack {
                Text(formatTime(player.currentTime)); Spacer(); Text(formatTime(player.duration))
            }.font(.system(.caption, design: .rounded, weight: .semibold)).foregroundStyle(palette.textMuted)
        }.padding(.top, 18)
    }

    private var transport: some View {
        HStack(spacing: 15) {
            playerButton("backward.end.fill", size: 76, action: player.previous)
            Button(action: player.playOrPause) {
                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 34, weight: .bold))
                    .frame(width: 96, height: 96)
                    .background(palette.primary, in: RoundedRectangle(cornerRadius: 32, style: .continuous))
                    .foregroundStyle(palette.onPrimaryContainer)
            }.buttonStyle(.plain)
            playerButton("forward.end.fill", size: 76, action: player.next)
        }.padding(.top, 10)
    }

    private var secondaryControls: some View {
        HStack(spacing: 0) {
            Button(action: player.toggleShuffle) {
                Image(systemName: "shuffle").frame(maxWidth: .infinity).frame(height: 58)
                    .background(player.isShuffled ? palette.primaryContainer : .clear)
            }
            Button(action: player.cycleRepeat) {
                Image(systemName: player.repeatMode == .one ? "repeat.1" : "repeat")
                    .frame(maxWidth: .infinity).frame(height: 58)
                    .background(player.repeatMode != .off ? palette.primaryContainer : .clear)
            }
            Button {
                if let song = player.currentSong { library.toggleFavorite(song) }
            } label: {
                Image(systemName: player.currentSong.map { library.favorites.contains($0.id) } == true ? "heart.fill" : "heart")
                    .frame(maxWidth: .infinity).frame(height: 58)
            }
        }
        .font(.title2.bold())
        .background(.black.opacity(0.20), in: Capsule())
        .clipShape(Capsule())
        .padding(.top, 18)
    }

    private func playerButton(_ symbol: String, size: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 25, weight: .bold)).frame(width: size, height: size)
                .background(.white.opacity(0.10), in: Circle())
        }.buttonStyle(.plain)
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        return String(format: "%d:%02d", Int(seconds) / 60, Int(seconds) % 60)
    }
}

private struct WavyProgressSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let tint: Color

    var body: some View {
        GeometryReader { proxy in
            let fraction = min(max((value - range.lowerBound) / max(range.upperBound - range.lowerBound, 1), 0), 1)
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.17)).frame(height: 6)
                WaveShape()
                    .stroke(tint, style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
                    .frame(width: max(1, proxy.size.width * fraction), height: 24)
                    .clipped()
                Circle().fill(tint).frame(width: 22, height: 22).offset(x: max(0, proxy.size.width * fraction - 11))
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { gesture in
                let x = min(max(gesture.location.x, 0), proxy.size.width)
                value = range.lowerBound + (x / max(proxy.size.width, 1)) * (range.upperBound - range.lowerBound)
            })
        }
    }
}

private struct WaveShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.midY))
        let wavelength: CGFloat = 28
        var x: CGFloat = 0
        while x < rect.maxX {
            path.addCurve(
                to: CGPoint(x: min(x + wavelength, rect.maxX), y: rect.midY),
                control1: CGPoint(x: x + wavelength * 0.25, y: rect.midY - 7),
                control2: CGPoint(x: x + wavelength * 0.75, y: rect.midY + 7)
            )
            x += wavelength
        }
        return path
    }
}

private extension UIImage {
    var averageColor: UIColor? {
        guard let input = CIImage(image: self) else { return nil }
        let extent = input.extent
        let filter = CIFilter(name: "CIAreaAverage", parameters: [kCIInputImageKey: input, kCIInputExtentKey: CIVector(cgRect: extent)])
        guard let output = filter?.outputImage else { return nil }
        var bitmap = [UInt8](repeating: 0, count: 4)
        CIContext(options: [.workingColorSpace: NSNull()]).render(output, toBitmap: &bitmap, rowBytes: 4, bounds: CGRect(x: 0, y: 0, width: 1, height: 1), format: .RGBA8, colorSpace: nil)
        return UIColor(red: CGFloat(bitmap[0]) / 255, green: CGFloat(bitmap[1]) / 255, blue: CGFloat(bitmap[2]) / 255, alpha: 1)
    }
}
