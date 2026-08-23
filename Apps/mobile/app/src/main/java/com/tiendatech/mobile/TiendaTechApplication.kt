package com.tiendatech.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.tiendatech.mobile.feature.notifications.data.NotificationChannels

@HiltAndroidApp
class TiendaTechApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}
