package com.theveloper.pixelplay.data.network.ytmusic

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.jupiter.api.Test

class YouTubeMusicEngineParserTest {
    private val engine = YouTubeMusicEngine(
        context = mockk<Context>(relaxed = true),
        okHttpClient = OkHttpClient(),
        newPipeStreamResolver = mockk(relaxed = true),
    )

    @Test
    fun `home parser preserves provider shelves tracks and exact artist ids`() {
        val response = JSONObject(
            """
            {
              "contents": [
                {"musicCarouselShelfRenderer": {
                  "header": {"musicCarouselShelfBasicHeaderRenderer": {
                    "title": {"runs": [{"text": "Quick picks"}]}
                  }},
                  "contents": [{"musicResponsiveListItemRenderer": {
                    "playlistItemData": {"videoId": "video123"},
                    "musicVideoType": "MUSIC_VIDEO_TYPE_ATV",
                    "flexColumns": [
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Exact Song"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{
                        "text": "Exact Artist",
                        "navigationEndpoint": {"browseEndpoint": {"browseId": "UCartist123"}}
                      }]}}}
                    ],
                    "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                      {"url": "https://example.test/art.jpg", "width": 544}
                    ]}}}
                  }}]
                }},
                {"musicCarouselShelfRenderer": {
                  "header": {"musicCarouselShelfBasicHeaderRenderer": {
                    "title": {"runs": [{"text": "Artists for you"}]}
                  }},
                  "contents": [{"musicTwoRowItemRenderer": {
                    "title": {"runs": [{"text": "Exact Artist"}]},
                    "subtitle": {"runs": [{"text": "Artist"}]},
                    "navigationEndpoint": {"browseEndpoint": {
                      "browseId": "UCartist123",
                      "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
                        "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                      }}
                    }},
                    "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                      {"url": "https://example.test/artist.jpg", "width": 544}
                    ]}}}
                  }}]
                }}
              ]
            }
            """.trimIndent(),
        )

        val shelves = engine.parseHomeShelves(response)

        assertThat(shelves.map { it.title }).containsExactly("Quick picks", "Artists for you").inOrder()
        assertThat(shelves.first().songs.single().id).isEqualTo("yt_video123")
        assertThat(shelves.first().songs.single().artist).isEqualTo("Exact Artist")
        assertThat(shelves.last().collections.single().browseId).isEqualTo("UCartist123")
        assertThat(shelves.last().collections.single().pageType).isEqualTo("MUSIC_PAGE_TYPE_ARTIST")
    }

    @Test
    fun `continuation token parser supports command and next data variants`() {
        assertThat(
            engine.extractContinuationToken(
                JSONObject("""{"continuationEndpoint":{"continuationCommand":{"token":"next-one"}}}"""),
            ),
        ).isEqualTo("next-one")
        assertThat(
            engine.extractContinuationToken(
                JSONObject("""{"nextContinuationData":{"continuation":"next-two"}}"""),
            ),
        ).isEqualTo("next-two")
    }

    @Test
    fun `album parser merges tracks from every continuation page`() {
        fun track(id: String, title: String) = """
            {"musicResponsiveListItemRenderer": {
              "playlistItemData": {"videoId": "$id"},
              "musicVideoType": "MUSIC_VIDEO_TYPE_ATV",
              "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "$title"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Artist"}]}}}
              ]
            }}
        """.trimIndent()
        val combined = """
            {"pages": [
              {"header": {"musicDetailHeaderRenderer": {
                "title": {"runs": [{"text": "Exact Album"}]},
                "subtitle": {"runs": [{"text": "Artist • 2026"}]}
              }}, "contents": [${track("one", "First")}]},
              {"continuationContents": {"musicShelfContinuation": {
                "contents": [${track("two", "Second")}]
              }}}
            ]}
        """.trimIndent()

        val details = engine.parseAlbumDetailsResponse("MPREexact", combined)

        assertThat(details).isNotNull()
        assertThat(details!!.title).isEqualTo("Exact Album")
        assertThat(details.tracks.map { it.id }).containsExactly("yt_one", "yt_two").inOrder()
    }
}
