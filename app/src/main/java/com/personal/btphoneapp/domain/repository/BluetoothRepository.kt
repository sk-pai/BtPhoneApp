package com.personal.btphoneapp.domain.repository

import com.personal.btphoneapp.domain.model.CallLogEntry
import com.personal.btphoneapp.domain.model.CallState
import com.personal.btphoneapp.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface BluetoothRepository {
    fun getPairedDevices(): List<BluetoothDevice>
    fun connectToDevice(address: String): Flow<ConnectionState>
    fun getContacts(): Flow<List<Contact>>
    fun getCallLog(): Flow<List<CallLogEntry>>
    fun getCallState(): Flow<CallState>
    fun dialNumber(number: String)
    fun acceptCall()
    fun rejectCall()
    fun endCall()
    fun toggleSpeaker(on: Boolean)
    fun cleanup()
}

data class BluetoothDevice(val name: String, val address: String)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}