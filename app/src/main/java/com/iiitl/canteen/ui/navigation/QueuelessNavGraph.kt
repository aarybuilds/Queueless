package com.iiitl.canteen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iiitl.canteen.AppContainer
import com.iiitl.canteen.data.model.UserRole
import com.iiitl.canteen.data.repository.CartItem
import com.iiitl.canteen.ui.auth.EmailVerificationScreen
import com.iiitl.canteen.ui.auth.LoginScreen
import com.iiitl.canteen.ui.auth.LoginViewModel
import com.iiitl.canteen.ui.cafe.CafeOrderQueueScreen
import com.iiitl.canteen.ui.cafe.CafeOrderQueueViewModel
import com.iiitl.canteen.ui.cafeteria.CafeteriaSelectionScreen
import com.iiitl.canteen.ui.menu.CartScreen
import com.iiitl.canteen.ui.menu.CartViewModel
import com.iiitl.canteen.ui.menu.MenuScreen
import com.iiitl.canteen.ui.menu.MenuViewModel
import com.iiitl.canteen.ui.menu.StudentInfo
import com.iiitl.canteen.ui.order.OrderHistoryScreen
import com.iiitl.canteen.ui.order.OrderHistoryViewModel
import com.iiitl.canteen.ui.order.OrderStatusScreen
import com.iiitl.canteen.ui.order.OrderStatusViewModel
import com.iiitl.canteen.ui.order.PlaceOrderViewModel
import com.iiitl.canteen.ui.profile.ProfileScreen
import com.iiitl.canteen.ui.profile.ProfileViewModel
import com.iiitl.canteen.ui.suggestion.SuggestionScreen
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val EMAIL_VERIFICATION = "email_verification"
    const val CAFETERIA_SELECTION = "cafeteria_selection"
    const val NO_CAFETERIA_ASSIGNED = "no_cafeteria_assigned"
    const val MENU = "menu/{cafeteriaId}"
    const val CART = "cart/{cafeteriaId}"
    const val ORDER_STATUS = "order_status/{orderId}"
    const val ORDER_HISTORY = "order_history"
    const val CAFE_QUEUE = "cafe_queue/{cafeteriaId}"
    const val PROFILE = "profile"
    const val SUGGESTION = "suggestion"

    val orderHistory = ORDER_HISTORY

    fun menu(cafeteriaId: String) = "menu/$cafeteriaId"
    fun cart(cafeteriaId: String) = "cart/$cafeteriaId"
    fun orderStatus(orderId: String) = "order_status/$orderId"
    fun cafeQueue(cafeteriaId: String) = "cafe_queue/$cafeteriaId"
}

@Composable
fun QueuelessNavGraph(appContainer: AppContainer) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Observe global auth state to drive initial route & login transitions.
    val isLoggedInState by appContainer.authRepository.observeAuthState()
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(isLoggedInState) {
        when (isLoggedInState) {
            true -> {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute == Routes.LOGIN || currentRoute == null) {
                    val uid = appContainer.authRepository.getCurrentUserId()
                    val role = if (uid != null) appContainer.authRepository.getUserRole(uid) else UserRole.STUDENT

                    if (role == UserRole.CAFE_STAFF) {
                        val assignedCafeteriaId = if (uid != null) appContainer.authRepository.getAssignedCafeteriaId(uid) else null
                        if (!assignedCafeteriaId.isNullOrEmpty()) {
                            navController.navigate(Routes.cafeQueue(assignedCafeteriaId)) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.NO_CAFETERIA_ASSIGNED) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    } else {
                        val verified = appContainer.authRepository.isEmailVerified()
                        if (verified) {
                            navController.navigate(Routes.CAFETERIA_SELECTION) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.EMAIL_VERIFICATION) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    }
                }
            }
            false -> {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != Routes.LOGIN) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            null -> { /* Auth state loading */ }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
        enterTransition = { androidx.compose.animation.EnterTransition.None },
        exitTransition = { androidx.compose.animation.ExitTransition.None },
        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
        popExitTransition = { androidx.compose.animation.ExitTransition.None }
    ) {
        composable(Routes.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = appContainer.loginViewModelFactory
            )
            LoginScreen(viewModel = loginViewModel)
        }

        composable(Routes.EMAIL_VERIFICATION) {
            val userEmail = appContainer.authRepository.getCurrentUserEmail() ?: ""
            EmailVerificationScreen(
                userEmail = userEmail,
                onVerified = {
                    navController.navigate(Routes.CAFETERIA_SELECTION) {
                        popUpTo(Routes.EMAIL_VERIFICATION) { inclusive = true }
                    }
                },
                onResendEmail = suspend {
                    appContainer.authRepository.sendEmailVerification()
                },
                onCheckVerification = suspend {
                    appContainer.authRepository.isEmailVerified()
                },
                onSignOut = {
                    appContainer.authRepository.signOut()
                }
            )
        }

        composable(Routes.CAFETERIA_SELECTION) {
            CafeteriaSelectionScreen(
                onCafeteriaSelected = { cafeteriaId ->
                    navController.navigate(Routes.menu(cafeteriaId))
                },
                onProfileClick = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        }

        composable(Routes.NO_CAFETERIA_ASSIGNED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No cafeteria assigned. Contact admin.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            appContainer.authRepository.signOut()
                        }
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }

        composable(
            route = Routes.MENU,
            arguments = listOf(navArgument("cafeteriaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val cafeteriaId = backStackEntry.arguments?.getString("cafeteriaId") ?: "nescafe"

            val menuViewModel: MenuViewModel = viewModel(
                key = cafeteriaId,
                factory = appContainer.menuViewModelFactory(cafeteriaId)
            )
            val cartViewModel: CartViewModel = viewModel(viewModelStoreOwner = backStackEntry)

            val menuUiState by menuViewModel.uiState.collectAsStateWithLifecycle()
            val cartUiState by cartViewModel.uiState.collectAsStateWithLifecycle()

            MenuScreen(
                uiState = menuUiState,
                cartItems = cartUiState.items,
                cafeteriaId = cafeteriaId,
                onItemClick = { menuItem ->
                    cartViewModel.addItem(menuItem)
                },
                onAddItem = { menuItem ->
                    cartViewModel.addItem(menuItem)
                },
                onRemoveItem = { menuItem ->
                    cartViewModel.removeItem(menuItem)
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onViewCartClick = {
                    navController.navigate(Routes.cart(cafeteriaId))
                },
                onProfileClick = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        }

        composable(
            route = Routes.CART,
            arguments = listOf(navArgument("cafeteriaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val cafeteriaId = backStackEntry.arguments?.getString("cafeteriaId") ?: "nescafe"

            // Scope CartViewModel to the parent MENU backstack entry so cart state is shared.
            val menuBackStackEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.MENU)
            }
            val cartViewModel: CartViewModel = viewModel(viewModelStoreOwner = menuBackStackEntry)
            val cartUiState by cartViewModel.uiState.collectAsStateWithLifecycle()

            val placeOrderViewModel: PlaceOrderViewModel = viewModel(
                factory = appContainer.placeOrderViewModelFactory
            )
            val placeOrderUiState by placeOrderViewModel.uiState.collectAsStateWithLifecycle()

            var studentInfo by remember { mutableStateOf(StudentInfo("", "")) }

            LaunchedEffect(Unit) {
                val uid = appContainer.authRepository.getCurrentUserId()
                if (uid != null) {
                    val profile = appContainer.authRepository.getUserProfile(uid)
                    if (profile != null) {
                        studentInfo = StudentInfo(profile.name, profile.rollNumber)
                    }
                }
            }

            LaunchedEffect(placeOrderUiState.placedOrderId) {
                val orderId = placeOrderUiState.placedOrderId
                if (orderId != null) {
                    cartViewModel.clearCart()
                    navController.navigate(Routes.orderStatus(orderId)) {
                        popUpTo(Routes.CAFETERIA_SELECTION) { inclusive = false }
                    }
                }
            }

            val effectiveErrorMessage = placeOrderUiState.errorMessage ?: cartUiState.errorMessage

            CartScreen(
                uiState = cartUiState.copy(errorMessage = effectiveErrorMessage),
                studentInfo = studentInfo,
                onAddItem = { cartViewModel.addItem(it) },
                onRemoveItem = { cartViewModel.removeItem(it) },
                onPlaceOrder = {
                    val cartItems = cartUiState.items.map { (item, qty) -> CartItem(item, qty) }
                    placeOrderViewModel.placeOrder(
                        cafeteriaId = cafeteriaId,
                        cartItems = cartItems,
                        studentName = studentInfo.name,
                        studentRollNumber = studentInfo.rollNumber
                    )
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.ORDER_STATUS,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""

            val orderStatusViewModel: OrderStatusViewModel = viewModel(
                key = orderId,
                factory = appContainer.orderStatusViewModelFactory(orderId)
            )
            val uiState by orderStatusViewModel.uiState.collectAsStateWithLifecycle()

            OrderStatusScreen(
                uiState = uiState,
                onBack = {
                    navController.popBackStack()
                },
                onConfirmReducedOrder = {
                    orderStatusViewModel.confirmReducedOrder()
                },
                onCancelOrder = {
                    orderStatusViewModel.cancelOrder()
                },
                onViewHistory = {
                    navController.navigate(Routes.orderHistory)
                }
            )
        }

        composable(Routes.ORDER_HISTORY) {
            val uid = appContainer.authRepository.getCurrentUserId() ?: ""

            val orderHistoryViewModel: OrderHistoryViewModel = viewModel(
                key = uid,
                factory = appContainer.orderHistoryViewModelFactory(uid)
            )
            val uiState by orderHistoryViewModel.uiState.collectAsStateWithLifecycle()

            OrderHistoryScreen(
                uiState = uiState,
                onOrderClick = { orderId ->
                    navController.navigate(Routes.orderStatus(orderId))
                }
            )
        }

        composable(
            route = Routes.CAFE_QUEUE,
            arguments = listOf(navArgument("cafeteriaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val cafeteriaId = backStackEntry.arguments?.getString("cafeteriaId") ?: "nescafe"

            val cafeQueueViewModel: CafeOrderQueueViewModel = viewModel(
                key = cafeteriaId,
                factory = appContainer.cafeQueueViewModelFactory(cafeteriaId)
            )
            val uiState by cafeQueueViewModel.uiState.collectAsStateWithLifecycle()

            CafeOrderQueueScreen(
                uiState = uiState,
                onClaimOrder = { orderId ->
                    cafeQueueViewModel.claimOrder(orderId)
                },
                onUpdateStatus = { orderId, newStatus ->
                    cafeQueueViewModel.updateStatus(orderId, newStatus)
                },
                onMarkUnavailable = { orderId, items ->
                    cafeQueueViewModel.markUnavailable(orderId, items)
                },
                onClearClaimError = {
                    cafeQueueViewModel.clearClaimError()
                },
                onProfileClick = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        }

        composable(Routes.PROFILE) {
            val profileViewModel: ProfileViewModel = viewModel(
                factory = appContainer.profileViewModelFactory
            )
            val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()

            ProfileScreen(
                uiState = profileUiState,
                onLogout = {
                    profileViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onViewHistory = {
                    navController.navigate(Routes.ORDER_HISTORY)
                },
                onSuggestionClick = {
                    navController.navigate(Routes.SUGGESTION)
                },
                onChangePassword = { newPassword ->
                    appContainer.authRepository.changePassword(newPassword)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SUGGESTION) {
            val uid = appContainer.authRepository.getCurrentUserId() ?: ""
            var studentName by remember { mutableStateOf("") }
            LaunchedEffect(uid) {
                if (uid.isNotEmpty()) {
                    studentName = appContainer.authRepository.getUserProfile(uid)?.name ?: ""
                }
            }
            SuggestionScreen(
                studentUid = uid,
                studentName = studentName,
                onSubmitSuggestion = { sUid, sName, msg ->
                    appContainer.suggestionDataSource.submitSuggestion(sUid, sName, msg)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
