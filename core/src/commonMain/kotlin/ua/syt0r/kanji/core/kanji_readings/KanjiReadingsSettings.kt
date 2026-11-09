package ua.syt0r.kanji.core.kanji_readings

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ua.syt0r.kanji.core.user_data.preferences.PreferencesContract

/**
 * Observable display setting for kanji readings.
 *
 * Mirrors [ua.syt0r.kanji.core.theme_manager.ThemeManager]: the initial value is
 * read once synchronously, then kept in sync with the preference flow so the UI
 * can read it without flicker.
 */
class KanjiReadingsSettings(
    private val appPreferences: PreferencesContract.AppPreferences,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
) {

    private val coroutineScope = CoroutineScope(dispatcher)

    private val _showOnKunReadings = mutableStateOf(
        value = runBlocking { appPreferences.showOnKunReadings.get() }
    )
    val showOnKunReadings: State<Boolean> = _showOnKunReadings

    init {
        appPreferences.showOnKunReadings
            .onModified
            .onEach { _showOnKunReadings.value = it }
            .launchIn(coroutineScope)
    }

    fun setShowOnKunReadings(value: Boolean) {
        coroutineScope.launch { appPreferences.showOnKunReadings.set(value) }
    }

}
