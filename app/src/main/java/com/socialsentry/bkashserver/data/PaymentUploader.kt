package com.socialsentry.bkashserver.data

import android.content.Context
import android.util.Log
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.functions.invoke
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class BkashPaymentSupabase(
    val trx_id: String,
    val sender_number: String,
    val amount: Double,
    val fee: Double,
    val balance_after: Double,
    val received_at: String, // Need to ensure ISO 8601 format or consistent DB format
    val raw_sms_text: String,
    val status: String = "received"
)

object PaymentUploader {
    private const val TAG = "PaymentUploader"

    suspend fun uploadPendingPayments(context: Context) {
        val database = PaymentDatabase.getDatabase(context)
        val pendingPayments = database.paymentDao().getPendingPayments()

        for (payment in pendingPayments) {
            try {
                uploadToSupabase(payment)
                database.paymentDao().updatePayment(payment.copy(uploadStatus = "UPLOADED"))
                Log.d(TAG, "Successfully uploaded payment: ${payment.trxId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload payment: ${payment.trxId}", e)
                database.paymentDao().updatePayment(payment.copy(uploadStatus = "FAILED"))
            }
        }
    }

    private suspend fun uploadToSupabase(payment: PaymentEntity) {
        withContext(Dispatchers.IO) {
            val supabasePayment = BkashPaymentSupabase(
                trx_id = payment.trxId,
                sender_number = payment.senderNumber,
                amount = payment.amount,
                fee = payment.fee,
                balance_after = payment.balanceAfter,
                received_at = formatToSupabaseDate(payment.dateTime),
                raw_sms_text = payment.rawText
            )

            SupabaseClientManager.client.functions.invoke(
                "upload-bkash-payment",
                body = supabasePayment,
                headers = io.ktor.http.Headers.build {
                    append("x-app-secret", com.socialsentry.bkashserver.BuildConfig.BKASH_APP_SECRET)
                }
            )
        }
    }

    private fun formatToSupabaseDate(originalDate: String): String {
        // Original: 23/02/2026 00:09 -> Target: 2026-02-23T00:09:00Z (ISO 8601)
        return try {
            val parts = originalDate.split(" ")
            val dateParts = parts[0].split("/")
            val time = parts[1]
            "${dateParts[2]}-${dateParts[1]}-${dateParts[0]}T${time}:00Z"
        } catch (e: Exception) {
            originalDate // Fallback to raw if parsing fails
        }
    }
}
