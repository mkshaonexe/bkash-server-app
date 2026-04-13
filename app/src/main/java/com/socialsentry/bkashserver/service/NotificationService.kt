package com.socialsentry.bkashserver.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.socialsentry.bkashserver.data.PaymentUploader
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import com.socialsentry.bkashserver.domain.parser.BkashNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationService", "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val packageName = sbn.packageName ?: "unknown"
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        Log.d("NotificationService", "Posted from: $packageName, Title: $title, Text: $text")

        val payment = BkashNotificationParser.parse(title = title, text = text)
        if (payment != null) {
            Log.d("NotificationService", "Parsed Push Notification Payment: Amount=${payment.amount}, Sender=${payment.senderNumber}")
            
            val entity = PaymentEntity(
                trxId = payment.trxId,
                amount = payment.amount,
                senderNumber = payment.senderNumber,
                dateTime = payment.dateTime,
                fee = payment.fee,
                balanceAfter = payment.balanceAfter,
                rawText = payment.rawText,
                paymentSource = payment.paymentSource
            )
            
            serviceScope.launch {
                try {
                    val database = PaymentDatabase.getDatabase(applicationContext)
                    database.paymentDao().insertPayment(entity)
                    Log.d("NotificationService", "Saved Push Payment to DB: ${entity.trxId}")

                    PaymentUploader.uploadSinglePayment(applicationContext, entity)
                } catch (e: Exception) {
                    Log.e("NotificationService", "Failed to save or upload push payment", e)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Log.d("NotificationService", "Notification removed")
    }
}

