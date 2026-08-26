package com.tiendatech.mobile.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tiendatech.mobile.core.designsystem.component.TiendaTechEmptyState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechErrorState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechLoadingState
import com.tiendatech.mobile.feature.catalog.data.CatalogImages
import com.tiendatech.mobile.feature.catalog.domain.Product
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CatalogScreen(
    onProduct: (Long) -> Unit,
    onAccount: () -> Unit,
    onCart: () -> Unit,
    onScanner: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column { Text("TiendaTech", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary); Text("Catálogo") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCart) { Text("Carrito") }
                OutlinedButton(onClick = onAccount) { Text("Cuenta") }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            label = { Text("Buscar productos") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        OutlinedButton(
            onClick = onScanner,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) { Text("Escanear código") }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterChip(selected = state.selectedCategoryId == null, onClick = { viewModel.selectCategory(null) }, label = { Text("Todos") }) }
            items(state.categories, key = { it.id }) { category ->
                FilterChip(selected = state.selectedCategoryId == category.id, onClick = { viewModel.selectCategory(category) }, label = { Text(category.name) })
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)); Text("Toca actualizar para volver a intentar", modifier = Modifier.padding(horizontal = 16.dp)) }
        when {
            state.loading -> TiendaTechLoadingState("Cargando catálogo", Modifier.fillMaxSize())
            state.products.isEmpty() -> TiendaTechEmptyState(
                title = "No encontramos productos",
                message = if (state.query.isBlank()) "No hay productos disponibles en esta categoría" else "El producto puede estar en la siguiente página",
                modifier = Modifier.fillMaxSize(),
                actionText = if (state.query.isNotBlank() && state.canLoadMore) "Buscar en más productos" else "Actualizar",
                onAction = {
                    if (state.query.isNotBlank() && state.canLoadMore) viewModel.loadNextPage()
                    else viewModel.refresh()
                }
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(state.products, key = { it.id }) { product -> ProductCard(product, onProduct) }
                if (state.canLoadMore || state.loadingMore) {
                    item(key = "load-more") {
                        LaunchedEffect(state.products.size, state.selectedCategoryId) {
                            viewModel.loadNextPage()
                        }
                        OutlinedButton(
                            onClick = viewModel::loadNextPage,
                            enabled = !state.loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.loadingMore) "Cargando más…" else "Cargar más") }
                    }
                }
                item(key = "refresh") {
                    OutlinedButton(onClick = { viewModel.refresh() }, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.refreshing) "Actualizando…" else "Actualizar catálogo")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onProduct: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onProduct(product.id) }) {
        AsyncImage(
            model = CatalogImages.url(product.imageId), contentDescription = product.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(money(product.price), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Text(if (product.available) "${product.stock} disponibles" else "Agotado", color = if (product.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    adding: Boolean,
    cartMessage: String?,
    onAdd: (Long, Int) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading && state.product == null) {
        TiendaTechLoadingState("Cargando producto", Modifier.fillMaxSize()); return
    }
    val product = state.product
    if (product == null) {
        TiendaTechErrorState(state.error ?: "Producto no encontrado", viewModel::load, Modifier.fillMaxSize()); return
    }
    var quantity by remember(product.id) { mutableIntStateOf(1) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("← Volver", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        val images = product.galleryImageIds.ifEmpty { listOfNotNull(product.imageId) }
        if (images.isEmpty()) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("Sin imagen") }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images) { imageId -> AsyncImage(model = CatalogImages.url(imageId), contentDescription = product.name, modifier = Modifier.size(300.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop) }
            }
        }
        Spacer(Modifier.height(20.dp))
        product.categoryName?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text(product.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(money(product.price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
        Text(if (product.available) "Disponible: ${product.stock} unidades" else "Producto agotado", color = if (product.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        product.description?.takeIf(String::isNotBlank)?.let { Text(it, modifier = Modifier.padding(top = 16.dp)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        if (product.available) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                OutlinedButton(onClick = { if (quantity > 1) quantity-- }, enabled = quantity > 1 && !adding) { Text("−") }
                Text(quantity.toString(), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { quantity++ }, enabled = quantity < (product.stock ?: 1) && !adding) { Text("+") }
            }
        }
        cartMessage?.let { Text(it, color = if (it.contains("añadido")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Button(
            onClick = { onAdd(product.id, quantity) },
            enabled = product.available && !adding,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text(if (adding) "Añadiendo…" else if (product.available) "Añadir al carrito" else "Producto agotado") }
    }
}

private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(value)
