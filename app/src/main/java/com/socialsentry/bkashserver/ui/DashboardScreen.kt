package com.socialsentry.bkashserver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialsentry.bkashserver.data.local.PaymentEntity
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📱 bKash SMS Reader") })
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
            StatusCard()
            Spacer(modifier = Modifier.height(16.dp))
            ConfigWarning()
            SummaryCard(payments)
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
            PaymentList(payments)
        }
    }
    
    if (showManualEntry) {
        ManualEntryDialog(onDismiss = { showManualEntry = false })
    }
}

@Composable
fun StatusCard() {
    val context = LocalContext.current
    var uptimeStatus by remember { mutableStateOf(com.socialsentry.bkashserver.domain.ServiceTracker.getUptimeStatus(context)) }

    LaunchedEffect(Unit) {
        while(true) {
            uptimeStatus = com.socialsentry.bkashserver.domain.ServiceTracker.getUptimeStatus(context)
            kotlinx.coroutines.delay(5000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uptimeStatus,
                color = if (uptimeStatus.contains("🟢")) Color(0xFF4CAF50) else Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun SummaryCard(payments: List<PaymentEntity>) {
    val totalAmount = payments.sumOf { it.amount }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("bn", "BD"))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today's Payments: ${payments.size}", fontSize = 18.sp)
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
