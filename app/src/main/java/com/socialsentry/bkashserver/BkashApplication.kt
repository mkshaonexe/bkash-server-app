package com.socialsentry.bkashserver

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class that provides a single, lifecycle-aware CoroutineScope for all
 * background work (SMS processing, uploads). This replaces all GlobalScope.launch usages
 * so coroutines are properly managed and can't cause the CPU to spin indefinitely.
 */
class BkashApplication : Application() {
    /**
     * This scope lives as long as the entire application process.
     * SupervisorJob ensures one failed coroutine doesn't cancel others.
     * Dispatchers.Default is used as default; callers switch to IO when needed.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Subscribe to admin_alerts topic to receive manual payment requests
        FirebaseMessaging.getInstance().subscribeToTopic("admin_alerts")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to admin_alerts"
                if (!task.isSuccessful) {
                    msg = "Subscribe failed"
                }
                Log.d("BkashApplication", msg)
            }
    }
}
