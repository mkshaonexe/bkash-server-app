package com.socialsentry.bkashserver.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey
    val trxId: String,
    val amount: Double,
    val senderNumber: String,
    val dateTime: String,
    val fee: Double,
    val balanceAfter: Double,
    val rawText: String,
    val uploadStatus: String = "PENDING", // PENDING, UPLOADED, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    // bkash_merchant | bkash_personal | nagad_personal
    @ColumnInfo(defaultValue = "bkash_merchant")
    val paymentSource: String = "bkash_merchant"
)
