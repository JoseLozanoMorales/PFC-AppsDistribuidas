package com.tiendatech.mobile.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiendatech.mobile.core.designsystem.component.TiendaTechErrorState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechLoadingState
import com.tiendatech.mobile.feature.account.data.PaymentExpiration
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CheckoutScreen(
    userId: Long,
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onCatalog: () -> Unit,
    onOrder: (Long) -> Unit,
    onUnauthorized: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(userId) { viewModel.load(userId, onUnauthorized) }
    if (state.loading) { TiendaTechLoadingState("Preparando compra", Modifier.fillMaxSize()); return }
    state.confirmation?.let { order ->
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("✓ Compra registrada", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Orden #${order.id}", style = MaterialTheme.typography.titleLarge); Text("Total: ${money(order.total)}"); Text("Fecha: ${order.date}")
            Button(onClick = { onOrder(order.id) }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Ver pedido") }
            Button(onClick = onCatalog, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Seguir comprando") }
        }; return
    }
    val account = state.account
    val cart = state.cart
    if (account == null || cart == null) { TiendaTechErrorState(state.error ?: "No se pudo preparar el checkout", { viewModel.load(userId, onUnauthorized) }, Modifier.fillMaxSize()); return }
    val enabledAddresses = account.addresses.filter { it.enabled }; val enabledMethods = account.paymentMethods.filter { it.enabled }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Carrito") }; Text("Finalizar compra", style = MaterialTheme.typography.headlineMedium)
        Text("Dirección de entrega", style = MaterialTheme.typography.titleLarge)
        if (enabledAddresses.isEmpty()) TextButton(onClick = onAccount) { Text("Crear una dirección") }
        enabledAddresses.forEach { address -> SelectCard(state.selectedAddressId == address.id, { viewModel.selectAddress(address.id) }) { Text(address.street); Text(listOfNotNull(address.city, address.province).joinToString(" · ")) } }
        Text("Método de pago", style = MaterialTheme.typography.titleLarge)
        if (enabledMethods.isEmpty()) TextButton(onClick = onAccount) { Text("Agregar un método de pago") }
        enabledMethods.forEach { method -> SelectCard(state.selectedPaymentId == method.id, { viewModel.selectPayment(method.id) }) { Text("${method.typeName} · ${method.mask}"); Text("Vence ${PaymentExpiration.display(method.expiration)}") } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Unidades"); Text(cart.units.toString()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", style = MaterialTheme.typography.titleLarge); Text(money(cart.total), style = MaterialTheme.typography.titleLarge) }
        } }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.ambiguous) Text("No cierres esta pantalla ni repitas la compra hasta poder verificar el historial.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { confirm = true }, enabled = !state.busy && !state.ambiguous && cart.lines.isNotEmpty() && state.selectedAddressId != null && state.selectedPaymentId != null, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Registrando…" else "Confirmar compra") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { if (!state.busy) confirm = false },
        title = { Text("Confirmar compra") },
        text = { Text("Se registrará una orden por ${money(cart.total)}. Esta acción no debe repetirse mientras esté en proceso.") },
        confirmButton = { Button(onClick = { confirm = false; viewModel.checkout(onUnauthorized) }) { Text("Sí, comprar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
    )
}

@Composable private fun SelectCard(selected: Boolean, select: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = select)) { Row(Modifier.padding(14.dp)) { RadioButton(selected, select); Column(Modifier.padding(start = 8.dp)) { content() } } }
}

private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(value)
