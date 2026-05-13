package com.bikenv.app

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NavNotificationService : NotificationListenerService() {

    private val MAPS_PACKAGE = "com.google.android.apps.maps"

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        if (sbn.packageName != MAPS_PACKAGE) return

        try {
            val extras = sbn.notification.extras

            val title =
                extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""

            val text =
                extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?.toString()
                    ?.trim() ?: ""

            val subText =
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                    ?.toString()
                    ?.trim() ?: ""

            Log.d("NAV_DEBUG", "TITLE: $title")
            Log.d("NAV_DEBUG", "TEXT: $text")
            Log.d("NAV_DEBUG", "SUBTEXT: $subText")

            val lower = "$title $text".lowercase()

            val direction = when {

                "u-turn" in lower -> "UTURN"
                "sharp left" in lower -> "SHARP LEFT"
                "sharp right" in lower -> "SHARP RIGHT"
                "slight left" in lower -> "SLIGHT LEFT"
                "slight right" in lower -> "SLIGHT RIGHT"
                "keep left" in lower -> "KEEP LEFT"
                "keep right" in lower -> "KEEP RIGHT"
                "fork left" in lower -> "FORK LEFT"
                "fork right" in lower -> "FORK RIGHT"
                "merge" in lower -> "MERGE"
                "roundabout" in lower && "exit" in lower -> "EXIT ROUNDABOUT"
                "roundabout" in lower -> "ROUNDABOUT"
                "destination" in lower -> "ARRIVE"
                "ferry" in lower -> "FERRY"
                "left" in lower -> "LEFT"
                "right" in lower -> "RIGHT"
                "straight" in lower -> "STRAIGHT"
                "head north" in lower ||
                        "head south" in lower ||
                        "head east" in lower ||
                        "head west" in lower -> "DEPART"
                else -> "NAV"
            }

            // Use ONLY next maneuver info
            val nextInstruction = when {
                title.isNotBlank() && text.isNotBlank() ->
                    "$title $text"

                text.isNotBlank() ->
                    text

                title.isNotBlank() ->
                    title

                else ->
                    "Navigation"
            }

            val finalData = "$direction|$nextInstruction"

            Log.d("NAV_DEBUG", "FINAL SENT: $finalData")

            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(
                    Intent("NAV_DATA")
                        .putExtra("text", finalData)
                )

        } catch (e: Exception) {
            Log.e("NAV_DEBUG", "ERROR", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}