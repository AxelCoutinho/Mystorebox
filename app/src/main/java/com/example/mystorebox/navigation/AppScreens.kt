package com.example.mystorebox.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Inventory : Screen("inventory")
    object Scan : Screen("scan")
}
