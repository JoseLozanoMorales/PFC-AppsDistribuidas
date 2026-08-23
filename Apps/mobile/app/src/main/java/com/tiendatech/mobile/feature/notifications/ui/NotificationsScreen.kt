package com.tiendatech.mobile.feature.notifications.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.tiendatech.mobile.feature.notifications.domain.OrderNotificationPayload
import com.tiendatech.mobile.feature.notifications.domain.OrderNotificationPublisher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val publisher: OrderNotificationPublisher) : ViewModel() {
    fun sendDemo(orderId: Long): Boolean = publisher.publish(OrderNotificationPayload(orderId, "Demostración local", "Esta es una notificación local de prueba de TiendaTech.", true))
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var orderText by remember { mutableStateOf("1") }
    var message by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; message = if (it) "Permiso concedido" else "Permiso no concedido" }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("← Volver", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Text("Notificaciones de pedidos", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Integración remota pendiente", style = MaterialTheme.typography.titleMedium)
            Text("No existe configuración Firebase, registro de dispositivo ni eventos de estado en el backend. Esta pantalla solo demuestra el canal y el enlace al pedido mediante una notificación local.")
        } }
        if (!granted && Build.VERSION.SDK_INT >= 33) Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir notificaciones") }
        OutlinedTextField(orderText, { orderText = it.filter(Char::isDigit).take(12) }, label = { Text("Número de orden de demostración") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { val id = orderText.toLongOrNull(); message = if (id == null || id <= 0) "Ingresa un número de orden válido" else if (viewModel.sendDemo(id)) "Notificación local enviada" else "Debes permitir las notificaciones" },
            enabled = granted,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Enviar demostración local") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
