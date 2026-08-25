package com.tiendatech.mobile.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiendatech.mobile.core.designsystem.component.TiendaTechErrorState
import com.tiendatech.mobile.core.designsystem.component.TiendaTechLoadingState
import com.tiendatech.mobile.feature.account.domain.Address
import com.tiendatech.mobile.feature.account.domain.PaymentMethod
import com.tiendatech.mobile.feature.account.data.PaymentExpiration
import com.tiendatech.mobile.core.preferences.ThemeMode

@Composable
fun AccountScreen(
    userId: Long,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOrders: () -> Unit,
    onNotifications: () -> Unit,
    onUnauthorized: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addressEditor by remember { mutableStateOf<Address?>(null) }
    var creatingAddress by remember { mutableStateOf(false) }
    var paymentEditor by remember { mutableStateOf<PaymentMethod?>(null) }
    var creatingPayment by remember { mutableStateOf(false) }
    LaunchedEffect(userId) { viewModel.load(userId, onUnauthorized) }
    if (state.loading && state.data == null) { TiendaTechLoadingState("Cargando tu cuenta", Modifier.fillMaxSize()); return }
    val data = state.data
    if (data == null) { TiendaTechErrorState(state.error ?: "No se pudo cargar la cuenta", { viewModel.load(userId, onUnauthorized) }, Modifier.fillMaxSize()); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Catálogo") }
        Text("Mi cuenta", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(onClick = onOrders, modifier = Modifier.fillMaxWidth()) { Text("Ver mis pedidos") }
        OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) { Text("Notificaciones") }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(data.profile.name, style = MaterialTheme.typography.titleLarge); Text("@${data.profile.username}")
            Text(data.profile.email); Text(data.profile.phone.ifBlank { "Sin teléfono" }); Text("Cédula: ${data.profile.document}")
        } }
        SectionTitle("Direcciones", "Nueva") { creatingAddress = true }
        if (data.addresses.isEmpty()) Text("Todavía no tienes puntos de entrega.")
        data.addresses.forEach { address -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text(address.street, style = MaterialTheme.typography.titleMedium); Text(listOfNotNull(address.city, address.province).joinToString(" · "))
            address.reference?.let { Text(it) }
            Row { TextButton(onClick = { addressEditor = address }) { Text("Editar") }; TextButton(onClick = { viewModel.deleteAddress(address.id, onUnauthorized) }, enabled = !state.busy) { Text("Eliminar") } }
        } } }
        SectionTitle("Métodos de pago", "Agregar") { creatingPayment = true }
        Text("Mecanismo académico: no corresponde a una pasarela PCI real.", style = MaterialTheme.typography.bodySmall)
        if (data.paymentMethods.isEmpty()) Text("No tienes métodos registrados.")
        data.paymentMethods.forEach { method -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(method.typeName); Text(method.mask); Text("Vence: ${PaymentExpiration.display(method.expiration)}") }
            Column { TextButton(onClick = { paymentEditor = method }) { Text("Actualizar") }; TextButton(onClick = { viewModel.setPaymentEnabled(method.id, !method.enabled, onUnauthorized) }, enabled = !state.busy) { Text(if (method.enabled) "Inactivar" else "Reactivar") } }
        } } }
        PasswordForm(state.busy) { current, new, repeated, clear -> viewModel.changePassword(current, new, repeated, onUnauthorized, clear) }
        Text("Tema", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode -> OutlinedButton(onClick = { onThemeMode(mode) }, enabled = themeMode != mode, modifier = Modifier.weight(1f)) { Text(when (mode) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" }) } }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
    if (creatingAddress || addressEditor != null) AddressDialog(addressEditor, data.cities, { creatingAddress = false; addressEditor = null }) { id, street, reference, city ->
        viewModel.saveAddress(id, street, reference, city, onUnauthorized); creatingAddress = false; addressEditor = null
    }
    if (creatingPayment || paymentEditor != null) PaymentDialog(paymentEditor, data.paymentTypes, { creatingPayment = false; paymentEditor = null }) { card, expiration, type, clear ->
        val editing = paymentEditor
        if (editing == null) viewModel.createPayment(card, expiration, type, onUnauthorized) { clear(); creatingPayment = false }
        else viewModel.updatePayment(editing.id, card, expiration, type, editing.enabled, onUnauthorized) { clear(); paymentEditor = null }
    }
}

@Composable private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.titleLarge); TextButton(onClick = onAction) { Text(action) } }
}

@Composable private fun AddressDialog(address: Address?, cities: List<com.tiendatech.mobile.feature.account.domain.City>, dismiss: () -> Unit, save: (Long?, String, String?, Long) -> Unit) {
    var street by remember(address) { mutableStateOf(address?.street.orEmpty()) }; var reference by remember(address) { mutableStateOf(address?.reference.orEmpty()) }
    var cityId by remember(address) { mutableStateOf(address?.cityId) }
    var cityQuery by remember { mutableStateOf("") }
    val visibleCities = remember(cities, cityQuery) {
        cities
            .filter { cityQuery.isBlank() || "${it.name} ${it.provinceName}".contains(cityQuery.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ it.provinceName }, { it.name }))
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (address == null) "Nueva dirección" else "Editar dirección") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(street, { street = it }, label = { Text("Calle y número") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reference, { reference = it }, label = { Text("Referencia") }, modifier = Modifier.fillMaxWidth())
        Text("Ciudad de Ecuador", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(cityQuery, { cityQuery = it }, label = { Text("Buscar ciudad o provincia") }, modifier = Modifier.fillMaxWidth())
        if (cities.isEmpty()) {
            Text("No se pudieron cargar las ciudades. Vuelve a intentarlo con conexión.", color = MaterialTheme.colorScheme.error)
        } else if (visibleCities.isEmpty()) {
            Text("No hay ciudades que coincidan con la búsqueda")
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                items(visibleCities, key = { it.id }) { city ->
                    Row(
                        Modifier.fillMaxWidth().clickable { cityId = city.id }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = cityId == city.id, onClick = { cityId = city.id })
                        Column {
                            Text(city.name, style = MaterialTheme.typography.bodyLarge)
                            Text(city.provinceName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        cities.firstOrNull { it.id == cityId }?.let { Text("Seleccionada: ${it.name} · ${it.provinceName}", color = MaterialTheme.colorScheme.primary) }
    } }, confirmButton = { Button(onClick = { cityId?.let { save(address?.id, street, reference.takeIf(String::isNotBlank), it) } }, enabled = street.isNotBlank() && cityId != null) { Text("Guardar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable private fun PaymentDialog(method: PaymentMethod?, types: List<com.tiendatech.mobile.feature.account.domain.PaymentType>, dismiss: () -> Unit, save: (String, String, Long, () -> Unit) -> Unit) {
    var card by remember { mutableStateOf("") }; var expiration by remember(method) { mutableStateOf(method?.let { PaymentExpiration.display(it.expiration) }.orEmpty()) }; var typeId by remember(method) { mutableStateOf(method?.typeId) }
    val clear = { card = ""; expiration = "" }
    val expirationError = when {
        expiration.isBlank() -> null
        PaymentExpiration.toApiDate(expiration) == null -> "Usa el formato MM/AA"
        !PaymentExpiration.isCurrentOrFuture(expiration) -> "La tarjeta está vencida"
        else -> null
    }
    AlertDialog(onDismissRequest = { clear(); dismiss() }, title = { Text(if (method == null) "Agregar método" else "Actualizar método") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No se solicita CVV. El número completo se envía una sola vez y no se guarda en el dispositivo.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(card, { card = it.filter(Char::isDigit).take(19) }, label = { Text(if (method == null) "Número de tarjeta" else "Nuevo número completo") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            expiration,
            { expiration = formatExpiration(it) },
            label = { Text("Vencimiento MM/AA") },
            placeholder = { Text("07/26") },
            isError = expirationError != null,
            supportingText = expirationError?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Tipo de tarjeta", style = MaterialTheme.typography.titleSmall)
        if (types.isEmpty()) {
            Text("No se pudieron cargar los tipos débito/crédito. Vuelve a intentarlo con conexión.", color = MaterialTheme.colorScheme.error)
        } else {
            types.forEach { type ->
                Row(
                    Modifier.fillMaxWidth().clickable { typeId = type.id }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(selected = typeId == type.id, onClick = { typeId = type.id })
                    Text(type.name, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    } }, confirmButton = { Button(onClick = { typeId?.let { save(card, expiration, it, clear) } }, enabled = card.length in 13..19 && expirationError == null && expiration.isNotBlank() && typeId != null) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { clear(); dismiss() }) { Text("Cancelar") } })
}

private fun formatExpiration(value: String): String {
    val digits = value.filter(Char::isDigit).take(4)
    return if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"
}

@Composable private fun PasswordForm(busy: Boolean, submit: (String, String, String, () -> Unit) -> Unit) {
    var current by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var repeated by remember { mutableStateOf("") }
    val clear = { current = ""; new = ""; repeated = "" }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text("Cambiar contraseña", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(current, { current = it }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(new, { new = it }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(repeated, { repeated = it }, label = { Text("Repetir contraseña") }, visualTransformation = PasswordVisualTransformation())
        Button(onClick = { submit(current, new, repeated, clear) }, enabled = !busy) { Text("Actualizar contraseña") }
    } }
}
