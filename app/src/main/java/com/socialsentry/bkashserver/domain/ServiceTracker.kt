package com.socialsentry.bkashserver.domain

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ServiceTracker {
    private const val PREFS_NAME = "bkash_service_prefs"
    private const val KEY_LAST_START_TIME = "last_start_time"
    private const val KEY_LAST_STOP_TIME = "last_stop_time"
    private const val KEY_STOP_REASON = "stop_reason"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getCurrentTimeFormatted(): String {
        return SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun onServiceStart(context: Context) {
        getPrefs(context).edit().apply {
            putString(KEY_LAST_START_TIME, getCurrentTimeFormatted())
            remove(KEY_LAST_STOP_TIME)
            remove(KEY_STOP_REASON)
            apply()
        }
    }

    fun onServiceStop(context: Context, reason: String = "Unknown") {
        getPrefs(context).edit().apply {
            putString(KEY_LAST_STOP_TIME, getCurrentTimeFormatted())
            putString(KEY_STOP_REASON, reason)
            apply()
        }
    }

    fun getUptimeStatus(context: Context): String {
        val prefs = getPrefs(context)
        val startTime = prefs.getString(KEY_LAST_START_TIME, null)
        val stopTime = prefs.getString(KEY_LAST_STOP_TIME, null)
        val reason = prefs.getString(KEY_STOP_REASON, "Killed/Closed")

        return when {
            startTime != null && stopTime == null -> "🟢 Active since $startTime"
            startTime != null && stopTime != null -> "🔴 Stopped at $stopTime ($reason). Previous start: $startTime"
            else -> "⚪ Never started"
        }
    }
}
