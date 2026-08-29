import AVFoundation
import Combine
import MediaPlayer
import UIKit

@MainActor
final class AudioPlayer: ObservableObject {
    @Published private(set) var currentSong: Song?
    @Published private(set) var queue: [Song] = []
    @Published private(set) var queueIndex = 0
    @Published private(set) var isPlaying = false
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published var isShuffled = false
    @Published var repeatMode: RepeatMode = .off

    private let player = AVPlayer()
    private weak var library: MusicLibrary?
    private var timeObserver: Any?
    private var completionObserver: NSObjectProtocol?
    private var connected = false

    init() {
        configureAudioSession()
        configureRemoteCommands()
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            Task { @MainActor in
                self?.currentTime = max(0, time.seconds.isFinite ? time.seconds : 0)
            }
        }
        completionObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: nil,
            queue: .main
        ) { [weak self] _ in Task { @MainActor in self?.handleCompletion() } }
    }

    func connect(to library: MusicLibrary) {
        guard !connected else { return }
        self.library = library
        connected = true
    }

    func play(_ song: Song, in source: [Song]? = nil) {
        var nextQueue = source ?? queue
        if nextQueue.isEmpty || !nextQueue.contains(song) { nextQueue = source ?? [song] }
        if isShuffled, nextQueue.count > 1 {
            nextQueue.shuffle()
            nextQueue.removeAll { $0.id == song.id }
            nextQueue.insert(song, at: 0)
        }
        queue = nextQueue
        queueIndex = queue.firstIndex(of: song) ?? 0
        loadCurrent(autoplay: true)
    }

    func playOrPause() {
        guard currentSong != nil else {
            if let first = library?.songs.first { play(first, in: library?.songs) }
            return
        }
        if isPlaying { pause() } else { resume() }
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func resume() {
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func next() {
        guard !queue.isEmpty else { return }
        if queueIndex + 1 < queue.count { queueIndex += 1 }
        else if repeatMode == .all { queueIndex = 0 }
        else { pause(); return }
        loadCurrent(autoplay: true)
    }

    func previous() {
        if currentTime > 4 { seek(to: 0); return }
        guard !queue.isEmpty else { return }
        if queueIndex > 0 { queueIndex -= 1 }
        else if repeatMode == .all { queueIndex = queue.count - 1 }
        loadCurrent(autoplay: true)
    }

    func seek(to seconds: Double) {
        let safe = min(max(0, seconds), max(duration, 0))
        player.seek(to: CMTime(seconds: safe, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        currentTime = safe
        updateNowPlaying()
    }

    func cycleRepeat() {
        switch repeatMode {
        case .off: repeatMode = .all
        case .all: repeatMode = .one
        case .one: repeatMode = .off
        }
    }

    func toggleShuffle() { isShuffled.toggle() }

    private func loadCurrent(autoplay: Bool) {
        guard queue.indices.contains(queueIndex), let library else { return }
        let song = queue[queueIndex]
        currentSong = song
        currentTime = 0
        duration = song.duration
        player.replaceCurrentItem(with: AVPlayerItem(url: library.audioURL(for: song)))
        library.markPlayed(song)
        updateNowPlaying()
        if autoplay { resume() }
    }

    private func handleCompletion() {
        if repeatMode == .one { seek(to: 0); resume() } else { next() }
    }

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.allowAirPlay, .allowBluetoothA2DP])
            try session.setActive(true)
        } catch {
            assertionFailure("Unable to configure the audio session: \(error)")
        }
    }

    private func configureRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }
        commands.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }
        commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.playOrPause() }
            return .success
        }
        commands.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.next() }
            return .success
        }
        commands.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previous() }
            return .success
        }
        commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(to: event.positionTime) }
            return .success
        }
    }

    private func updateNowPlaying() {
        guard let song = currentSong else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: song.title,
            MPMediaItemPropertyArtist: song.artist,
            MPMediaItemPropertyAlbumTitle: song.album,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: queueIndex,
            MPNowPlayingInfoPropertyPlaybackQueueCount: queue.count
        ]
        if let image = library?.artworkImage(for: song) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
