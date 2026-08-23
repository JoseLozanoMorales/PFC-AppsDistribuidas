package com.tiendatech.mobile.feature.cart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tiendatech.mobile.core.designsystem.component.TiendaTechEmptyState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechErrorState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechLoadingState
import com.tiendatech.mobile.feature.cart.domain.CartLine
import com.tiendatech.mobile.feature.catalog.data.CatalogImages
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CartScreen(
    userId: Long,
    onBack: () -> Unit,
    onProduct: (Long) -> Unit,
    onCheckout: () -> Unit,
    onUnauthorized: () -> Unit,
    viewModel: CartViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.load(userId, onUnauthorized) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Catálogo") }
            Text("Mi carrito", style = MaterialTheme.typography.headlineSmall)
        }
        when {
            state.loading && state.cart == null -> TiendaTechLoadingState("Cargando carrito", Modifier.fillMaxSize())
            state.error != null && state.cart == null -> TiendaTechErrorState(state.error.orEmpty(), { viewModel.load(userId, onUnauthorized) }, Modifier.fillMaxSize())
            state.cart?.lines.isNullOrEmpty() -> TiendaTechEmptyState("Tu carrito está vacío", "Encuentra componentes para tu próximo equipo", Modifier.fillMaxSize(), "Explorar productos", onBack)
            else -> {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.cart!!.lines, key = { it.productId }) { line ->
                        CartLineCard(line, state.busyProductId == line.productId, onProduct, { viewModel.update(line.productId, it, onUnauthorized) }, { viewModel.remove(line.productId, onUnauthorized) })
                    }
                }
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${state.cart!!.units} unidades"); Text(money(state.cart!!.total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
                    Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Continuar al pago") }
                }
            }
        }
    }
}

@Composable
private fun CartLineCard(line: CartLine, busy: Boolean, onProduct: (Long) -> Unit, onQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = CatalogImages.url(line.product?.imageId), contentDescription = line.product?.name, modifier = Modifier.size(82.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(line.product?.name ?: "Producto ${line.productId}", fontWeight = FontWeight.SemiBold)
                Text(money(line.unitPrice), color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onQuantity(line.quantity - 1) }, enabled = !busy) { Text("−") }
                    Text(line.quantity.toString(), modifier = Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { onQuantity(line.quantity + 1) }, enabled = !busy && (line.product?.stock == null || line.quantity < line.product.stock)) { Text("+") }
                }
                Row { TextButton(onClick = { onProduct(line.productId) }) { Text("Ver") }; TextButton(onClick = onRemove, enabled = !busy) { Text("Quitar") } }
            }
            Text(money(line.subtotal), fontWeight = FontWeight.Bold)
        }
    }
}

private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(value)
