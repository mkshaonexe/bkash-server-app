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
import com.socialsentry.bkashserver.domain.SmsRecoveryManager
import kotlinx.coroutines.launch

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
                        
                        // Recover any missed payments while the app was closed
                        launch {
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