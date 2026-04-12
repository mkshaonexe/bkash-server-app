package com.socialsentry.bkashserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.socialsentry.bkashserver.domain.SmsRecoveryManager
import com.socialsentry.bkashserver.BkashApplication
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            context?.let { ctx ->
                val appScope = (ctx.applicationContext as? BkashApplication)?.applicationScope
                appScope?.launch {
                    SmsRecoveryManager.recoverMissedPayments(ctx)
                }
            }
        }
    }
}
