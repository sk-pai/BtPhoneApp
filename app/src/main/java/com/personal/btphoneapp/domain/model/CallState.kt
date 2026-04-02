package com.personal.btphoneapp.domain.model

sealed class CallState {
    object Idle : CallState()
    data class Incoming(val phoneNumber: String, val name: String?) : CallState()
    data class Outgoing(val phoneNumber: String, val name: String?) : CallState()
    data class Active(val phoneNumber: String, val name: String?, val durationSeconds: Int) : CallState()
    object Disconnected : CallState()
}