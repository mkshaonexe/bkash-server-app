package com.socialsentry.bkashserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.socialsentry.bkashserver.BkashApplication
import com.socialsentry.bkashserver.data.PaymentUploadWorker
import com.socialsentry.bkashserver.data.PaymentUploader
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import com.socialsentry.bkashserver.domain.parser.BkashSmsParser
import com.socialsentry.bkashserver.domain.parser.NagadSmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private val BKASH_SENDERS = listOf("bKash", "16247", "BKASH")
        private val NAGAD_SENDERS = listOf("Nagad", "16167", "NAGAD")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context == null) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

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

                    when {
                        // --- bKash SMS (both merchant and personal) ---
                        BKASH_SENDERS.any { sender.contains(it, ignoreCase = true) } -> {
                            val payment = BkashSmsParser.parse(body)
                            if (payment != null) {
                                Log.d(TAG, "Parsed bKash payment [${payment.paymentSource}]: ${payment.trxId}")
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
                                saveAndUpload(context, entity)
                            } else {
                                Log.d(TAG, "SMS from bKash sender but not a payment: $body")
                            }
                        }

                        // --- Nagad SMS ---
                        NAGAD_SENDERS.any { sender.contains(it, ignoreCase = true) } -> {
                            val payment = NagadSmsParser.parse(body)
                            if (payment != null) {
                                Log.d(TAG, "Parsed Nagad payment: ${payment.trxId}")
                                val entity = PaymentEntity(
                                    trxId = payment.trxId,
                                    amount = payment.amount,
                                    senderNumber = payment.senderNumber,
                                    dateTime = payment.dateTime,
                                    fee = 0.0,
                                    balanceAfter = payment.balanceAfter,
                                    rawText = payment.rawText,
                                    paymentSource = "nagad_personal"
                                )
                                saveAndUpload(context, entity)
                            } else {
                                Log.d(TAG, "SMS from Nagad sender but not a payment: $body")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun saveAndUpload(context: Context, entity: PaymentEntity) {
        val database = PaymentDatabase.getDatabase(context)
        database.paymentDao().insertPayment(entity)
        Log.d(TAG, "Saved to DB: ${entity.trxId} [${entity.paymentSource}]")

        PaymentUploader.uploadSinglePayment(context, entity)

        // Queue a WorkManager job as a safety net for offline scenarios
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadRequest = OneTimeWorkRequestBuilder<PaymentUploadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(uploadRequest)
    }
}
