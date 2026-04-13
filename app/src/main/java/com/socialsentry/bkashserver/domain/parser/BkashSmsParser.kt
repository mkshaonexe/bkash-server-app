package com.socialsentry.bkashserver.domain.parser

import java.util.regex.Pattern

/**
 * Represents a parsed bKash payment.
 * @param paymentSource Either "bkash_merchant" or "bkash_personal"
 */
data class BkashPayment(
    val amount: Double,
    val senderNumber: String,
    val trxId: String,
    val dateTime: String,
    val fee: Double,
    val balanceAfter: Double,
    val rawText: String,
    val paymentSource: String = "bkash_merchant"
)

/**
 * Parses bKash incoming payment SMS.
 *
 * Merchant format (auto via bKash link):
 * "You have received payment Tk 549.00 from 01757840664. Fee Tk 0.00. Balance Tk 8,210.56. TrxID DDC03X8Z8I at 12/04/2026 16:39"
 *
 * Personal format (manual Send Money):
 * "You have received Tk 100.00 from 01710459562. Ref . Fee Tk 0.00. Balance Tk 138.34. TrxID DDC947L61P at 12/04/2026 20:18"
 *
 * Key difference: merchant SMS contains "received payment", personal contains "received Tk" (no "payment").
 */
object BkashSmsParser {
    // Merchant: "received payment Tk 549.00"
    private val MERCHANT_AMOUNT_PATTERN = Pattern.compile("received payment Tk\\s+([\\d,.]+)", Pattern.CASE_INSENSITIVE)
    // Personal: "received Tk 100.00" (when NOT followed by "payment")
    private val PERSONAL_AMOUNT_PATTERN = Pattern.compile("received Tk\\s+([\\d,.]+)", Pattern.CASE_INSENSITIVE)
    // Gateway: "Payment of Tk 119.00 to Provider..."
    private val GATEWAY_AMOUNT_PATTERN = Pattern.compile("Payment of Tk\\s+([\\d,.]+)", Pattern.CASE_INSENSITIVE)

    private val SENDER_PATTERN = Pattern.compile("from\\s+([\\d]+)")
    private val GATEWAY_PROVIDER_PATTERN = Pattern.compile("to Provider\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE)

    private val TRXID_PATTERN = Pattern.compile("TrxID\\s+([A-Z0-9]+)\\s+at")
    private val DATE_PATTERN = Pattern.compile("at\\s+([\\d/]+\\s+[\\d:]+)")
    private val FEE_PATTERN = Pattern.compile("Fee Tk\\s+([\\d,.]+)")
    private val BALANCE_PATTERN = Pattern.compile("Balance Tk\\s+([\\d,.]+)")

    fun parse(smsText: String): BkashPayment? {
        // Must be a "received" payment SMS OR a "Payment of Tk... successful" gateway SMS
        val isGateway = smsText.contains("Payment of Tk", ignoreCase = true) && smsText.contains("successful", ignoreCase = true)
        val isReceived = smsText.contains("received", ignoreCase = true)

        if (!isReceived && !isGateway) return null

        return try {
            val isMerchant = smsText.contains("received payment", ignoreCase = true)

            val amountStr = when {
                isGateway -> findMatch(GATEWAY_AMOUNT_PATTERN, smsText)?.replace(",", "")
                isMerchant -> findMatch(MERCHANT_AMOUNT_PATTERN, smsText)?.replace(",", "")
                else -> findMatch(PERSONAL_AMOUNT_PATTERN, smsText)?.replace(",", "")
            }

            val sender = when {
                isGateway -> {
                    val rawProvider = findMatch(GATEWAY_PROVIDER_PATTERN, smsText)
                    // If rawProvider contains a number, extract it, otherwise use as is
                    rawProvider?.let { provider ->
                        val numberMatcher = Pattern.compile("(\\d{10,})").matcher(provider)
                        if (numberMatcher.find()) numberMatcher.group(1) else provider
                    } ?: "GATEWAY"
                }
                else -> findMatch(SENDER_PATTERN, smsText)
            }

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
                    rawText = smsText,
                    paymentSource = if (isMerchant || isGateway) "bkash_merchant" else "bkash_personal"
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
