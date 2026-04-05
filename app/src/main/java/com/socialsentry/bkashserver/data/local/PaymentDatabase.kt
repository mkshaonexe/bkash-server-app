package com.socialsentry.bkashserver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PaymentEntity::class], version = 1, exportSchema = false)
abstract class PaymentDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: PaymentDatabase? = null

        fun getDatabase(context: Context): PaymentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaymentDatabase::class.java,
                    "payment_database"
                )
                    // WAL mode allows simultaneous reads while a write is happening,
                    // preventing the UI from freezing when SmsReceiver writes a payment.
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
