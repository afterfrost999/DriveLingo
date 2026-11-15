package com.drivelingo.app

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.drivelingo.BuildConfig
import com.drivelingo.feature.vehicleconsole.DebugVehicleReceiver

class DriveLingoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.SHOW_VEHICLE_CONSOLE) registerDebugReceiver()
    }

    /** 디버그 빌드에서만 adb 로 Mock 차량 신호를 바꿀 수 있게 한다(docs/testing.md). */
    private fun registerDebugReceiver() {
        val receiver = DebugVehicleReceiver(container)
        val filter = IntentFilter(DebugVehicleReceiver.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }
}
