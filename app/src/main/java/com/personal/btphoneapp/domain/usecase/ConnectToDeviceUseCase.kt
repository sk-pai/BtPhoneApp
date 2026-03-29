package com.personal.btphoneapp.domain.usecase

import com.personal.btphoneapp.domain.repository.BluetoothRepository
import javax.inject.Inject

class ConnectToDeviceUseCase @Inject constructor(
    private val repository: BluetoothRepository
) {
    operator fun invoke(address: String) = repository.connectToDevice(address)
}