package com.socialsentry.bkashserver.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayment(payment: PaymentEntity)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE uploadStatus = 'PENDING' OR uploadStatus = 'FAILED'")
    suspend fun getPendingPayments(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE trxId = :trxId LIMIT 1")
    suspend fun getPaymentByTrxId(trxId: String): PaymentEntity?
}
