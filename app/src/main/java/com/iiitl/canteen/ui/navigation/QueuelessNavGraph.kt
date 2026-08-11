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
import com.iiitl.canteen.AppContainer
import com.iiitl.canteen.data.model.UserRole
import com.iiitl.canteen.data.repository.CartItem
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
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val CAFETERIA_SELECTION = "cafeteria_selection"
    const val MENU = "menu/{cafeteriaId}"
    const val CART = "cart/{cafeteriaId}"
    const val ORDER_STATUS = "order_status/{orderId}"
    const val ORDER_HISTORY = "order_history"
    const val CAFE_QUEUE = "cafe_queue/{cafeteriaId}"

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
                    navController.navigate(Routes.CAFETERIA_SELECTION) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
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
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = appContainer.loginViewModelFactory
            )
            LoginScreen(viewModel = loginViewModel)
        }

        composable(Routes.CAFETERIA_SELECTION) {
            CafeteriaSelectionScreen(
                onCafeteriaSelected = { cafeteriaId ->
                    scope.launch {
                        val uid = appContainer.authRepository.getCurrentUserId()
                        if (uid != null) {
                            val role = appContainer.authRepository.getUserRole(uid)
                            if (role == UserRole.STUDENT) {
                                navController.navigate(Routes.menu(cafeteriaId))
                            } else {
                                navController.navigate(Routes.cafeQueue(cafeteriaId))
                            }
                        } else {
                            navController.navigate(Routes.menu(cafeteriaId))
                        }
                    }
                }
            )
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

            MenuScreen(
                uiState = menuUiState,
                cafeteriaId = cafeteriaId,
                onItemClick = { menuItem ->
                    cartViewModel.addItem(menuItem)
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onViewCartClick = {
                    navController.navigate(Routes.cart(cafeteriaId))
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

            CartScreen(
                uiState = cartUiState,
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
                onConfirmReducedOrder = { /* Status updates handled via staff flow / repo */ },
                onCancelOrder = { /* Status updates handled via repo */ },
                onViewHistory = {
                    navController.navigate(Routes.ORDER_HISTORY)
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
                }
            )
        }
    }
}
