package ua.syt0r.kanji.presentation.screen.setup

import androidx.compose.runtime.Composable

/**
 * Platform specific first-launch screen that makes sure the external app data
 * database is installed before the main app is created.
 */
interface AppDataSetupContract {

    interface Content {
        @Composable
        fun invoke(onReady: () -> Unit)
    }

}
