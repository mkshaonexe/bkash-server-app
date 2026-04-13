package com.socialsentry.bkashserver.domain.parser

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object BkashNotificationParser {

    // Matches: TK 1.0 received as payment from 0173XXX8081
    private val AMOUNT_SENDER_PATTERN = Pattern.compile("TK\\s+([\\d,.]+)\\s+received as payment from\\s+([\\wX]+)", Pattern.CASE_INSENSITIVE)

    fun parse(title: String, text: String): BkashPayment? {
        if (!title.contains("Payment Received", ignoreCase = true)) {
            return null
        }

        return try {
            val matcher = AMOUNT_SENDER_PATTERN.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "")
                val sender = matcher.group(2)
                
                if (amountStr != null && sender != null) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val currentDate = sdf.format(Date())
                    
                    // Generate pseudo trxId for push notifications
                    val pseudoTrxId = "PUSH_" + System.currentTimeMillis()

                    BkashPayment(
                        amount = amountStr.toDouble(),
                        senderNumber = sender,
                        trxId = pseudoTrxId,
                        dateTime = currentDate,
                        fee = 0.0,
                        balanceAfter = 0.0,
                        rawText = text,
                        paymentSource = "bkash_merchant_push"
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
