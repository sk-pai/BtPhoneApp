package com.personal.btphoneapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.btphoneapp.domain.model.CallLogEntry
import com.personal.btphoneapp.domain.model.CallState
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
    private val dialNumberUseCase: DialNumberUseCase,
    private val getCallStateUseCase: GetCallStateUseCase,
    private val handleCallUseCase: HandleCallUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "PhoneViewModel"
    }

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<CallLogEntry>> = _callLogs.asStateFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialized")
        loadPairedDevices()
    }

    fun loadPairedDevices() {
        Log.d(TAG, "Loading paired devices")
        _pairedDevices.value = getPairedDevicesUseCase()
    }

    fun connect(address: String) {
        Log.d(TAG, "Connecting to device: $address")
        viewModelScope.launch {
            connectToDeviceUseCase(address).collect { state ->
                Log.d(TAG, "Connection state update: $state")
                _connectionState.value = state
                if (state is ConnectionState.Connected) {
                    Log.d(TAG, "Connected successfully, fetching data")
                    fetchData()
                }
            }
        }
    }

    private fun fetchData() {
        Log.d(TAG, "Fetching contacts, call logs, and call state")
        viewModelScope.launch {
            getContactsUseCase().collect { 
                Log.d(TAG, "Received ${it.size} contacts")
                _contacts.value = it 
            }
        }
        viewModelScope.launch {
            getCallLogUseCase().collect { 
                Log.d(TAG, "Received ${it.size} call logs")
                _callLogs.value = it 
            }
        }
        viewModelScope.launch {
            getCallStateUseCase().collect { 
                Log.d(TAG, "Call state changed to: $it")
                _callState.value = it 
            }
        }
    }

    fun dial(number: String) {
        Log.d(TAG, "Dialing number: $number")
        dialNumberUseCase(number)
    }

    fun acceptCall() {
        Log.d(TAG, "Accepting call")
        handleCallUseCase.accept()
    }

    fun rejectCall() {
        Log.d(TAG, "Rejecting call")
        handleCallUseCase.reject()
    }

    fun endCall() {
        Log.d(TAG, "Ending call")
        handleCallUseCase.end()
    }
}