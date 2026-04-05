package com.socialsentry.bkashserver.data

import android.content.Context
import android.util.Log
import com.socialsentry.bkashserver.BuildConfig
import com.socialsentry.bkashserver.data.local.PaymentDatabase
import com.socialsentry.bkashserver.data.local.PaymentEntity
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BkashPaymentSupabase(
    val trx_id: String,
    val sender_number: String,
    val amount: Double,
    val fee: Double,
    val balance_after: Double,
    val received_at: String,
    val raw_sms_text: String,
    val status: String = "received"
)

object PaymentUploader {
    private const val TAG = "PaymentUploader"

    // We use a raw Ktor HTTP client instead of the Supabase SDK's functions.invoke()
    // because the SDK does not correctly pass custom headers (x-app-secret) to edge
    // functions in some Kotlin SDK versions, causing 401 Unauthorized errors.
    private val httpClient = HttpClient(OkHttp)

    /**
     * Uploads a SINGLE specific payment. Used by SmsReceiver when a new SMS arrives
     * so we only make one network call, not retry-all every time.
     */
    suspend fun uploadSinglePayment(context: Context, payment: PaymentEntity) {
        val database = PaymentDatabase.getDatabase(context)
        val result = uploadToSupabase(payment)
        when (result) {
            UploadResult.SUCCESS, UploadResult.ALREADY_EXISTS -> {
                // ALREADY_EXISTS means the TrxID is already in Supabase (e.g. from
                // a previous run that uploaded but crashed before updating local DB).
                // In both cases the server has the data — mark it UPLOADED.
                database.paymentDao().updatePayment(payment.copy(uploadStatus = "UPLOADED"))
                Log.d(TAG, "Uploaded (or already exists) single payment: ${payment.trxId}")
            }
            UploadResult.FAILURE -> {
                Log.e(TAG, "Failed to upload payment: ${payment.trxId}")
                database.paymentDao().updatePayment(payment.copy(uploadStatus = "FAILED"))
            }
        }
    }

    /**
     * Retries ALL pending/failed payments. Use ONLY on startup recovery or manual
     * "Retry All" tap — not on every incoming SMS.
     */
    suspend fun uploadPendingPayments(context: Context) {
        val database = PaymentDatabase.getDatabase(context)
        val pendingPayments = database.paymentDao().getPendingPayments()

        if (pendingPayments.isEmpty()) return

        Log.d(TAG, "Uploading ${pendingPayments.size} pending payments...")
        for (payment in pendingPayments) {
            val result = uploadToSupabase(payment)
            when (result) {
                UploadResult.SUCCESS, UploadResult.ALREADY_EXISTS -> {
                    database.paymentDao().updatePayment(payment.copy(uploadStatus = "UPLOADED"))
                    Log.d(TAG, "Uploaded (or already exists): ${payment.trxId}")
                }
                UploadResult.FAILURE -> {
                    Log.e(TAG, "Failed: ${payment.trxId}")
                    database.paymentDao().updatePayment(payment.copy(uploadStatus = "FAILED"))
                }
            }
        }
    }

    private enum class UploadResult { SUCCESS, ALREADY_EXISTS, FAILURE }

    private suspend fun uploadToSupabase(payment: PaymentEntity): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val supabasePayment = BkashPaymentSupabase(
                    trx_id = payment.trxId,
                    sender_number = payment.senderNumber,
                    amount = payment.amount,
                    fee = payment.fee,
                    balance_after = payment.balanceAfter,
                    received_at = formatToSupabaseDate(payment.dateTime),
                    raw_sms_text = payment.rawText
                )

                val json = Json.encodeToString(supabasePayment)
                val functionUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/upload-bkash-payment"

                val response: HttpResponse = httpClient.post(functionUrl) {
                    header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    header("x-app-secret", BuildConfig.BKASH_APP_SECRET)
                    contentType(ContentType.Application.Json)
                    setBody(json)
                }

                val statusCode = response.status.value
                val responseBody = response.bodyAsText()
                Log.d(TAG, "Upload response for ${payment.trxId}: HTTP $statusCode - $responseBody")

                when {
                    statusCode == 200 -> UploadResult.SUCCESS
                    // 409 or 400 with duplicate message means it's already on the server
                    statusCode == 409 || responseBody.contains("already_exists", ignoreCase = true) || 
                    responseBody.contains("duplicate", ignoreCase = true) -> UploadResult.ALREADY_EXISTS
                    else -> {
                        Log.e(TAG, "Failed HTTP $statusCode for ${payment.trxId}: $responseBody")
                        UploadResult.FAILURE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception uploading ${payment.trxId}", e)
                UploadResult.FAILURE
            }
        }
    }

    private fun formatToSupabaseDate(originalDate: String): String {
        return try {
            val parts = originalDate.split(" ")
            val dateParts = parts[0].split("/")
            val time = parts[1]
            "${dateParts[2]}-${dateParts[1]}-${dateParts[0]}T${time}:00Z"
        } catch (e: Exception) {
            originalDate
        }
    }
}
