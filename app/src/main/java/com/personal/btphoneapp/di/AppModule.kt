package com.personal.btphoneapp.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.telephony.TelephonyManager
import com.personal.btphoneapp.data.repository.BluetoothRepositoryImpl
import com.personal.btphoneapp.domain.repository.BluetoothRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(
        bluetoothRepositoryImpl: BluetoothRepositoryImpl
    ): BluetoothRepository

    companion object {
        @Provides
        @Singleton
        fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        @Provides
        @Singleton
        fun provideBluetoothAdapter(manager: BluetoothManager): BluetoothAdapter? = manager.adapter

        @Provides
        @Singleton
        fun provideTelephonyManager(@ApplicationContext context: Context): TelephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
}