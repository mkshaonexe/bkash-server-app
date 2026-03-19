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
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(payments: List<PaymentEntity>) {
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
            SummaryCard(payments)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Recent Payments", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(2.dp)
            ) {
                // Circle indicator
            }
            Text(
                text = "● Running (24/7)",
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
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
                "Total Received: ৳${String.format("%.2(f)", totalAmount)}",
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

@Composable
fun ManualEntryDialog(onDismiss: () -> Unit) {
    // Basic dialog placeholder for now
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Entry") },
        text = { Text("Manual entry feature coming in next step.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
