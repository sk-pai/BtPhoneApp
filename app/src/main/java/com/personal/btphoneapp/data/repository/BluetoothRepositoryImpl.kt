package com.personal.btphoneapp.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.personal.btphoneapp.domain.model.*
import com.personal.btphoneapp.domain.repository.BluetoothDevice
import com.personal.btphoneapp.domain.repository.BluetoothRepository
import com.personal.btphoneapp.domain.repository.ConnectionState
import com.personal.btphoneapp.service.AppInCallService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) : BluetoothRepository {

    companion object {
        private const val TAG = "BluetoothRepoImpl"
    }

    private val contentResolver: ContentResolver = context.contentResolver
    private var bluetoothHeadset: BluetoothHeadset? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                Log.d(TAG, "Headset profile connected")
                bluetoothHeadset = proxy as BluetoothHeadset
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                Log.d(TAG, "Headset profile disconnected")
                bluetoothHeadset = null
            }
        }
    }

    init {
        Log.d(TAG, "Initializing BluetoothRepositoryImpl")
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
    }

    private fun hasPermission(permission: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Permission not granted: $permission")
        }
        return granted
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        Log.d(TAG, "Fetching paired devices")
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
            return emptyList()
        }
        return try {
            val devices = bluetoothAdapter?.bondedDevices?.map {
                BluetoothDevice(it.name ?: "Unknown", it.address)
            } ?: emptyList()
            Log.d(TAG, "Found ${devices.size} paired devices")
            devices
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while fetching paired devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(address: String): Flow<ConnectionState> = callbackFlow {
        Log.d(TAG, "Connecting to device: $address")
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission for connection")
            trySend(ConnectionState.Error("Bluetooth permission missing"))
            close()
            return@callbackFlow
        }

        val device = try { 
            bluetoothAdapter?.getRemoteDevice(address) 
        } catch (e: Exception) { 
            Log.e(TAG, "Error getting remote device for address: $address", e)
            null 
        }

        if (device == null) {
            Log.w(TAG, "Device not found for address: $address")
            trySend(ConnectionState.Error("Device not found"))
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    val receivedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (receivedDevice?.address == address) {
                        Log.d(TAG, "Connection state changed for $address: $state")
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> trySend(ConnectionState.Connected)
                            BluetoothProfile.STATE_CONNECTING -> trySend(ConnectionState.Connecting)
                            BluetoothProfile.STATE_DISCONNECTED -> trySend(ConnectionState.Disconnected)
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))
        
        try {
            val currentState = bluetoothHeadset?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
            Log.d(TAG, "Current connection state for $address: $currentState")
            if (currentState == BluetoothProfile.STATE_CONNECTED) trySend(ConnectionState.Connected)
            else trySend(ConnectionState.Connecting)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission revoked during connection check", e)
            trySend(ConnectionState.Error("Permission revoked"))
        }

        awaitClose { 
            Log.d(TAG, "Closing connection flow for $address")
            context.unregisterReceiver(receiver) 
        }
    }.flowOn(Dispatchers.IO)

    override fun getContacts(): Flow<List<Contact>> = callbackFlow {
        Log.d(TAG, "Observing contacts")
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { 
                Log.d(TAG, "Contacts changed, refetching...")
                launch { trySend(fetchLocalContacts()) } 
            }
        }
        contentResolver.registerContentObserver(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, true, observer)
        trySend(fetchLocalContacts())
        awaitClose { 
            Log.d(TAG, "Stopping contact observation")
            contentResolver.unregisterContentObserver(observer) 
        }
    }.flowOn(Dispatchers.IO)

    override fun getCallLog(): Flow<List<CallLogEntry>> = callbackFlow {
        Log.d(TAG, "Observing call logs")
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { 
                Log.d(TAG, "Call logs changed, refetching...")
                launch { trySend(fetchLocalCallLog()) } 
            }
        }
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        trySend(fetchLocalCallLog())
        awaitClose { 
            Log.d(TAG, "Stopping call log observation")
            contentResolver.unregisterContentObserver(observer) 
        }
    }.flowOn(Dispatchers.IO)

    override fun getCallState(): Flow<CallState> = callbackFlow {
        Log.d(TAG, "Observing call state via InCallService")
        val job = launch {
            AppInCallService.callStateFlow.collect { state ->
                Log.d(TAG, "Repository: Received call state: $state")
                val enrichedState = when (state) {
                    is CallState.Incoming -> state.copy(name = resolveContactName(state.phoneNumber))
                    is CallState.Outgoing -> state.copy(name = resolveContactName(state.phoneNumber))
                    is CallState.Active -> state.copy(name = resolveContactName(state.phoneNumber))
                    else -> state
                }
                Log.d(TAG, "Repository: Emitting enriched call state: $enrichedState")
                trySend(enrichedState)
            }
        }

        awaitClose { 
            Log.d(TAG, "Stopping call state observation")
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveContactName(phoneNumber: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        return try {
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    Log.d(TAG, "Resolved name for $phoneNumber: $name")
                    name
                } else {
                    Log.d(TAG, "No name found for $phoneNumber")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact name for $phoneNumber", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocalContacts(): List<Contact> {
        Log.d(TAG, "Fetching local contacts")
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val contactsList = mutableListOf<Contact>()
        try {
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    contactsList.add(Contact(cursor.getString(idIdx), cursor.getString(nameIdx) ?: "Unknown", cursor.getString(numIdx) ?: ""))
                }
            }
            Log.d(TAG, "Fetched ${contactsList.size} contacts")
        } catch (e: Exception) { 
            Log.e(TAG, "Error fetching local contacts", e)
        }
        return contactsList
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocalCallLog(): List<CallLogEntry> {
        Log.d(TAG, "Fetching local call logs")
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val callLogs = mutableListOf<CallLogEntry>()
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID, CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE), null, null, CallLog.Calls.DATE + " DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    val type = when (cursor.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        else -> CallType.INCOMING
                    }
                    callLogs.add(CallLogEntry(cursor.getString(idIdx), cursor.getString(nameIdx), cursor.getString(numIdx) ?: "", cursor.getLong(dateIdx), cursor.getInt(durIdx), type))
                }
            }
            Log.d(TAG, "Fetched ${callLogs.size} call log entries")
        } catch (e: Exception) { 
            Log.e(TAG, "Error fetching local call logs", e)
        }
        return callLogs
    }

    override fun dialNumber(number: String) {
        Log.d(TAG, "Dialing number: $number")
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            Log.w(TAG, "Missing CALL_PHONE permission")
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        } catch (e: Exception) { 
            Log.e(TAG, "Error initiating call to $number", e)
        }
    }

    override fun acceptCall() {
        Log.d(TAG, "Accepting call via AppInCallService")
        AppInCallService.accept()
    }

    override fun rejectCall() {
        Log.d(TAG, "Rejecting call via AppInCallService")
        AppInCallService.reject()
    }

    override fun endCall() {
        Log.d(TAG, "Ending call via AppInCallService")
        AppInCallService.end()
    }

    override fun toggleSpeaker(on: Boolean) {
        Log.d(TAG, "Toggling speaker: $on")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = on
        audioManager.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
    }

    override fun cleanup() {
        Log.d(TAG, "Cleaning up BluetoothRepositoryImpl")
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
    }
}