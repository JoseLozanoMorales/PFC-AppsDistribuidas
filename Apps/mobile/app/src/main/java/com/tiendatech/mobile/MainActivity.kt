package com.tiendatech.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.tiendatech.mobile.core.designsystem.theme.TiendaTechTheme
import com.tiendatech.mobile.core.navigation.TiendaTechDestinations
import com.tiendatech.mobile.feature.auth.ui.LoginScreen
import com.tiendatech.mobile.feature.auth.ui.RecoveryScreen
import com.tiendatech.mobile.feature.auth.ui.RegisterScreen
import com.tiendatech.mobile.feature.auth.ui.SessionUiState
import com.tiendatech.mobile.feature.auth.ui.SessionViewModel
import com.tiendatech.mobile.feature.catalog.ui.CatalogScreen
import com.tiendatech.mobile.feature.catalog.ui.ProductDetailScreen
import com.tiendatech.mobile.feature.cart.ui.CartScreen
import com.tiendatech.mobile.feature.cart.ui.CartViewModel
import com.tiendatech.mobile.feature.account.ui.AccountScreen as CustomerAccountScreen
import com.tiendatech.mobile.feature.account.ui.CheckoutScreen
import com.tiendatech.mobile.core.preferences.ThemeMode
import com.tiendatech.mobile.core.preferences.ThemeViewModel
import com.tiendatech.mobile.feature.orders.ui.OrdersScreen
import com.tiendatech.mobile.feature.orders.ui.OrderDetailScreen
import com.tiendatech.mobile.feature.scanner.ui.ScannerScreen
import com.tiendatech.mobile.feature.notifications.ui.NotificationsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TiendaTechRoot() }
    }
}

@Composable
private fun TiendaTechRoot(themeViewModel: ThemeViewModel = hiltViewModel()) {
    val themeMode by themeViewModel.mode.collectAsStateWithLifecycle()
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    TiendaTechTheme(darkTheme = dark) { TiendaTechApp(themeMode = themeMode, onThemeMode = themeViewModel::setMode) }
}

@Composable
private fun TiendaTechApp(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel()
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val cartState by cartViewModel.state.collectAsStateWithLifecycle()
    var pendingRoute by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        NavHost(
            navController = navController,
            startDestination = TiendaTechDestinations.HOME_ROUTE,
            modifier = Modifier.padding(padding)
        ) {
            composable(TiendaTechDestinations.HOME_ROUTE) {
                CatalogScreen(
                    onProduct = { navController.navigate(TiendaTechDestinations.product(it)) },
                    onAccount = { navController.navigate(TiendaTechDestinations.ACCOUNT_ROUTE) },
                    onScanner = { navController.navigate(TiendaTechDestinations.SCANNER_ROUTE) },
                    onCart = {
                        if (session is SessionUiState.Authenticated) navController.navigate(TiendaTechDestinations.CART_ROUTE)
                        else { pendingRoute = TiendaTechDestinations.CART_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }
                    }
                )
            }
            composable(TiendaTechDestinations.SCANNER_ROUTE) {
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    onProduct = { navController.navigate(TiendaTechDestinations.product(it)) }
                )
            }
            composable(TiendaTechDestinations.ACCOUNT_ROUTE) {
                when (val current = session) {
                    SessionUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    SessionUiState.Guest -> GuestAccountScreen({ pendingRoute = TiendaTechDestinations.ACCOUNT_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }, { navController.popBackStack() })
                    is SessionUiState.Authenticated -> CustomerAccountScreen(
                        userId = current.user.id.toLong(), onBack = { navController.popBackStack() },
                        themeMode = themeMode, onThemeMode = onThemeMode,
                        onOrders = { navController.navigate(TiendaTechDestinations.ORDERS_ROUTE) },
                        onNotifications = { navController.navigate(TiendaTechDestinations.NOTIFICATIONS_ROUTE) },
                        onLogout = { sessionViewModel.logout(); navController.popBackStack(TiendaTechDestinations.HOME_ROUTE, false) },
                        onUnauthorized = { sessionViewModel.logout(); pendingRoute = TiendaTechDestinations.ACCOUNT_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }
                    )
                }
            }
            composable(
                route = TiendaTechDestinations.PRODUCT_ROUTE,
                arguments = listOf(navArgument("productId") { type = NavType.LongType })
            ) {
                ProductDetailScreen(
                    onBack = { navController.popBackStack() },
                    adding = cartState.busyProductId != null,
                    cartMessage = cartState.error ?: cartState.message,
                    onAdd = { productId, quantity ->
                        val authenticated = session as? SessionUiState.Authenticated
                        if (authenticated == null) {
                            pendingRoute = TiendaTechDestinations.product(productId)
                            navController.navigate(TiendaTechDestinations.LOGIN_ROUTE)
                        } else {
                            cartViewModel.add(
                                authenticated.user.id.toLong(), productId, quantity,
                                onUnauthorized = {
                                    sessionViewModel.logout(); pendingRoute = TiendaTechDestinations.product(productId)
                                    navController.navigate(TiendaTechDestinations.LOGIN_ROUTE)
                                },
                                onSuccess = {}
                            )
                        }
                    }
                )
            }
            composable(TiendaTechDestinations.CART_ROUTE) {
                when (val current = session) {
                    SessionUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    SessionUiState.Guest -> {
                        LaunchedEffect(Unit) {
                            pendingRoute = TiendaTechDestinations.CART_ROUTE
                            navController.navigate(TiendaTechDestinations.LOGIN_ROUTE)
                        }
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                    is SessionUiState.Authenticated -> CartScreen(
                        userId = current.user.id.toLong(),
                        onBack = { navController.popBackStack() },
                        onProduct = { navController.navigate(TiendaTechDestinations.product(it)) },
                        onCheckout = { navController.navigate(TiendaTechDestinations.CHECKOUT_ROUTE) },
                        onUnauthorized = {
                            sessionViewModel.logout(); pendingRoute = TiendaTechDestinations.CART_ROUTE
                            navController.navigate(TiendaTechDestinations.LOGIN_ROUTE)
                        },
                        viewModel = cartViewModel
                    )
                }
            }
            composable(TiendaTechDestinations.CHECKOUT_ROUTE) {
                when (val current = session) {
                    SessionUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    SessionUiState.Guest -> {
                        LaunchedEffect(Unit) { pendingRoute = TiendaTechDestinations.CHECKOUT_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                    is SessionUiState.Authenticated -> CheckoutScreen(
                        userId = current.user.id.toLong(), onBack = { navController.popBackStack() },
                        onAccount = { navController.navigate(TiendaTechDestinations.ACCOUNT_ROUTE) },
                        onCatalog = { navController.popBackStack(TiendaTechDestinations.HOME_ROUTE, false) },
                        onOrder = { navController.navigate(TiendaTechDestinations.order(it)) },
                        onUnauthorized = { sessionViewModel.logout(); pendingRoute = TiendaTechDestinations.CHECKOUT_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }
                    )
                }
            }
            composable(TiendaTechDestinations.ORDERS_ROUTE) {
                when (val current = session) {
                    SessionUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    SessionUiState.Guest -> { LaunchedEffect(Unit) { pendingRoute = TiendaTechDestinations.ORDERS_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }; CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                    is SessionUiState.Authenticated -> OrdersScreen(
                        userId = current.user.id.toLong(), onBack = { navController.popBackStack() },
                        onOrder = { navController.navigate(TiendaTechDestinations.order(it)) },
                        onUnauthorized = { sessionViewModel.logout(); pendingRoute = TiendaTechDestinations.ORDERS_ROUTE; navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }
                    )
                }
            }
            composable(
                TiendaTechDestinations.ORDER_ROUTE,
                arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = TiendaTechDestinations.ORDER_DEEP_LINK })
            ) {
                when (val current = session) {
                    SessionUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    SessionUiState.Guest -> { LaunchedEffect(Unit) { pendingRoute = TiendaTechDestinations.order(it.arguments?.getLong("orderId") ?: 0); navController.navigate(TiendaTechDestinations.LOGIN_ROUTE) }; CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                    is SessionUiState.Authenticated -> OrderDetailScreen(
                        userId = current.user.id.toLong(), onBack = { navController.popBackStack() },
                        onProduct = { navController.navigate(TiendaTechDestinations.product(it)) },
                        onUnauthorized = { sessionViewModel.logout(); navController.popBackStack(TiendaTechDestinations.HOME_ROUTE, false) }
                    )
                }
            }
            composable(TiendaTechDestinations.NOTIFICATIONS_ROUTE) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(TiendaTechDestinations.LOGIN_ROUTE) {
                LoginScreen(
                    onAuthenticated = { user ->
                        sessionViewModel.authenticated(user)
                        navController.popBackStack(TiendaTechDestinations.HOME_ROUTE, false)
                        pendingRoute?.let { route ->
                            pendingRoute = null
                            if (route != TiendaTechDestinations.HOME_ROUTE) navController.navigate(route)
                        }
                    },
                    onRegister = { navController.navigate(TiendaTechDestinations.REGISTER_ROUTE) },
                    onRecovery = { navController.navigate(TiendaTechDestinations.RECOVERY_ROUTE) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(TiendaTechDestinations.REGISTER_ROUTE) {
                RegisterScreen(onLogin = { navController.navigateToLogin() })
            }
            composable(TiendaTechDestinations.RECOVERY_ROUTE) {
                RecoveryScreen(onLogin = { navController.navigateToLogin() })
            }
        }
    }
}

private fun NavHostController.navigateToLogin() {
    navigate(TiendaTechDestinations.LOGIN_ROUTE) {
        popUpTo(TiendaTechDestinations.LOGIN_ROUTE) { inclusive = true }
    }
}

@Composable
private fun GuestAccountScreen(onLogin: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mi cuenta", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("Inicia sesión para comprar y consultar tus pedidos.", modifier = Modifier.padding(20.dp))
        Button(onClick = onLogin) { Text("Iniciar sesión") }
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Volver al catálogo") }
    }
}
