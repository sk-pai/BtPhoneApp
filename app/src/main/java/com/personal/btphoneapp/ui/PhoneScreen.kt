package com.personal.btphoneapp.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.btphoneapp.domain.model.CallLogEntry
import com.personal.btphoneapp.domain.model.CallState
import com.personal.btphoneapp.domain.model.Contact
import com.personal.btphoneapp.domain.repository.BluetoothDevice
import com.personal.btphoneapp.domain.repository.ConnectionState

private const val TAG = "PhoneScreen"

@Composable
fun PhoneScreen(viewModel: PhoneViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val callState by viewModel.callState.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(connectionState) {
        Log.d(TAG, "UI: Connection state changed to $connectionState")
    }

    LaunchedEffect(callState) {
        Log.d(TAG, "UI: Call state changed to $callState")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (connectionState is ConnectionState.Connected) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTabIndex == 0,
                            onClick = { 
                                Log.d(TAG, "Tab: Recents clicked")
                                selectedTabIndex = 0 
                            },
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Recents") },
                            label = { Text("Recents") }
                        )
                        NavigationBarItem(
                            selected = selectedTabIndex == 1,
                            onClick = { 
                                Log.d(TAG, "Tab: Contacts clicked")
                                selectedTabIndex = 1 
                            },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Contacts") },
                            label = { Text("Contacts") }
                        )
                        NavigationBarItem(
                            selected = selectedTabIndex == 2,
                            onClick = { 
                                Log.d(TAG, "Tab: Dialer clicked")
                                selectedTabIndex = 2 
                            },
                            icon = { Icon(Icons.Default.DateRange, contentDescription = "Dialer") },
                            label = { Text("Dialer") }
                        )
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (connectionState) {
                    is ConnectionState.Disconnected -> {
                        DeviceList(pairedDevices) { device ->
                            Log.d(TAG, "Device selected: ${device.name}")
                            viewModel.connect(device.address) 
                        }
                    }
                    is ConnectionState.Connecting -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is ConnectionState.Connected -> {
                        when (selectedTabIndex) {
                            0 -> RecentsScreen(callLogs)
                            1 -> ContactsScreen(contacts)
                            2 -> DialerScreen { number ->
                                Log.d(TAG, "Dialer initiating call to $number")
                                viewModel.dial(number) 
                            }
                        }
                    }
                    is ConnectionState.Error -> {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Error: ${(connectionState as ConnectionState.Error).message}", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { 
                                Log.d(TAG, "Retry button clicked")
                                viewModel.loadPairedDevices() 
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        if (callState !is CallState.Idle) {
            CallOverlay(
                callState = callState,
                onAccept = { 
                    Log.d(TAG, "Overlay: Accept clicked")
                    viewModel.acceptCall() 
                },
                onReject = { 
                    Log.d(TAG, "Overlay: Reject clicked")
                    viewModel.rejectCall() 
                },
                onEnd = { 
                    Log.d(TAG, "Overlay: End clicked")
                    viewModel.endCall() 
                }
            )
        }
    }
}

@Composable
fun CallOverlay(
    callState: CallState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            val title = when (callState) {
                is CallState.Incoming -> "Incoming Call"
                is CallState.Outgoing -> "Outgoing Call..."
                is CallState.Active -> "Active Call"
                else -> ""
            }
            
            val number = when (callState) {
                is CallState.Incoming -> callState.phoneNumber
                is CallState.Outgoing -> callState.phoneNumber
                is CallState.Active -> callState.phoneNumber
                else -> ""
            }

            val name = when (callState) {
                is CallState.Incoming -> callState.name
                is CallState.Outgoing -> callState.name
                is CallState.Active -> callState.name
                else -> null
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(name ?: "Unknown", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(number, style = MaterialTheme.typography.titleLarge)
                
                if (callState is CallState.Active) {
                    Text(
                        text = formatDuration(callState.durationSeconds),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (callState is CallState.Incoming) {
                    CallActionButton(icon = Icons.Default.Call, color = Color.Green, onClick = onAccept)
                    CallActionButton(icon = Icons.Default.Clear, color = Color.Red, onClick = onReject)
                } else {
                    CallActionButton(icon = Icons.Default.Clear, color = Color.Red, onClick = onEnd)
                }
            }
        }
    }
}

@Composable
fun CallActionButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp).background(color, CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
    }
}

fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

@Composable
fun DeviceList(devices: List<BluetoothDevice>, onDeviceClick: (BluetoothDevice) -> Unit) {
    LazyColumn {
        item { Text("Select a device to connect", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
        items(devices) { device ->
            ListItem(
                headlineContent = { Text(device.name) },
                supportingContent = { Text(device.address) },
                modifier = Modifier.clickable { onDeviceClick(device) }
            )
        }
    }
}

@Composable
fun RecentsScreen(callLogs: List<CallLogEntry>) {
    if (callLogs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent calls")
        }
    } else {
        LazyColumn {
            items(callLogs) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name ?: entry.phoneNumber) },
                    supportingContent = { Text("${entry.type} • ${formatDate(entry.timestamp)}") },
                    trailingContent = { Icon(Icons.Default.Call, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun ContactsScreen(contacts: List<Contact>) {
    if (contacts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No contacts found")
        }
    } else {
        LazyColumn {
            items(contacts) { contact ->
                ListItem(
                    headlineContent = { Text(contact.name) },
                    supportingContent = { Text(contact.phoneNumber) },
                    trailingContent = { Icon(Icons.Default.Call, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun DialerScreen(onDial: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(text = number, style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(bottom = 32.dp))
        
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        Column {
            for (i in 0 until 4) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (j in 0 until 3) {
                        val key = keys[i * 3 + j]
                        DialKey(key) { number += key }
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(64.dp))
            IconButton(
                onClick = { onDial(number) },
                modifier = Modifier.size(72.dp).background(Color.Green, CircleShape)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun DialKey(key: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(80.dp).padding(8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(key, fontSize = 28.sp)
        }
    }
}

fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    return java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(date)
}
