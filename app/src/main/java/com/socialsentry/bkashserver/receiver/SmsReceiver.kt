package com.socialsentry.bkashserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.socialsentry.bkashserver.domain.parser.BkashSmsParser

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private val BKASH_SENDERS = listOf("bKash", "16247", "BKASH")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val sender = message.displayOriginatingAddress
                val body = message.displayMessageBody
                
                Log.d(TAG, "Received SMS from $sender: $body")

                if (BKASH_SENDERS.any { sender.contains(it, ignoreCase = true) }) {
                    val payment = BkashSmsParser.parse(body)
                    if (payment != null) {
                        Log.d(TAG, "Parsed bKash payment: $payment")
                        // TODO: Save to Room DB and upload to Supabase
                    } else {
                        Log.d(TAG, "Failed to parse bKash SMS")
                    }
                }
            }
        }
    }
}
