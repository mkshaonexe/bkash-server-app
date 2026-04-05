package com.socialsentry.bkashserver.domain

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.socialsentry.bkashserver.data.PaymentUploader
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import com.socialsentry.bkashserver.domain.parser.BkashSmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsRecoveryManager {
    private const val TAG = "SmsRecoveryManager"
    private val BKASH_SENDERS = listOf("bKash", "16247", "BKASH")

    /**
     * Scans the last 30 days of SMS inbox for any bKash payment messages that were
     * missed while the app was off. Only processes messages we haven't seen before.
     * Previously this queried with no date filter and no time boundary, which was
     * wasteful; now it only looks at messages since 30 days ago.
     */
    suspend fun recoverMissedPayments(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Missed SMS Recovery...")
        try {
            val resolver = context.contentResolver
            val uri = Telephony.Sms.Inbox.CONTENT_URI

            // Only look at SMS from the last 30 days. This prevents scanning the
            // entire inbox history on every app launch.
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

            val cursor = resolver.query(
                uri,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(thirtyDaysAgo.toString()),
                "${Telephony.Sms.DATE} DESC"
            )

            if (cursor == null) {
                Log.e(TAG, "Failed to query SMS Inbox - permission may be missing")
                return@withContext
            }

            val database = PaymentDatabase.getDatabase(context)
            var newPaymentsFound = 0

            cursor.use {
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: continue
                    val body = it.getString(bodyIndex) ?: continue
                    // Read the actual SMS timestamp from the system inbox.
                    // This is crucial: without it, recovered SMS get createdAt = now()
                    // which makes old messages from March appear in Today's tab.
                    val smsDate = it.getLong(dateIndex)

                    if (BKASH_SENDERS.any { sender -> address.contains(sender, ignoreCase = true) }) {
                        val parsedPayment = BkashSmsParser.parse(body)
                        if (parsedPayment != null) {
                            // Only add if we haven't already stored this TrxID
                            val exists = database.paymentDao().getPaymentByTrxId(parsedPayment.trxId) != null
                            if (!exists) {
                                Log.d(TAG, "Recovered missed payment: ${parsedPayment.trxId} (SMS date: $smsDate)")
                                val entity = PaymentEntity(
                                    trxId = parsedPayment.trxId,
                                    amount = parsedPayment.amount,
                                    senderNumber = parsedPayment.senderNumber,
                                    dateTime = parsedPayment.dateTime,
                                    fee = parsedPayment.fee,
                                    balanceAfter = parsedPayment.balanceAfter,
                                    rawText = parsedPayment.rawText,
                                    uploadStatus = "PENDING",
                                    // Use the actual SMS date so filters (Today/Week/Month)
                                    // correctly categorize recovered historical messages.
                                    createdAt = smsDate
                                )
                                database.paymentDao().insertPayment(entity)
                                newPaymentsFound++
                            }
                        }
                    }
                }
            }

            if (newPaymentsFound > 0) {
                Log.d(TAG, "Found $newPaymentsFound missed payments, uploading...")
                PaymentUploader.uploadPendingPayments(context)
            } else {
                Log.d(TAG, "No new missed payments found.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during SMS recovery", e)
        }
    }
}
