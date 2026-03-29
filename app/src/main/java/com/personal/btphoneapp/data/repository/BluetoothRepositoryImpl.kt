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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.personal.btphoneapp.domain.model.CallLogEntry
import com.personal.btphoneapp.domain.model.CallType
import com.personal.btphoneapp.domain.model.Contact
import com.personal.btphoneapp.domain.repository.BluetoothDevice
import com.personal.btphoneapp.domain.repository.BluetoothRepository
import com.personal.btphoneapp.domain.repository.ConnectionState
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
    @ApplicationContext private val context: Context
) : BluetoothRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val contentResolver: ContentResolver = context.contentResolver

    private var bluetoothHeadset: BluetoothHeadset? = null

    init {
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HEADSET) {
                    bluetoothHeadset = proxy as BluetoothHeadset
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HEADSET) {
                    bluetoothHeadset = null
                }
            }
        }, BluetoothProfile.HEADSET)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true // Legacy permissions are normal permissions
        }
    }

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothConnectPermission()) return emptyList()
        
        return try {
            bluetoothAdapter?.bondedDevices?.map {
                BluetoothDevice(it.name ?: "Unknown", it.address)
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(address: String): Flow<ConnectionState> = callbackFlow {
        if (!hasBluetoothConnectPermission()) {
            trySend(ConnectionState.Error("Bluetooth permission missing"))
            close()
            return@callbackFlow
        }

        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        }

        if (device == null) {
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

        // Initial state check
        try {
            val currentState = bluetoothHeadset?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
            when (currentState) {
                BluetoothProfile.STATE_CONNECTED -> trySend(ConnectionState.Connected)
                BluetoothProfile.STATE_CONNECTING -> trySend(ConnectionState.Connecting)
                else -> {
                    trySend(ConnectionState.Connecting)
                    if (bluetoothAdapter?.bondedDevices?.any { it.address == address } == true) {
                        trySend(ConnectionState.Connected)
                    }
                }
            }
        } catch (e: SecurityException) {
            trySend(ConnectionState.Error("Security Exception: Permission revoked"))
        }

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.flowOn(Dispatchers.IO)

    override fun getContacts(): Flow<List<Contact>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { trySend(fetchLocalContacts()) }
            }
        }
        
        contentResolver.registerContentObserver(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            true,
            observer
        )

        trySend(fetchLocalContacts())

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    override fun getCallLog(): Flow<List<CallLogEntry>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { trySend(fetchLocalCallLog()) }
            }
        }

        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            observer
        )

        trySend(fetchLocalCallLog())

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun fetchLocalContacts(): List<Contact> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()

        val contactsList = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    contactsList.add(
                        Contact(
                            id = cursor.getString(idIndex),
                            name = cursor.getString(nameIndex) ?: "Unknown",
                            phoneNumber = cursor.getString(numberIndex) ?: ""
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return contactsList
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocalCallLog(): List<CallLogEntry> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()

        val callLogs = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)

                while (cursor.moveToNext()) {
                    val type = when (cursor.getInt(typeIndex)) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        else -> CallType.INCOMING
                    }

                    callLogs.add(
                        CallLogEntry(
                            id = cursor.getString(idIndex),
                            name = cursor.getString(nameIndex),
                            phoneNumber = cursor.getString(numberIndex) ?: "",
                            timestamp = cursor.getLong(dateIndex),
                            duration = cursor.getInt(durationIndex),
                            type = type
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return callLogs
    }

    @SuppressLint("MissingPermission")
    override fun dialNumber(number: String) {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) return
        
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}