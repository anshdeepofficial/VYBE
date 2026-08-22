package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryTabIdTest {

    @Test
    fun `decodeLibraryTabOrder returns default order when stored value is null`() {
        val order = decodeLibraryTabOrder(null)
        assertIterableEquals(LibraryTabId.defaultOrder, order)
    }

    @Test
    fun `decodeLibraryTabOrder preserves known order and restores missing tabs`() {
        val storedKeys = listOf(
            LibraryTabId.Liked.stableKey,
            "UNKNOWN",
            LibraryTabId.Playlists.stableKey,
            LibraryTabId.Liked.stableKey // duplicate should be ignored
        )
        val order = decodeLibraryTabOrder(Json.encodeToString(storedKeys))

        assertEquals(LibraryTabId.Downloads, order.first(), "Downloads should remain the fixed first tab")
        assertTrue(
            order.indexOf(LibraryTabId.Liked) < order.indexOf(LibraryTabId.Playlists),
            "Stored relative order should be preserved after the fixed Downloads tab"
        )
        assertTrue(order.containsAll(LibraryTabId.defaultOrder), "All default tabs should be present exactly once")
        assertEquals(LibraryTabId.defaultOrder.size, order.size)
    }

    @Test
    fun `sort associations remain tied to tab ids after reordering`() {
        val persistedSorts = LibraryTabId.defaultOrder.associateWith { tab ->
            tab.sortOptions.firstOrNull() ?: SortOption.SongTitleAZ
        }

        val shuffledOrder = decodeLibraryTabOrder(
            Json.encodeToString(
                listOf(
                    LibraryTabId.Folders.stableKey,
                    LibraryTabId.Songs.stableKey,
                    LibraryTabId.Playlists.stableKey
                )
            )
        )

        shuffledOrder.forEach { tab ->
            assertEquals(persistedSorts[tab], persistedSorts.getValue(tab))
        }
    }
}
