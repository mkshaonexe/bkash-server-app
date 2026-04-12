package com.socialsentry.bkashserver.domain

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.socialsentry.bkashserver.data.PaymentUploader
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import com.socialsentry.bkashserver.domain.parser.BkashSmsParser
import com.socialsentry.bkashserver.domain.parser.NagadSmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsRecoveryManager {
    private const val TAG = "SmsRecoveryManager"
    private val BKASH_SENDERS = listOf("bKash", "16247", "BKASH")
    private val NAGAD_SENDERS = listOf("Nagad", "16167", "NAGAD")

    /**
     * Scans the last 30 days of SMS inbox for any bKash OR Nagad payment messages
     * that were missed while the app was off. Only processes messages not yet seen.
     */
    suspend fun recoverMissedPayments(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Multi-Provider Missed SMS Recovery...")
        try {
            val resolver = context.contentResolver
            val uri = Telephony.Sms.Inbox.CONTENT_URI
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
                    val smsDate = it.getLong(dateIndex)

                    val entity: PaymentEntity? = when {
                        // bKash SMS
                        BKASH_SENDERS.any { s -> address.contains(s, ignoreCase = true) } -> {
                            val p = BkashSmsParser.parse(body)
                            if (p != null) PaymentEntity(
                                trxId = p.trxId,
                                amount = p.amount,
                                senderNumber = p.senderNumber,
                                dateTime = p.dateTime,
                                fee = p.fee,
                                balanceAfter = p.balanceAfter,
                                rawText = p.rawText,
                                uploadStatus = "PENDING",
                                createdAt = smsDate,
                                paymentSource = p.paymentSource
                            ) else null
                        }

                        // Nagad SMS
                        NAGAD_SENDERS.any { s -> address.contains(s, ignoreCase = true) } -> {
                            val p = NagadSmsParser.parse(body)
                            if (p != null) PaymentEntity(
                                trxId = p.trxId,
                                amount = p.amount,
                                senderNumber = p.senderNumber,
                                dateTime = p.dateTime,
                                fee = 0.0,
                                balanceAfter = p.balanceAfter,
                                rawText = p.rawText,
                                uploadStatus = "PENDING",
                                createdAt = smsDate,
                                paymentSource = "nagad_personal"
                            ) else null
                        }

                        else -> null
                    }

                    if (entity != null) {
                        val exists = database.paymentDao().getPaymentByTrxId(entity.trxId) != null
                        if (!exists) {
                            Log.d(TAG, "Recovered: ${entity.trxId} [${entity.paymentSource}]")
                            database.paymentDao().insertPayment(entity)
                            newPaymentsFound++
                        }
                    }
                }
            }

            if (newPaymentsFound > 0) {
                Log.d(TAG, "Recovered $newPaymentsFound missed payments, uploading...")
                PaymentUploader.uploadPendingPayments(context)
            } else {
                Log.d(TAG, "No new missed payments found.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during SMS recovery", e)
        }
    }
}
