package com.personal.btphoneapp.domain.usecase

import com.personal.btphoneapp.domain.repository.BluetoothRepository
import javax.inject.Inject

class DialNumberUseCase @Inject constructor(
    private val repository: BluetoothRepository
) {
    operator fun invoke(number: String) = repository.dialNumber(number)
}