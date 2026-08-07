package com.github.damontecres.wholphin.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.damontecres.wholphin.desktop.di.desktopModule
import com.github.damontecres.wholphin.desktop.ui.WholphinApp
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(desktopModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Wholphin",
        ) {
            WholphinApp()
        }
    }
}
