package com.socialsentry.bkashserver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PaymentUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            PaymentUploader.uploadPendingPayments(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // WorkManager will automatically retry based on backoff policy if we return Result.retry()
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
