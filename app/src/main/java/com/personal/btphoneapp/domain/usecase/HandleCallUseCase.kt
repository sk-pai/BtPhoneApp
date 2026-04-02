package com.personal.btphoneapp.domain.usecase

import com.personal.btphoneapp.domain.repository.BluetoothRepository
import javax.inject.Inject

class HandleCallUseCase @Inject constructor(
    private val repository: BluetoothRepository
) {
    fun accept() = repository.acceptCall()
    fun reject() = repository.rejectCall()
    fun end() = repository.endCall()
}