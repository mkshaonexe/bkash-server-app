package com.socialsentry.bkashserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.socialsentry.bkashserver.ui.DashboardScreen
import com.socialsentry.bkashserver.ui.ManualRequestsScreen
import com.socialsentry.bkashserver.ui.theme.BkashServerTheme
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import android.util.Log
import android.content.ComponentName
import com.socialsentry.bkashserver.domain.SmsRecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BkashServerTheme {
                val context = LocalContext.current
                val database = remember { PaymentDatabase.getDatabase(context) }
                val payments by database.paymentDao().getAllPayments().collectAsState(initial = emptyList())

                val permissionsToRequest = listOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // Permissions granted, background listener will work automatically
                }

                LaunchedEffect(Unit) {
                    val allGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        launch {
                            // Fix existing records that were recovered with wrong createdAt (= now)
                            // This runs once at startup and costs very little.
                            fixCorruptedCreatedAtTimestamps(database)

                            // Retry any "Failed" or "Pending" payments from previous attempts
                            com.socialsentry.bkashserver.data.PaymentUploader.uploadPendingPayments(context)

                            // Recover any missed payments while the app was closed
                            SmsRecoveryManager.recoverMissedPayments(context)
                        }
                    } else {
                        launcher.launch(permissionsToRequest.toTypedArray())
                    }

                    // Request Battery Optimization Ignore to ensure 24/7 uptime
                    val pm = context.getSystemService(POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }

                    // Request Notification Access until granted
                    if (!isNotificationServiceEnabled()) {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                                putExtra(
                                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                    ComponentName(context, com.socialsentry.bkashserver.service.NotificationService::class.java).flattenToString()
                                )
                            }
                        } else {
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    }
                }

                var currentTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, contentDescription = "SMS Log") },
                                label = { Text("SMS Log") },
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.PendingActions, contentDescription = "Pending Requests") },
                                label = { Text("Pending") },
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (currentTab == 0) {
                            DashboardScreen(payments = payments)
                        } else {
                            ManualRequestsScreen()
                        }
                    }
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }
}

/**
 * One-time repair: For any payment in the local DB whose createdAt is far in the future
 * compared to what the dateTime string says, we re-parse the dateTime and update createdAt.
 *
 * This fixes entries created by the old SmsRecoveryManager before this fix was applied,
 * which used System.currentTimeMillis() as createdAt for ALL recovered SMS regardless of
 * their actual date — causing old March SMS to appear in the "Today" tab.
 */
private suspend fun fixCorruptedCreatedAtTimestamps(
    database: com.socialsentry.bkashserver.data.local.PaymentDatabase
) = withContext(Dispatchers.IO) {
    try {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dao = database.paymentDao()
        val allPayments = dao.getAllPaymentsOnce()
        var fixedCount = 0

        for (payment in allPayments) {
            try {
                val parsedDate = sdf.parse(payment.dateTime) ?: continue
                val parsedMs = parsedDate.time
                // If createdAt differs from the parsed dateTime by more than 1 hour,
                // the record was saved with the wrong timestamp — fix it.
                val diffMs = Math.abs(payment.createdAt - parsedMs)
                if (diffMs > 60 * 60 * 1000L) {
                    dao.updateCreatedAt(payment.trxId, parsedMs)
                    fixedCount++
                }
            } catch (_: Exception) {
                // Unparseable dateTime — leave it alone
            }
        }

        if (fixedCount > 0) {
            Log.d("MainActivity", "Fixed createdAt timestamps for $fixedCount payments")
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to fix createdAt timestamps", e)
    }
}