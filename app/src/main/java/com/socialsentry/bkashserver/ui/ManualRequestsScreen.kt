package com.socialsentry.bkashserver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialsentry.bkashserver.data.SupabaseClientManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ManualPaymentRequest(
    val id: String,
    val email: String?,
    val phone: String?,
    val trx_id: String,
    val amount: Double,
    val status: String,
    val created_at: String
)

@Serializable
data class ApproveRequestParams(
    val request_id: String,
    val action: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRequestsScreen() {
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<ManualPaymentRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun fetchRequests() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val data = SupabaseClientManager.client.postgrest["manual_payment_requests"]
                    .select {
                        filter {
                            eq("status", "pending")
                        }
                    }
                    .decodeList<ManualPaymentRequest>()
                withContext(Dispatchers.Main) {
                    requests = data
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchRequests()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pending Manual Requests", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (requests.isEmpty()) {
            Text("No pending requests.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(requests) { req ->
                    RequestItem(req, onAction = { action ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                SupabaseClientManager.client.functions.invoke("approve-manual-verification", 
                                    body = ApproveRequestParams(request_id = req.id, action = action)
                                )
                                fetchRequests()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun RequestItem(request: ManualPaymentRequest, onAction: (String) -> Unit) {
    var isProcessing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TrxID: ${request.trx_id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Amount: ৳${request.amount}", color = Color.Green, fontWeight = FontWeight.Medium)
            if (!request.email.isNullOrEmpty()) Text("Email: ${request.email}", fontSize = 14.sp)
            if (!request.phone.isNullOrEmpty()) Text("Phone: ${request.phone}", fontSize = 14.sp)
            Text("Time: ${request.created_at}", fontSize = 12.sp, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { 
                        isProcessing = true
                        onAction("decline") 
                    }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Decline")
                    }
                    Button(onClick = { 
                        isProcessing = true
                        onAction("approve") 
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve")
                    }
                }
            }
        }
    }
}
