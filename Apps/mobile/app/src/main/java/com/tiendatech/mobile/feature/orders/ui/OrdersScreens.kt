package com.tiendatech.mobile.feature.orders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiendatech.mobile.core.designsystem.component.TiendaTechEmptyState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechErrorState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechLoadingState
import com.tiendatech.mobile.feature.orders.domain.OrderLine
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OrdersScreen(userId: Long, onBack: () -> Unit, onOrder: (Long) -> Unit, onUnauthorized: () -> Unit, viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); LaunchedEffect(userId) { viewModel.load(userId, onUnauthorized) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = onBack) { Text("← Cuenta") }; Text("Mis pedidos", style = MaterialTheme.typography.headlineSmall) }
        when {
            state.loading && state.orders.isEmpty() -> TiendaTechLoadingState("Cargando pedidos", Modifier.fillMaxSize())
            state.error != null && state.orders.isEmpty() -> TiendaTechErrorState(state.error.orEmpty(), { viewModel.load(userId, onUnauthorized) }, Modifier.fillMaxSize())
            state.orders.isEmpty() -> TiendaTechEmptyState("No tienes pedidos", "Tus compras aparecerán aquí", Modifier.fillMaxSize())
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.orders, key = { it.id }) { order -> Card(Modifier.fillMaxWidth().clickable { onOrder(order.id) }) { Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Orden #${order.id}", fontWeight = FontWeight.Bold); Text(money(order.total), color = MaterialTheme.colorScheme.primary) }
                    Text(order.date); Text("Orden registrada"); order.invoiceNumber?.let { Text("Factura $it") }
                } } }
                if (state.page + 1 < state.totalPages) item { Button(onClick = { viewModel.loadMore(onUnauthorized) }, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Cargando…" else "Cargar más") } }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(userId: Long, onBack: () -> Unit, onProduct: (Long) -> Unit, onUnauthorized: () -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); LaunchedEffect(userId) { viewModel.load(userId, onUnauthorized) }
    if (state.loading) { TiendaTechLoadingState("Cargando pedido", Modifier.fillMaxSize()); return }
    val detail = state.detail
    if (detail == null) { TiendaTechErrorState(state.error ?: "Pedido no encontrado", { viewModel.load(userId, onUnauthorized) }, Modifier.fillMaxSize()); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("← Pedidos") }; Text("Orden #${detail.summary.id}", style = MaterialTheme.typography.headlineMedium); Text("Orden registrada", color = MaterialTheme.colorScheme.primary); Text(detail.summary.date) }
        items(detail.lines, key = { it.productId }) { line -> LineCard(line, onProduct) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { SummaryRow("Subtotal", detail.summary.subtotal); SummaryRow("Total", detail.summary.total) } } }
        item { val invoice = detail.invoice; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            if (invoice == null) { Text("Factura aún no disponible", style = MaterialTheme.typography.titleMedium); Text("La orden fue registrada, pero todavía no hay una factura asociada.") }
            else { Text("Factura ${invoice.number}", style = MaterialTheme.typography.titleLarge); Text("Emitida: ${invoice.issueDate}"); Text(invoice.customerName); Text(invoice.deliveryAddress); Text("Total facturado: ${money(invoice.total)}", fontWeight = FontWeight.Bold); Text("La API actual no ofrece descarga PDF.", style = MaterialTheme.typography.bodySmall) }
        } } }
    }
}

@Composable private fun LineCard(line: OrderLine, onProduct: (Long) -> Unit) { Card(Modifier.fillMaxWidth().clickable { onProduct(line.productId) }) { Column(Modifier.padding(14.dp)) {
    Text(line.productName ?: "Producto ${line.productId}", fontWeight = FontWeight.SemiBold); Text("${line.quantity} × ${money(line.unitPrice)}"); Text("Subtotal: ${money(line.subtotal)} · IVA: ${money(line.tax)}"); Text("Total: ${money(line.total)}", fontWeight = FontWeight.Bold)
} } }
@Composable private fun SummaryRow(label: String, value: Double) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(money(value), fontWeight = FontWeight.Bold) } }
private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(value)
