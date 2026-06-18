package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaListener"
        
        @Volatile
        private var lastActiveMediaTitle: String? = null
        @Volatile
        private var lastActiveMediaArtist: String? = null

        fun getLatestMediaInfo(): Pair<String?, String?> {
            return Pair(lastActiveMediaTitle, lastActiveMediaArtist)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateMediaInfo(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // We could clear it, but usually we want the last one before pause
    }

    private fun updateMediaInfo(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        
        // Check if it's a media notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        
        // Android Media notifications usually have specific categories or actions
        // We look for ones that have a title and are not our own notification
        if (!title.isNullOrBlank() && sbn.packageName != packageName) {
            // Check if it's likely a media notification by checking for media style or certain categories
            val isMedia = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || 
                          notification.category == Notification.CATEGORY_TRANSPORT ||
                          notification.category == Notification.CATEGORY_SERVICE
            
            if (isMedia) {
                lastActiveMediaTitle = title
                lastActiveMediaArtist = artist
                Log.d(TAG, "Captured Media: $title by $artist from ${sbn.packageName}")
            }
        }
    }
}
