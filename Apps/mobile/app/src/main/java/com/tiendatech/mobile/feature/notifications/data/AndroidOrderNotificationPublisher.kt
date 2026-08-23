package com.tiendatech.mobile.feature.notifications.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.tiendatech.mobile.MainActivity
import com.tiendatech.mobile.R
import com.tiendatech.mobile.feature.notifications.domain.OrderNotificationPayload
import com.tiendatech.mobile.feature.notifications.domain.OrderNotificationPublisher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

object NotificationChannels {
    const val ORDERS = "pedidos"
    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ORDERS, "Pedidos", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Actualizaciones relacionadas con tus pedidos"
        })
    }
}

@Singleton
class AndroidOrderNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context
) : OrderNotificationPublisher {
    override fun publish(payload: OrderNotificationPayload): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val intent = Intent(Intent.ACTION_VIEW, "tiendatech://orders/${payload.orderId}".toUri(), context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, payload.orderId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NotificationChannels.ORDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(payload.orderId.hashCode(), notification)
        return true
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds @Singleton abstract fun bindPublisher(implementation: AndroidOrderNotificationPublisher): OrderNotificationPublisher
}
