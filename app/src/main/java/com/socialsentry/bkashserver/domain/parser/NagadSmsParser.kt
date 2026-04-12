package com.socialsentry.bkashserver.domain.parser

import java.util.regex.Pattern

data class NagadPayment(
    val amount: Double,
    val senderNumber: String,
    val trxId: String,
    val dateTime: String,
    val balanceAfter: Double,
    val rawText: String
)

/**
 * Parses Nagad "Money Received" SMS format.
 *
 * Example:
 * Money Received.
 * Amount: Tk 10.00
 * Sender: 01969865701
 * Ref: shaon
 * TxnID: 755NFG4S
 * Balance: Tk 10.00
 * 01/04/2026 07:26
 */
object NagadSmsParser {

    private val AMOUNT_PATTERN = Pattern.compile("Amount:\\s*Tk\\s+([\\d,.]+)", Pattern.CASE_INSENSITIVE)
    private val SENDER_PATTERN = Pattern.compile("Sender:\\s*([\\d]+)", Pattern.CASE_INSENSITIVE)
    private val TXNID_PATTERN = Pattern.compile("TxnID:\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE)
    private val BALANCE_PATTERN = Pattern.compile("Balance:\\s*Tk\\s+([\\d,.]+)", Pattern.CASE_INSENSITIVE)
    private val DATE_PATTERN = Pattern.compile("(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})")

    fun parse(smsText: String): NagadPayment? {
        // Must contain "Money Received" (Nagad signature)
        if (!smsText.contains("Money Received", ignoreCase = true)) return null

        return try {
            val amountStr = findMatch(AMOUNT_PATTERN, smsText)?.replace(",", "")
            val sender = findMatch(SENDER_PATTERN, smsText)
            val trxId = findMatch(TXNID_PATTERN, smsText)
            val balanceStr = findMatch(BALANCE_PATTERN, smsText)?.replace(",", "")
            val dateStr = findMatch(DATE_PATTERN, smsText)

            if (amountStr != null && sender != null && trxId != null && dateStr != null) {
                NagadPayment(
                    amount = amountStr.toDouble(),
                    senderNumber = sender,
                    trxId = trxId,
                    dateTime = dateStr,
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
        return if (matcher.find()) matcher.group(1) else null
    }
}
