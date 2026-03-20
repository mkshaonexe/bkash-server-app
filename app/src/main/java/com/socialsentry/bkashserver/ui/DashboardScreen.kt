package com.socialsentry.bkashserver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialsentry.bkashserver.data.local.PaymentEntity
import com.socialsentry.bkashserver.domain.ServiceLog
import com.socialsentry.bkashserver.domain.ServiceTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(payments: List<PaymentEntity>) {
    val context = LocalContext.current
    var showManualEntry by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Today") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📱 bKash SMS Reader") },
                actions = {
                    IconButton(onClick = { showLogsDialog = true }) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "View Logs")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showManualEntry = true }) {
                Icon(Icons.Default.Add, contentDescription = "Manual Entry")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val calendar = Calendar.getInstance()
            val filteredPayments = payments.filter { payment ->
                when (selectedFilter) {
                    "Today" -> {
                        calendar.timeInMillis = System.currentTimeMillis()
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        payment.createdAt >= calendar.timeInMillis
                    }
                    "Week" -> {
                        calendar.timeInMillis = System.currentTimeMillis()
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        payment.createdAt >= calendar.timeInMillis
                    }
                    "Month" -> {
                        calendar.timeInMillis = System.currentTimeMillis()
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        payment.createdAt >= calendar.timeInMillis
                    }
                    else -> true
                }
            }

            // Filter row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Today", "Week", "Month", "All").forEach { filterOpt ->
                    FilterChip(
                        selected = selectedFilter == filterOpt,
                        onClick = { selectedFilter = filterOpt },
                        label = { Text(filterOpt) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            ConfigWarning()
            SummaryCard(filteredPayments, selectedFilter)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Payments", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                if (payments.any { it.uploadStatus == "FAILED" || it.uploadStatus == "PENDING" }) {
                    TextButton(onClick = {
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            com.socialsentry.bkashserver.data.PaymentUploader.uploadPendingPayments(context)
                        }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry All")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PaymentList(filteredPayments)
        }
    }
    
    if (showManualEntry) {
        ManualEntryDialog(onDismiss = { showManualEntry = false })
    }

    if (showLogsDialog) {
        ServiceLogsDialog(onDismiss = { showLogsDialog = false })
    }
}

@Composable
fun SummaryCard(payments: List<PaymentEntity>, filterPeriod: String) {
    val totalAmount = payments.sumOf { it.amount }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("bn", "BD"))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$filterPeriod's Payments: ${payments.size}", fontSize = 18.sp)
            Text(
                "Total Received: ৳${String.format("%.2f", totalAmount)}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PaymentList(payments: List<PaymentEntity>) {
    LazyColumn {
        items(payments) { payment ->
            PaymentItem(payment)
            HorizontalDivider()
        }
    }
}

@Composable
fun PaymentItem(payment: PaymentEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("৳${payment.amount} from ${payment.senderNumber}", fontWeight = FontWeight.Bold)
            Text("TrxID: ${payment.trxId}", fontSize = 12.sp, color = Color.Gray)
            Text("${payment.dateTime}", fontSize = 12.sp, color = Color.Gray)
        }
        
        StatusIndicator(payment.uploadStatus)
    }
}

@Composable
fun StatusIndicator(status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (icon, color, text) = when (status) {
            "UPLOADED" -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "Uploaded")
            "FAILED" -> Triple(Icons.Default.Error, Color.Red, "Failed")
            else -> Triple(Icons.Default.Sync, Color.Gray, "Pending")
        }
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var trxId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var senderNumber by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf<String>(getNowFormatted()) }
    var isUploading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Manual Payment Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = trxId,
                    onValueChange = { trxId = it },
                    label = { Text("TrxID") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (৳)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderNumber,
                    onValueChange = { senderNumber = it },
                    label = { Text("Sender Number") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateTime,
                    onValueChange = { dateTime = it },
                    label = { Text("Date & Time (DD/MM/YYYY HH:MM)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trxId.isNotEmpty() && amount.isNotEmpty() && senderNumber.isNotEmpty()) {
                        isUploading = true
                        val database = com.socialsentry.bkashserver.data.local.PaymentDatabase.getDatabase(context)
                        val entity = com.socialsentry.bkashserver.data.local.PaymentEntity(
                            trxId = trxId,
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            senderNumber = senderNumber,
                            dateTime = dateTime,
                            fee = 0.0,
                            balanceAfter = 0.0,
                            rawText = "MANUAL_ENTRY",
                            uploadStatus = "PENDING"
                        )
                        
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            database.paymentDao().insertPayment(entity)
                            com.socialsentry.bkashserver.data.PaymentUploader.uploadPendingPayments(context)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isUploading = false
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Upload to Server")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfigWarning() {
    val isConfigured = com.socialsentry.bkashserver.BuildConfig.SUPABASE_URL.isNotEmpty()
            && com.socialsentry.bkashserver.BuildConfig.SUPABASE_SERVICE_ROLE_KEY.isNotEmpty()

    if (!isConfigured) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Configuration Missing", color = Color.Red, fontWeight = FontWeight.Bold)
                Text(
                    "Please add SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY to your local.properties file.",
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
        }
    }
}

private fun getNowFormatted(): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceLogsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logs = remember { com.socialsentry.bkashserver.domain.ServiceTracker.getLogHistory(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🕒 Service Uptime History") },
        text = {
            if (logs.isEmpty()) {
                Text("No logs available.", color = Color.Gray)
            } else {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(logs.size) { index ->
                        val log = logs[index]
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🟢 Started: ${log.startTime}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (log.stopTime != null) {
                                    Text(
                                        text = "🔴 Stopped: ${log.stopTime}",
                                        color = Color.Red,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Reason: ${log.reason}",
                                        color = Color.DarkGray,
                                        fontSize = 12.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                } else {
                                    Text(
                                        text = "⚡ Currently Running",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
