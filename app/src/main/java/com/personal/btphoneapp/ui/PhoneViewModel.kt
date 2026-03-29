package com.personal.btphoneapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.btphoneapp.domain.model.CallLogEntry
import com.personal.btphoneapp.domain.model.Contact
import com.personal.btphoneapp.domain.repository.BluetoothDevice
import com.personal.btphoneapp.domain.repository.ConnectionState
import com.personal.btphoneapp.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhoneViewModel @Inject constructor(
    private val getPairedDevicesUseCase: GetPairedDevicesUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val getContactsUseCase: GetContactsUseCase,
    private val getCallLogUseCase: GetCallLogUseCase,
    private val dialNumberUseCase: DialNumberUseCase
) : ViewModel() {

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<CallLogEntry>> = _callLogs.asStateFlow()

    init {
        loadPairedDevices()
    }

    fun loadPairedDevices() {
        _pairedDevices.value = getPairedDevicesUseCase()
    }

    fun connect(address: String) {
        viewModelScope.launch {
            connectToDeviceUseCase(address).collect { state ->
                _connectionState.value = state
                if (state is ConnectionState.Connected) {
                    fetchData()
                }
            }
        }
    }

    private fun fetchData() {
        viewModelScope.launch {
            getContactsUseCase().collect { _contacts.value = it }
        }
        viewModelScope.launch {
            getCallLogUseCase().collect { _callLogs.value = it }
        }
    }

    fun dial(number: String) {
        dialNumberUseCase(number)
    }
}