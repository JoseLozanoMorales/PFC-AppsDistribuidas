package com.tiendatech.mobile.feature.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiendatech.mobile.MainActivity
import com.tiendatech.mobile.feature.notifications.data.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun ordersChannelExists() {
        NotificationChannels.create(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(NotificationChannels.ORDERS))
    }

    @Test fun orderDeepLinkResolvesMainActivity() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tiendatech://orders/42"))
        val component = intent.resolveActivity(context.packageManager)
        assertEquals(MainActivity::class.java.name, component?.className)
    }
}
