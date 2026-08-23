package com.tiendatech.mobile.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiendatech.mobile.feature.auth.domain.AuthUser
import com.tiendatech.mobile.feature.auth.domain.RegistrationData

@Composable
fun LoginScreen(
    onAuthenticated: (AuthUser) -> Unit,
    onRegister: () -> Unit,
    onRecovery: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthContainer("Bienvenido de vuelta", "Inicia sesión para comprar y consultar tus pedidos") {
        Field(username, { username = it }, "Usuario")
        Field(password, { password = it }, "Contraseña", password = true)
        Feedback(state)
        PrimaryAction("Entrar", state.loading) { viewModel.login(username, password, onAuthenticated) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onRegister) { Text("Crear cuenta") }
            TextButton(onClick = onRecovery) { Text("Olvidé mi contraseña") }
        }
        TextButton(onClick = onBack) { Text("Continuar como invitado") }
    }
}

@Composable
fun RegisterScreen(
    onLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stage by viewModel.registrationStage.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }; var otp by remember { mutableStateOf("") }
    AuthContainer("Crear cuenta", "Regístrate como cliente de TiendaTech") {
        when (stage) {
            RegistrationStage.FORM -> {
                Field(name, { name = it }, "Nombre completo")
                Field(username, { username = it }, "Usuario")
                Field(email, { email = it }, "Correo", KeyboardType.Email)
                Field(phone, { phone = it.filter(Char::isDigit).take(10) }, "Teléfono", KeyboardType.Number)
                Field(document, { document = it.filter(Char::isDigit).take(10) }, "Cédula", KeyboardType.Number)
                Field(password, { password = it }, "Contraseña", password = true)
                Field(repeated, { repeated = it }, "Repetir contraseña", password = true)
                Feedback(state)
                PrimaryAction("Verificar correo", state.loading) {
                    viewModel.requestOtp(
                        RegistrationData(name, username, email.trim(), password, document, phone), repeated
                    )
                }
                TextButton(onClick = onLogin) { Text("Ya tengo una cuenta") }
            }
            RegistrationStage.OTP -> {
                Text("Ingresa el código de seis dígitos enviado a tu correo.")
                Field(otp, { otp = it.filter(Char::isDigit).take(6) }, "Código", KeyboardType.Number)
                Feedback(state)
                PrimaryAction("Crear mi cuenta", state.loading) { viewModel.verifyAndRegister(otp) }
                TextButton(onClick = viewModel::editRegistration) { Text("Corregir datos") }
            }
            RegistrationStage.DONE -> {
                Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text("Cuenta creada correctamente", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onLogin) { Text("Iniciar sesión") }
            }
        }
    }
}

@Composable
fun RecoveryScreen(onLogin: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    AuthContainer("Recuperar contraseña", "Recibirás una contraseña temporal en tu correo registrado") {
        if (state.message == null) {
            Field(email, { email = it }, "Correo electrónico", KeyboardType.Email)
            Feedback(state)
            PrimaryAction("Enviar instrucciones", state.loading) { viewModel.recover(email) }
        } else {
            Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Feedback(state)
        }
        TextButton(onClick = onLogin) { Text("Volver al inicio de sesión") }
    }
}

@Composable
private fun AuthContainer(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TiendaTech", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp)); Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp)); content()
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
private fun Feedback(state: AuthActionState) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
}

@Composable
private fun PrimaryAction(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (loading) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp) else Text(text)
    }
}
