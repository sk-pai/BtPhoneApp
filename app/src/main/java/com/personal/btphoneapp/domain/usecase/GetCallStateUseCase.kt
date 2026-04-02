package com.personal.btphoneapp.domain.usecase

import com.personal.btphoneapp.domain.repository.BluetoothRepository
import javax.inject.Inject

class GetCallStateUseCase @Inject constructor(
    private val repository: BluetoothRepository
) {
    operator fun invoke() = repository.getCallState()
}