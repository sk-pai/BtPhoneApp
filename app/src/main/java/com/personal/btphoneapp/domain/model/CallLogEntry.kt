package com.personal.btphoneapp.domain.model

data class CallLogEntry(
    val id: String,
    val name: String?,
    val phoneNumber: String,
    val timestamp: Long,
    val duration: Int,
    val type: CallType
)

enum class CallType {
    INCOMING, OUTGOING, MISSED
}