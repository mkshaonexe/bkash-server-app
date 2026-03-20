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
     * Scans the device's native SMS inbox for missed bKash payments since the last known timestamp,
     * parses them, ignores duplicates, saves to local DB, and pushes to Supabase.
     */
    suspend fun recoverMissedPayments(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Missed SMS Recovery...")
        val resolver = context.contentResolver
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        
        // Fetch last 100 recent messages just to be safe, filtering for bKash is done below
        val cursor = resolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT 100"
        )

        if (cursor == null) {
            Log.e(TAG, "Failed to query SMS Inbox")
            return@withContext
        }

        val database = PaymentDatabase.getDatabase(context)
        var newPaymentsFound = 0

        cursor.use {
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            
            while (it.moveToNext()) {
                val address = it.getString(addressIndex)
                val body = it.getString(bodyIndex)

                if (BKASH_SENDERS.any { sender -> address.contains(sender, ignoreCase = true) }) {
                    val parsedPayment = BkashSmsParser.parse(body)
                    if (parsedPayment != null) {
                        // Check if this TrxId already exists in our PaymentDatabase
                        val exists = database.paymentDao().getPaymentByTrxId(parsedPayment.trxId) != null
                        
                        if (!exists) {
                            Log.d(TAG, "Recovered missed payment: ${parsedPayment.trxId}")
                            val entity = PaymentEntity(
                                trxId = parsedPayment.trxId,
                                amount = parsedPayment.amount,
                                senderNumber = parsedPayment.senderNumber,
                                dateTime = parsedPayment.dateTime,
                                fee = parsedPayment.fee,
                                balanceAfter = parsedPayment.balanceAfter,
                                rawText = parsedPayment.rawText,
                                uploadStatus = "PENDING"
                            )
                            database.paymentDao().insertPayment(entity)
                            newPaymentsFound++
                        }
                    }
                }
            }
        }

        if (newPaymentsFound > 0) {
            Log.d(TAG, "Found $newPaymentsFound missed payments! Uploading now...")
            PaymentUploader.uploadPendingPayments(context)
        } else {
            Log.d(TAG, "No new missed payments found.")
        }
    }
}
