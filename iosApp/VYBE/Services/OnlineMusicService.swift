import Foundation

@MainActor
final class OnlineMusicService: ObservableObject {
    static let shared = OnlineMusicService()

    @Published private(set) var trendingTracks: [Song] = []
    @Published private(set) var releaseRadarTracks: [Song] = []
    @Published private(set) var quickPicks: [Song] = []
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    private let apiBases = [
        "https://saavn.dev/api",
        "https://saavn.me/api"
    ]

    private var streamCache: [String: String] = [:]

    init() {
        Task {
            await fetchHomeFeed()
        }
    }

    func fetchHomeFeed() async {
        isLoading = true
        defer { isLoading = false }

        async let trending = search(query: "Top Hits Worldwide", limit: 15)
        async let newReleases = search(query: "Latest Releases 2026", limit: 15)
        async let picks = search(query: "Best Songs For You", limit: 12)

        let (t, nr, qp) = await (trending, newReleases, picks)
        if !t.isEmpty { self.trendingTracks = t }
        if !nr.isEmpty { self.releaseRadarTracks = nr }
        if !qp.isEmpty { self.quickPicks = qp }
    }

    func search(query: String, limit: Int = 20) async -> [Song] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        guard let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return []
        }

        for base in apiBases {
            guard let url = URL(string: "\(base)/search/songs?query=\(encoded)&limit=\(limit)") else { continue }
            var request = URLRequest(url: url, timeoutInterval: 10)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { continue }
                if let songs = parseSongs(from: data), !songs.isEmpty {
                    return songs
                }
            } catch {
                continue
            }
        }

        return []
    }

    func resolveStream(for song: Song) async -> String? {
        if let existing = song.streamUrl, !existing.isEmpty {
            return existing
        }

        if let cached = streamCache[song.id.uuidString] {
            return cached
        }

        let query = "\(song.title) \(song.artist)"
        let results = await search(query: query, limit: 3)
        if let firstMatch = results.first(where: { $0.streamUrl != nil }), let stream = firstMatch.streamUrl {
            streamCache[song.id.uuidString] = stream
            return stream
        }

        return nil
    }

    private func parseSongs(from data: Data) -> [Song]? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }

        var items: [[String: Any]] = []

        if let dataObj = json["data"] as? [String: Any], let results = dataObj["results"] as? [[String: Any]] {
            items = results
        } else if let results = json["results"] as? [[String: Any]] {
            items = results
        } else if let dataArray = json["data"] as? [[String: Any]] {
            items = dataArray
        }

        guard !items.isEmpty else { return nil }

        return items.compactMap { dict -> Song? in
            let idString = (dict["id"] as? String) ?? UUID().uuidString
            let title = decodeHTMLEntities((dict["name"] as? String) ?? (dict["title"] as? String) ?? "Unknown Track")
            guard !title.isEmpty else { return nil }

            var artistName = "Unknown Artist"
            if let artistsObj = dict["artists"] as? [String: Any],
               let primary = artistsObj["primary"] as? [[String: Any]],
               let first = primary.first,
               let name = first["name"] as? String {
                artistName = decodeHTMLEntities(name)
            } else if let artist = dict["primaryArtists"] as? String, !artist.isEmpty {
                artistName = decodeHTMLEntities(artist)
            } else if let artist = dict["artist"] as? String, !artist.isEmpty {
                artistName = decodeHTMLEntities(artist)
            }

            var albumName = "Single"
            if let albumObj = dict["album"] as? [String: Any], let name = albumObj["name"] as? String {
                albumName = decodeHTMLEntities(name)
            } else if let album = dict["album"] as? String, !album.isEmpty {
                albumName = decodeHTMLEntities(album)
            }

            let durationSeconds: Double = {
                if let dur = dict["duration"] as? Double { return dur }
                if let durStr = dict["duration"] as? String, let d = Double(durStr) { return d }
                if let durInt = dict["duration"] as? Int { return Double(durInt) }
                return 210
            }()

            var artworkUrl: String?
            if let images = dict["image"] as? [[String: Any]] {
                if let high = images.first(where: { ($0["quality"] as? String) == "500x500" }), let url = high["url"] as? String {
                    artworkUrl = url
                } else if let last = images.last, let url = last["url"] as? String {
                    artworkUrl = url
                }
            } else if let img = dict["image"] as? String {
                artworkUrl = img
            }

            var streamUrl: String?
            if let downloadUrls = dict["downloadUrl"] as? [[String: Any]] {
                if let hq = downloadUrls.first(where: { ($0["quality"] as? String) == "320kbps" }), let url = hq["url"] as? String {
                    streamUrl = url
                } else if let med = downloadUrls.first(where: { ($0["quality"] as? String) == "160kbps" }), let url = med["url"] as? String {
                    streamUrl = url
                } else if let last = downloadUrls.last, let url = last["url"] as? String {
                    streamUrl = url
                }
            }

            return Song(
                title: title,
                artist: artistName,
                album: albumName,
                genre: "Pop",
                duration: durationSeconds,
                fileName: "",
                artworkFileName: nil,
                lyrics: nil,
                dateAdded: .now,
                playCount: 0,
                lastPlayed: nil,
                streamUrl: streamUrl,
                artworkUrl: artworkUrl,
                source: "saavn",
                remoteId: idString
            )
        }
    }

    private func decodeHTMLEntities(_ string: String) -> String {
        var result = string
        let entities = [
            ("&quot;", "\""),
            ("&amp;", "&"),
            ("&apos;", "'"),
            ("&#039;", "'"),
            ("&lt;", "<"),
            ("&gt;", ">"),
            ("&nbsp;", " ")
        ]
        for (entity, replacement) in entities {
            result = result.replacingOccurrences(of: entity, with: replacement)
        }
        return result
    }
}
