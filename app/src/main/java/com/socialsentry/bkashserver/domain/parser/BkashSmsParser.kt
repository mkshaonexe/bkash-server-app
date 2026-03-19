package com.socialsentry.bkashserver.domain.parser

import java.util.regex.Pattern

data class BkashPayment(
    val amount: Double,
    val senderNumber: String,
    val trxId: String,
    val dateTime: String,
    val fee: Double,
    val balanceAfter: Double,
    val rawText: String
)

object BkashSmsParser {
    private val AMOUNT_PATTERN = Pattern.compile("Tk\\s+([\\d,.]+)\\s+from")
    private val SENDER_PATTERN = Pattern.compile("from\\s+([\\d]+)")
    private val TRXID_PATTERN = Pattern.compile("TrxID\\s+([A-Z0-9]+)\\s+at")
    private val DATE_PATTERN = Pattern.compile("at\\s+([\\d/]+\\s+[\\d:]+)")
    private val FEE_PATTERN = Pattern.compile("Fee\\s+Tk\\s+([\\d,.]+)")
    private val BALANCE_PATTERN = Pattern.compile("Balance\\s+Tk\\s+([\\d,.]+)")

    fun parse(smsText: String): BkashPayment? {
        if (!smsText.contains("received payment", ignoreCase = true)) return null

        return try {
            val amountStr = findMatch(AMOUNT_PATTERN, smsText)?.replace(",", "")
            val sender = findMatch(SENDER_PATTERN, smsText)
            val trxId = findMatch(TRXID_PATTERN, smsText)
            val dateStr = findMatch(DATE_PATTERN, smsText)
            val feeStr = findMatch(FEE_PATTERN, smsText)?.replace(",", "")
            val balanceStr = findMatch(BALANCE_PATTERN, smsText)?.replace(",", "")

            if (amountStr != null && sender != null && trxId != null && dateStr != null) {
                BkashPayment(
                    amount = amountStr.toDouble(),
                    senderNumber = sender,
                    trxId = trxId,
                    dateTime = dateStr,
                    fee = feeStr?.toDoubleOrNull() ?: 0.0,
                    balanceAfter = balanceStr?.toDoubleOrNull() ?: 0.0,
                    rawText = smsText
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findMatch(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
}
