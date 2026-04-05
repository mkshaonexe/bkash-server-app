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
import androidx.core.content.ContextCompat
import com.socialsentry.bkashserver.service.SmsForegroundService
import com.socialsentry.bkashserver.ui.DashboardScreen
import com.socialsentry.bkashserver.ui.theme.BkashServerTheme
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import android.util.Log
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

                val permissionsToRequest = mutableListOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions.values.all { it }) {
                        SmsForegroundService.startService(context)
                    }
                }

                LaunchedEffect(Unit) {
                    val allGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        SmsForegroundService.startService(context)

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
                }

                DashboardScreen(payments = payments)
            }
        }
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