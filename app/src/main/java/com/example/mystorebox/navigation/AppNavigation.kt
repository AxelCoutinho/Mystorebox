package com.example.mystorebox.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mystorebox.data.network.ScryfallCard
import com.example.mystorebox.ui.screens.home.HomeScreen
import com.example.mystorebox.ui.screens.inventory.InventoryScreen
import com.example.mystorebox.ui.screens.scanner.ScanScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val pendingCardsToSave = remember { mutableStateListOf<ScryfallCard>() }

    val items = listOf(
        Screen.Home to Icons.Default.Home,
        Screen.Inventory to Icons.Default.Inventory,
        Screen.Scan to Icons.Default.PhotoCamera
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { (screen, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screen.route) },
                        label = { Text(screen.route.replaceFirstChar { it.uppercase() }) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(contentPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) { HomeScreen() }

            composable(Screen.Inventory.route) {
                InventoryScreen(
                    scannedCards = pendingCardsToSave,
                    onDismiss = {
                        pendingCardsToSave.clear()
                    },
                    onSaveInventory = { _, _, _ ->
                        pendingCardsToSave.clear()
                    }
                )
            }

            composable(Screen.Scan.route) {
                ScanScreen(
                    onNavigateToInventory = { cardsFromScanner ->
                        pendingCardsToSave.clear()
                        pendingCardsToSave.addAll(cardsFromScanner)

                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}