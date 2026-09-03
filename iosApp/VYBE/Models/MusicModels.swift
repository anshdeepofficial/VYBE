import Foundation

struct Song: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var title: String
    var artist: String
    var album: String
    var genre: String
    var duration: Double
    var fileName: String
    var artworkFileName: String?
    var lyrics: String?
    var dateAdded: Date
    var playCount: Int
    var lastPlayed: Date?

    init(
        id: UUID = UUID(),
        title: String,
        artist: String = "Unknown Artist",
        album: String = "Unknown Album",
        genre: String = "Unknown Genre",
        duration: Double = 0,
        fileName: String,
        artworkFileName: String? = nil,
        lyrics: String? = nil,
        dateAdded: Date = .now,
        playCount: Int = 0,
        lastPlayed: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.album = album
        self.genre = genre
        self.duration = duration
        self.fileName = fileName
        self.artworkFileName = artworkFileName
        self.lyrics = lyrics
        self.dateAdded = dateAdded
        self.playCount = playCount
        self.lastPlayed = lastPlayed
    }
}

struct VYBEPlaylist: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var name: String
    var songIDs: [UUID]
    var createdAt: Date

    init(id: UUID = UUID(), name: String, songIDs: [UUID] = [], createdAt: Date = .now) {
        self.id = id
        self.name = name
        self.songIDs = songIDs
        self.createdAt = createdAt
    }
}

struct LibrarySnapshot: Codable, Sendable {
    var songs: [Song] = []
    var favorites: Set<UUID> = []
    var playlists: [VYBEPlaylist] = []
}

enum LibraryTab: String, CaseIterable, Identifiable {
    case songs = "Songs"
    case cached = "Cached"
    case albums = "Albums"
    case artists = "Artists"
    case playlists = "Playlists"
    case favorites = "Favorites"

    var id: String { rawValue }
}

enum RepeatMode: String, CaseIterable {
    case off
    case all
    case one
}

enum RootTab: String, CaseIterable, Identifiable {
    case home = "Home"
    case search = "Search"
    case library = "Library"

    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .home: "house.fill"
        case .search: "magnifyingglass"
        case .library: "music.note.list"
        }
    }
}

enum AppRoute: Hashable {
    case settings
    case stats
    case album(String)
    case artist(String)
    case playlist(UUID)
}

