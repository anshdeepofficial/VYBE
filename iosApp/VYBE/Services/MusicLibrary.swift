import AVFoundation
import Foundation
import UIKit

@MainActor
final class MusicLibrary: ObservableObject {
    @Published private(set) var songs: [Song] = []
    @Published private(set) var favorites: Set<UUID> = []
    @Published private(set) var playlists: [VYBEPlaylist] = []
    @Published var isImporting = false
    @Published var importMessage: String?

    private let fileManager = FileManager.default
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private var applicationSupportURL: URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("VYBE", isDirectory: true)
    }

    private var mediaURL: URL { applicationSupportURL.appendingPathComponent("Media", isDirectory: true) }
    private var artworkURL: URL { applicationSupportURL.appendingPathComponent("Artwork", isDirectory: true) }
    private var snapshotURL: URL { applicationSupportURL.appendingPathComponent("library.json") }

    var albums: [String: [Song]] { Dictionary(grouping: songs, by: \.album) }
    var artists: [String: [Song]] { Dictionary(grouping: songs, by: \.artist) }
    var recentlyPlayed: [Song] {
        songs.filter { $0.lastPlayed != nil }.sorted { ($0.lastPlayed ?? .distantPast) > ($1.lastPlayed ?? .distantPast) }
    }
    var cachedSongs: [Song] {
        Array((recentlyPlayed.isEmpty ? songs : recentlyPlayed).prefix(10))
    }
    var favoriteSongs: [Song] { songs.filter { favorites.contains($0.id) } }
    var totalListeningTime: Double { songs.reduce(0) { $0 + ($1.duration * Double($1.playCount)) } }

    func load() async {
        do {
            try prepareDirectories()
            guard fileManager.fileExists(atPath: snapshotURL.path) else { return }
            let data = try Data(contentsOf: snapshotURL)
            let snapshot = try decoder.decode(LibrarySnapshot.self, from: data)
            songs = snapshot.songs.filter { fileManager.fileExists(atPath: audioURL(for: $0).path) }
            favorites = snapshot.favorites.intersection(Set(songs.map(\.id)))
            playlists = snapshot.playlists
        } catch {
            importMessage = "The saved library could not be opened: \(error.localizedDescription)"
        }
    }

    func audioURL(for song: Song) -> URL { mediaURL.appendingPathComponent(song.fileName) }

    func artworkImage(for song: Song?) -> UIImage? {
        guard let file = song?.artworkFileName else { return nil }
        return UIImage(contentsOfFile: artworkURL.appendingPathComponent(file).path)
    }

    func importAudio(from urls: [URL]) async {
        guard !urls.isEmpty else { return }
        isImporting = true
        importMessage = nil
        defer { isImporting = false }

        do {
            try prepareDirectories()
            var added = 0
            for source in urls {
                let accessed = source.startAccessingSecurityScopedResource()
                defer { if accessed { source.stopAccessingSecurityScopedResource() } }

                let ext = source.pathExtension.isEmpty ? "m4a" : source.pathExtension.lowercased()
                let storedFile = "\(UUID().uuidString).\(ext)"
                let destination = mediaURL.appendingPathComponent(storedFile)
                try fileManager.copyItem(at: source, to: destination)
                var song = try await metadata(for: destination, originalName: source.deletingPathExtension().lastPathComponent)
                song.fileName = storedFile
                if let artworkData = try await embeddedArtwork(for: destination) {
                    let artworkFile = "\(song.id.uuidString).jpg"
                    try artworkData.write(to: artworkURL.appendingPathComponent(artworkFile), options: .atomic)
                    song.artworkFileName = artworkFile
                }
                songs.append(song)
                added += 1
            }
            songs.sort { $0.title.localizedStandardCompare($1.title) == .orderedAscending }
            try save()
            importMessage = "Imported \(added) \(added == 1 ? "song" : "songs")."
        } catch {
            importMessage = "Import failed: \(error.localizedDescription)"
        }
    }

    func toggleFavorite(_ song: Song) {
        if favorites.contains(song.id) { favorites.remove(song.id) } else { favorites.insert(song.id) }
        try? save()
    }

    func markPlayed(_ song: Song) {
        guard let index = songs.firstIndex(where: { $0.id == song.id }) else { return }
        songs[index].playCount += 1
        songs[index].lastPlayed = .now
        try? save()
    }

    func delete(_ song: Song) {
        try? fileManager.removeItem(at: audioURL(for: song))
        if let artwork = song.artworkFileName {
            try? fileManager.removeItem(at: artworkURL.appendingPathComponent(artwork))
        }
        songs.removeAll { $0.id == song.id }
        favorites.remove(song.id)
        for index in playlists.indices { playlists[index].songIDs.removeAll { $0 == song.id } }
        try? save()
    }

    func createPlaylist(name: String) {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        playlists.append(VYBEPlaylist(name: clean))
        try? save()
    }

    func add(_ song: Song, to playlistID: UUID) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        if !playlists[index].songIDs.contains(song.id) { playlists[index].songIDs.append(song.id) }
        try? save()
    }

    func songs(in playlist: VYBEPlaylist) -> [Song] {
        let lookup = Dictionary(uniqueKeysWithValues: songs.map { ($0.id, $0) })
        return playlist.songIDs.compactMap { lookup[$0] }
    }

    private func prepareDirectories() throws {
        try fileManager.createDirectory(at: mediaURL, withIntermediateDirectories: true)
        try fileManager.createDirectory(at: artworkURL, withIntermediateDirectories: true)
    }

    private func save() throws {
        try prepareDirectories()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(LibrarySnapshot(songs: songs, favorites: favorites, playlists: playlists))
        try data.write(to: snapshotURL, options: .atomic)
    }

    private func metadata(for url: URL, originalName: String) async throws -> Song {
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration).seconds
        let metadata = try await asset.load(.commonMetadata)
        var title = originalName
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var genre = "Unknown Genre"

        for item in metadata {
            guard let key = item.commonKey else { continue }
            let value = (try? await item.load(.stringValue))?.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let value, !value.isEmpty else { continue }
            switch key {
            case .commonKeyTitle: title = value
            case .commonKeyArtist: artist = value
            case .commonKeyAlbumName: album = value
            case .commonKeyType: genre = value
            default: break
            }
        }
        return Song(title: title, artist: artist, album: album, genre: genre, duration: duration, fileName: url.lastPathComponent)
    }

    private func embeddedArtwork(for url: URL) async throws -> Data? {
        let metadata = try await AVURLAsset(url: url).load(.commonMetadata)
        for item in metadata where item.commonKey == .commonKeyArtwork {
            if let data = try? await item.load(.dataValue) { return data }
        }
        return nil
    }
}
