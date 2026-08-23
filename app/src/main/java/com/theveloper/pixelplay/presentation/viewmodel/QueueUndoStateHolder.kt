package com.theveloper.pixelplay.presentation.viewmodel

import androidx.media3.session.MediaController
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toPersistentList

@ViewModelScoped
class QueueUndoStateHolder @Inject constructor() {
    private var queueItemUndoTimerJob: Job? = null

    fun removeSongFromQueue(
        scope: CoroutineScope,
        mediaController: MediaController?,
        songId: String,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        onQueueItemRemoved: (index: Int, songId: String) -> Unit,
    ) {
        val controller = mediaController ?: return
        
        // Find index in CONTROLLER to ensure we remove the correct item,
        // even if the UI state is slightly out of sync with the player.
        var indexToRemove = -1
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == songId) {
                indexToRemove = i
                break
            }
        }
        
        if (indexToRemove == -1) return

        val currentQueue = getUiState().currentPlaybackQueue
        val uiIndex = indexToRemove
            .takeIf { it in currentQueue.indices && currentQueue[it].id == songId }
            ?: currentQueue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
        val removedSong = uiIndex?.let(currentQueue::get)

        controller.removeMediaItem(indexToRemove)
        onQueueItemRemoved(indexToRemove, songId)

        updateUiState {
            val updatedQueue = uiIndex
                ?.takeIf { queueIndex -> queueIndex in it.currentPlaybackQueue.indices }
                ?.let { queueIndex ->
                    it.currentPlaybackQueue.toMutableList().apply { removeAt(queueIndex) }.toPersistentList()
                }
                ?: it.currentPlaybackQueue
            it.copy(
                currentPlaybackQueue = updatedQueue,
                showQueueItemUndoBar = removedSong != null,
                lastRemovedQueueSong = removedSong,
                lastRemovedQueueIndex = if (removedSong != null) indexToRemove else -1
            )
        }

        if (removedSong == null) return

        queueItemUndoTimerJob?.cancel()
        queueItemUndoTimerJob = scope.launch {
            delay(4000L)
            if (getUiState().showQueueItemUndoBar) {
                updateUiState {
                    it.copy(
                        showQueueItemUndoBar = false,
                        lastRemovedQueueSong = null,
                        lastRemovedQueueIndex = -1
                    )
                }
            }
        }
    }

    fun undoRemoveSongFromQueue(
        mediaController: MediaController?,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        onQueueItemAdded: (song: Song, index: Int) -> Unit,
    ) {
        val uiState = getUiState()
        val song = uiState.lastRemovedQueueSong ?: return
        val index = uiState.lastRemovedQueueIndex
        if (index < 0) return

        mediaController?.let { controller ->
            val mediaItem = MediaItemBuilder.build(song)
            val insertAt = index.coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertAt, mediaItem)
            onQueueItemAdded(song, insertAt)
        }

        hideQueueItemUndoBar(updateUiState)
    }

    fun hideQueueItemUndoBar(
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
    ) {
        queueItemUndoTimerJob?.cancel()
        updateUiState {
            it.copy(
                showQueueItemUndoBar = false,
                lastRemovedQueueSong = null,
                lastRemovedQueueIndex = -1
            )
        }
    }

    fun onCleared() {
        queueItemUndoTimerJob?.cancel()
    }
}
