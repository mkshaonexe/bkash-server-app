package com.socialsentry.bkashserver.domain

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServiceLog(
    val startTime: String,
    var stopTime: String? = null,
    var reason: String? = null
)

object ServiceTracker {
    private const val PREFS_NAME = "bkash_service_prefs"
    private const val KEY_LOG_HISTORY = "log_history"
    private const val MAX_LOGS = 50 // Keep the last 50 logs

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getCurrentTimeFormatted(): String {
        return SimpleDateFormat("dd/MMM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    fun onServiceStart(context: Context) {
        val logs = getLogHistory(context).toMutableList()
        // Start a new log
        logs.add(0, ServiceLog(startTime = getCurrentTimeFormatted()))
        if (logs.size > MAX_LOGS) {
            logs.removeAt(logs.size - 1)
        }
        saveLogs(context, logs)
    }

    fun onServiceStop(context: Context, reason: String = "Unknown") {
        val logs = getLogHistory(context).toMutableList()
        if (logs.isNotEmpty()) {
            val mostRecent = logs[0]
            if (mostRecent.stopTime == null) {
                mostRecent.stopTime = getCurrentTimeFormatted()
                mostRecent.reason = reason
            }
        }
        saveLogs(context, logs)
    }

    private fun saveLogs(context: Context, logs: List<ServiceLog>) {
        val jsonArray = JSONArray()
        logs.forEach { log ->
            val obj = JSONObject().apply {
                put("startTime", log.startTime)
                put("stopTime", log.stopTime ?: JSONObject.NULL)
                put("reason", log.reason ?: JSONObject.NULL)
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_LOG_HISTORY, jsonArray.toString()).apply()
    }

    fun getLogHistory(context: Context): List<ServiceLog> {
        val jsonString = getPrefs(context).getString(KEY_LOG_HISTORY, "[]") ?: "[]"
        val logs = mutableListOf<ServiceLog>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                logs.add(
                    ServiceLog(
                        startTime = obj.getString("startTime"),
                        stopTime = if (obj.isNull("stopTime")) null else obj.getString("stopTime"),
                        reason = if (obj.isNull("reason")) null else obj.getString("reason")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return logs
    }

    fun getUptimeStatus(context: Context): String {
        val logs = getLogHistory(context)
        if (logs.isEmpty()) return "⚪ Never started"
        val lastLog = logs.first()
        return if (lastLog.stopTime == null) {
            "🟢 Active since ${lastLog.startTime}"
        } else {
            "🔴 Stopped at ${lastLog.stopTime} (${lastLog.reason})."
        }
    }
}
