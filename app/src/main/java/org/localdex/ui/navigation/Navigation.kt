package org.localdex.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "LocalDeX")
    data object Setup : Screen("setup", "Setup Wizard")
    data object Profiles : Screen("profiles", "Display Profiles")
    data object Settings : Screen("settings", "Preferences")
}
