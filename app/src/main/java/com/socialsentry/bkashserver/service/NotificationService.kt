package com.socialsentry.bkashserver.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationService", "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: "unknown"
        Log.d("NotificationService", "Notification posted from: $packageName")
        
        // Future logic for blocking or saving notifications can be added here
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d("NotificationService", "Notification removed")
    }
}
