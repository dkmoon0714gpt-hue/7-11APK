package com.chaoshangka.manager.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.ui.screens.CardDetailScreen
import com.chaoshangka.manager.ui.screens.CardListScreen
import com.chaoshangka.manager.ui.screens.CategoriesScreen
import com.chaoshangka.manager.ui.screens.FullscreenBarcodeScreen
import com.chaoshangka.manager.ui.screens.HomeScreen
import com.chaoshangka.manager.ui.screens.ScanResultScreen
import com.chaoshangka.manager.ui.screens.ScanScreen
import com.chaoshangka.manager.ui.screens.SettingsScreen
import com.chaoshangka.manager.ui.screens.StoreScreen
import com.chaoshangka.manager.ui.screens.UsedScreen
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.ScanViewModel

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val factory = remember(container) { AppViewModelFactory(container) }
    // 掃描流程跨兩個畫面（拍照 → 確認），因此 ViewModel 提升到這裡共用。
    val scanViewModel: ScanViewModel = viewModel(factory = factory)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != null && Routes.bottomBarRoutes.contains(currentRoute)) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigateToTab(Routes.HOME) },
                        icon = { Icon(imageVector = Icons.Default.Home, contentDescription = null) },
                        label = { Text("首頁") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CARDS,
                        onClick = { navController.navigateToTab(Routes.CARDS) },
                        icon = { Icon(imageVector = Icons.Default.List, contentDescription = null) },
                        label = { Text("全部卡片") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.USED,
                        onClick = { navController.navigateToTab(Routes.USED) },
                        icon = { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null) },
                        label = { Text("已使用") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateToTab(Routes.SETTINGS) },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                        label = { Text("設定") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onScan = { navController.navigate(Routes.SCAN) },
                    onManualAdd = {
                        scanViewModel.startManualEntry()
                        navController.navigate(Routes.SCAN_RESULT)
                    },
                    onOpenStore = { store -> navController.navigate(Routes.store(store)) },
                    onOpenSearch = { navController.navigate(Routes.CARDS) },
                    onOpenUsed = { navController.navigate(Routes.USED) },
                )
            }

            composable(Routes.CARDS) {
                CardListScreen(
                    container = container,
                    onBack = null,
                    onScan = { navController.navigate(Routes.SCAN) },
                    onOpenCard = { cardId -> navController.navigate(Routes.card(cardId)) },
                )
            }

            composable(Routes.USED) {
                UsedScreen(
                    container = container,
                    onBack = null,
                    onOpenCard = { cardId -> navController.navigate(Routes.card(cardId)) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = null,
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                )
            }

            composable(
                route = Routes.STORE,
                arguments = listOf(navArgument(Routes.ARG_STORE_KEY) { type = NavType.StringType }),
            ) { entry ->
                val storeKey = entry.arguments?.getString(Routes.ARG_STORE_KEY).orEmpty()
                StoreScreen(
                    container = container,
                    storeType = StoreType.fromKey(storeKey),
                    onBack = { navController.popBackStack() },
                    onScan = { navController.navigate(Routes.SCAN) },
                    onOpenCard = { cardId -> navController.navigate(Routes.card(cardId)) },
                )
            }

            composable(
                route = Routes.CARD,
                arguments = listOf(navArgument(Routes.ARG_CARD_ID) { type = NavType.LongType }),
            ) { entry ->
                val cardId = entry.arguments?.getLong(Routes.ARG_CARD_ID) ?: 0L
                CardDetailScreen(
                    container = container,
                    cardId = cardId,
                    onBack = { navController.popBackStack() },
                    onOpenFullscreen = { id -> navController.navigate(Routes.barcode(id)) },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.BARCODE,
                arguments = listOf(navArgument(Routes.ARG_CARD_ID) { type = NavType.LongType }),
            ) { entry ->
                val cardId = entry.arguments?.getLong(Routes.ARG_CARD_ID) ?: 0L
                FullscreenBarcodeScreen(
                    container = container,
                    cardId = cardId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SCAN) {
                ScanScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onPhotoReady = { path ->
                        scanViewModel.onPhotoCaptured(path)
                        navController.navigate(Routes.SCAN_RESULT)
                    },
                    onManualEntry = {
                        scanViewModel.startManualEntry()
                        navController.navigate(Routes.SCAN_RESULT)
                    },
                )
            }

            composable(Routes.SCAN_RESULT) {
                ScanResultScreen(
                    container = container,
                    viewModel = scanViewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { cardId ->
                        scanViewModel.reset()
                        navController.navigate(Routes.card(cardId)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onOpenExistingCard = { cardId -> navController.navigate(Routes.card(cardId)) },
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(container = container, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
