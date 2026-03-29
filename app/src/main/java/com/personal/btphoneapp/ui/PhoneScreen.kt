package com.personal.btphoneapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.btphoneapp.domain.model.CallLogEntry
import com.personal.btphoneapp.domain.model.Contact
import com.personal.btphoneapp.domain.repository.BluetoothDevice
import com.personal.btphoneapp.domain.repository.ConnectionState

@Composable
fun PhoneScreen(viewModel: PhoneViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            if (connectionState is ConnectionState.Connected) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        icon = { Icon(Icons.Default.Face,contentDescription = "Recents") },
                        label = { Text("Recents") }
                    )
                    NavigationBarItem(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Contacts") },
                        label = { Text("Contacts") }
                    )
                    NavigationBarItem(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        icon = { Icon(Icons.Default.Phone, contentDescription = "Dialer") },
                        label = { Text("Dialer") }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (connectionState) {
                is ConnectionState.Disconnected -> {
                    DeviceList(pairedDevices) { viewModel.connect(it.address) }
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
                        2 -> DialerScreen { viewModel.dial(it) }
                    }
                }
                is ConnectionState.Error -> {
                    Text("Error: ${(connectionState as ConnectionState.Error).message}")
                    Button(onClick = { viewModel.loadPairedDevices() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
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
                    supportingContent = { Text("${entry.type} • ${entry.timestamp}") },
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
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = number, style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(32.dp))
        // Simplified Dialer UI
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        for (i in 0 until 4) {
            Row {
                for (j in 0 until 3) {
                    val key = keys[i * 3 + j]
                    Button(
                        onClick = { number += key },
                        modifier = Modifier.size(80.dp).padding(4.dp)
                    ) {
                        Text(key)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onDial(number) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Call, contentDescription = "Call")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Call")
        }
        Button(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }) {
            Text("Delete")
        }
    }
}
