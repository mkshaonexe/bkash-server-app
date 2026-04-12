package com.socialsentry.bkashserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.socialsentry.bkashserver.BkashApplication
import com.socialsentry.bkashserver.data.PaymentUploader
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import com.socialsentry.bkashserver.domain.parser.BkashSmsParser
import kotlinx.coroutines.Dispatchers
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.socialsentry.bkashserver.data.PaymentUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private val BKASH_SENDERS = listOf("bKash", "16247", "BKASH")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context == null) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // goAsync() tells Android to wait for our coroutine to finish before
        // considering this BroadcastReceiver "done". Without this, Android may
        // kill the process after 10 seconds even if coroutines are still running.
        val pendingResult = goAsync()

        val appScope = (context.applicationContext as? BkashApplication)?.applicationScope
        if (appScope == null) {
            pendingResult.finish()
            return
        }

        appScope.launch(Dispatchers.IO) {
            try {
                for (message in messages) {
                    val sender = message.displayOriginatingAddress
                    val body = message.displayMessageBody

                    Log.d(TAG, "Received SMS from $sender")

                    if (BKASH_SENDERS.any { sender.contains(it, ignoreCase = true) }) {
                        val payment = BkashSmsParser.parse(body)
                        if (payment != null) {
                            Log.d(TAG, "Parsed bKash payment: ${payment.trxId}")

                            val database = PaymentDatabase.getDatabase(context)
                            val paymentEntity = PaymentEntity(
                                trxId = payment.trxId,
                                amount = payment.amount,
                                senderNumber = payment.senderNumber,
                                dateTime = payment.dateTime,
                                fee = payment.fee,
                                balanceAfter = payment.balanceAfter,
                                rawText = payment.rawText
                            )

                            // Save to local DB first (fast, always works)
                            database.paymentDao().insertPayment(paymentEntity)
                            Log.d(TAG, "Saved to DB: ${payment.trxId}")

                            // Upload ONLY this specific new payment — not retry-all.
                            // This is what was causing CPU overload before.
                            PaymentUploader.uploadSinglePayment(context, paymentEntity)

                            // Schedule a background sync for any pending payments just in case
                            // or if the immediate upload failed.
                            val constraints = Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                            
                            val uploadRequest = OneTimeWorkRequestBuilder<PaymentUploadWorker>()
                                .setConstraints(constraints)
                                .build()
                                
                            WorkManager.getInstance(context).enqueue(uploadRequest)
                        } else {
                            Log.d(TAG, "SMS from bKash sender but not a payment: $body")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                // Always finish, even on error, so Android doesn't think we're stuck.
                pendingResult.finish()
            }
        }
    }
}
