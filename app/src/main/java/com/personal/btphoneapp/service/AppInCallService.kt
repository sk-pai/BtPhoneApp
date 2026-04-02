package com.personal.btphoneapp.service

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.personal.btphoneapp.domain.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}")
        currentCall = call
        updateCallState(call)
        call.registerCallback(callCallback)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
            _callStateFlow.value = CallState.Idle
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "Call state changed: $state")
            updateCallState(call)
        }
    }

    private fun updateCallState(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val newState = when (call.state) {
            Call.STATE_RINGING -> CallState.Incoming(number, null)
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.Outgoing(number, null)
            Call.STATE_ACTIVE -> CallState.Active(number, null, 0)
            Call.STATE_DISCONNECTED -> CallState.Idle
            else -> _callStateFlow.value
        }
        _callStateFlow.value = newState
    }

    companion object {
        private const val TAG = "AppInCallService"
        private var currentCall: Call? = null
        private val _callStateFlow = MutableStateFlow<CallState>(CallState.Idle)
        val callStateFlow = _callStateFlow.asStateFlow()
        
        fun accept() {
            Log.d(TAG, "Accepting call")
            currentCall?.answer(0)
        }
        
        fun reject() {
            Log.d(TAG, "Rejecting call")
            currentCall?.disconnect()
        }
        
        fun end() {
            Log.d(TAG, "Ending call")
            currentCall?.disconnect()
        }
    }
}