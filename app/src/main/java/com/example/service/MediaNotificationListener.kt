package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class MediaPlaybackInfo(
    val title: String,
    val artist: String?,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaListener"
        
        // Holding a static reference to the active listener instance
        private var instance: MediaNotificationListener? = null
        
        // Use a list to track all media items played during the active timer session
        private val sessionMediaList = mutableListOf<MediaPlaybackInfo>()

        fun startNewTrackingSession() {
            synchronized(sessionMediaList) {
                sessionMediaList.clear()
            }
            Log.d(TAG, "New Tracking Session Started. Requesting initial scan...")
            instance?.scanActiveNotifications()
        }

        fun getCapturedMediaList(): List<MediaPlaybackInfo> {
            return synchronized(sessionMediaList) {
                sessionMediaList.toList()
            }
        }

        fun getLatestMediaInfo(): Pair<String?, String?> {
            return synchronized(sessionMediaList) {
                val last = sessionMediaList.lastOrNull()
                Pair(last?.title, last?.artist)
            }
        }

        fun isAnyMediaActive(): Boolean {
            if (instance == null) return false
            try {
                val active = instance?.activeNotifications ?: return false
                return active.any { sbn ->
                    val notification = sbn.notification
                    val pkg = sbn.packageName ?: ""
                    val extras = notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    
                    if (title.isNullOrBlank() || pkg == instance?.packageName) return@any false

                    val isMediaStyle = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
                    val isTransport = notification.category == Notification.CATEGORY_TRANSPORT
                    val isCommonMediaApp = pkg.contains("youtube") || 
                                          pkg.contains("spotify") || 
                                          pkg.contains("music") || 
                                          pkg.contains("vlc") || 
                                          pkg.contains("player") ||
                                          pkg.contains("tiktok") ||
                                          pkg.contains("netflix") ||
                                          pkg.contains("browser") ||
                                          pkg.contains("chrome")
                    
                    isMediaStyle || isCommonMediaApp || isTransport
                }
            } catch (e: Exception) {
                return false
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Notification Listener Connected")
        // Scan immediately upon connection just in case
        scanActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.d(TAG, "Notification Listener Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateMediaInfo(sbn)
        BedtimeReminderHelper.maybeShowReminder(this)
    }

    fun scanActiveNotifications() {
        try {
            val activeNotifications = getActiveNotifications()
            Log.d(TAG, "Scanning ${activeNotifications?.size ?: 0} active notifications")
            activeNotifications?.forEach { updateMediaInfo(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan active notifications", e)
        }
    }

    private fun updateMediaInfo(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: ""
        
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val artist = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence("android.subText"))?.toString()?.trim()
        
        if (!title.isNullOrBlank() && pkg != packageName) {
            // Broad detection: Media Style OR common media packages OR Transport category
            val isMediaStyle = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            val isTransport = notification.category == Notification.CATEGORY_TRANSPORT
            val isCommonMediaApp = pkg.contains("youtube") || 
                                  pkg.contains("spotify") || 
                                  pkg.contains("music") || 
                                  pkg.contains("vlc") || 
                                  pkg.contains("player") ||
                                  pkg.contains("tiktok") ||
                                  pkg.contains("netflix") ||
                                  pkg.contains("browser") ||
                                  pkg.contains("chrome")

            if (isMediaStyle || isCommonMediaApp || isTransport) {
                synchronized(sessionMediaList) {
                    val lastEntry = sessionMediaList.lastOrNull()
                    
                    // Case 1: This notification is an update to the current track
                    if (lastEntry != null && lastEntry.title == title && lastEntry.packageName == pkg) {
                        // If current entry has no artist but new notification DOES, update it in place
                        if (lastEntry.artist.isNullOrBlank() && !artist.isNullOrBlank()) {
                            sessionMediaList[sessionMediaList.size - 1] = MediaPlaybackInfo(title, artist, pkg)
                            Log.d(TAG, "Updated last track (added artist) from $pkg: $title")
                        }
                        // Otherwise, it's just a duplicate/refresh of the current track, so we do nothing.
                    } else {
                        // Case 2: Check if this track was captured EARLIER in the session
                        val alreadyCaptured = sessionMediaList.any { 
                            it.title == title && it.artist == artist && it.packageName == pkg
                        }

                        if (!alreadyCaptured) {
                            sessionMediaList.add(MediaPlaybackInfo(title, artist, pkg))
                            Log.d(TAG, "Captured Track from $pkg: $title by $artist")
                        }
                    }
                }
            }
        }
    }
}
