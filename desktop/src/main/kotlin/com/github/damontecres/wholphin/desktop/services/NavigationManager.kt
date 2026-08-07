package com.github.damontecres.wholphin.desktop.services

import androidx.compose.runtime.mutableStateListOf
import com.github.damontecres.wholphin.desktop.ui.nav.Destination

/**
 * Manages navigating between pages and manages the app's back stack.
 * Desktop equivalent of the app's [com.github.damontecres.wholphin.services.NavigationManager]
 * (which used the Android nav3 back stack).
 */
class NavigationManager {
    var backStack = mutableStateListOf<Destination>(Destination.Home())

    /**
     * Go to the specified [Destination]
     */
    fun navigateTo(destination: Destination) {
        backStack.add(destination)
    }

    /**
     * Go to the specified [Destination], but reset the back stack to Home first
     */
    fun navigateToFromDrawer(destination: Destination) {
        goToHome()
        backStack.add(destination)
    }

    /**
     * Go to the previous page
     */
    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    /**
     * Go all the way back to the home page
     */
    fun goToHome() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
        if (backStack[0] !is Destination.Home) {
            backStack[0] = Destination.Home()
        }
    }
}
