package com.github.damontecres.wholphin.desktop.services

import androidx.compose.runtime.mutableStateListOf
import com.github.damontecres.wholphin.desktop.ui.nav.Destination

/**
 * Manages navigating between pages and manages the app's back stack.
 * Desktop equivalent of the app's [com.github.damontecres.wholphin.services.NavigationManager]
 * (which used the Android nav3 back stack).
 */
class NavigationManager {
    private val _backStack = mutableStateListOf<Destination>(Destination.Home())
    val backStack: List<Destination> get() = _backStack

    /**
     * Go to the specified [Destination]
     */
    fun navigateTo(destination: Destination) {
        _backStack.add(destination)
    }

    /**
     * Go to the specified [Destination], but reset the back stack to Home first
     */
    fun navigateToFromDrawer(destination: Destination) {
        goToHome()
        _backStack.add(destination)
    }

    /**
     * Go to the previous page
     */
    fun goBack() {
        if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.size - 1)
        }
    }

    /**
     * Go all the way back to the home page
     */
    fun goToHome() {
        while (_backStack.size > 1) {
            _backStack.removeAt(_backStack.size - 1)
        }
        if (_backStack[0] !is Destination.Home) {
            _backStack[0] = Destination.Home()
        }
    }
}
