package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Wraps Media3's default provider and marks playback notifications as local-only
 * so they don't get bridged to Wear OS as generic remote media controls.
 */
@UnstableApi
class LocalOnlyMediaNotificationProvider(
    private val context: Context,
    private val delegate: DefaultMediaNotificationProvider =
        DefaultMediaNotificationProvider.Builder(context).build(),
) : MediaNotification.Provider {

    @Volatile
    private var promotedOngoingEnabled: Boolean = true

    fun setPromotedOngoingEnabled(enabled: Boolean) {
        promotedOngoingEnabled = enabled
    }

    fun setSmallIcon(iconResId: Int) {
        delegate.setSmallIcon(iconResId)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val notification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            callback
        )
        val localOnlyNotification = runCatching {
            Notification.Builder.recoverBuilder(context, notification.notification)
                .setLocalOnly(true)
                .apply {
                    if (android.os.Build.VERSION.SDK_INT >= 36) {
                        setRequestPromotedOngoing(promotedOngoingEnabled)
                    }
                }
                .build()
        }.getOrElse {
            notification.notification
        }
        return MediaNotification(notification.notificationId, localOnlyNotification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}

