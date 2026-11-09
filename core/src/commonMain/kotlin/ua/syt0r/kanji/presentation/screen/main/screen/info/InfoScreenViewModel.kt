package ua.syt0r.kanji.presentation.screen.main.screen.info

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.syt0r.kanji.core.analytics.AnalyticsManager
import ua.syt0r.kanji.presentation.screen.main.screen.info.InfoScreenContract.ScreenState

class InfoScreenViewModel(
    private val viewModelScope: CoroutineScope,
    screenData: InfoScreenData,
    private val loadLetterStateUseCase: InfoScreenContract.LoadLetterStateUseCase,
    private val analyticsManager: AnalyticsManager
) : InfoScreenContract.ViewModel {

    override val state = mutableStateOf<ScreenState>(ScreenState.Loading)

    init {
        viewModelScope.launch {
            state.value = withContext(Dispatchers.IO) {
                val letter = (screenData as InfoScreenData.Letter).letter
                reportLetter(letter)
                loadLetterStateUseCase(letter, viewModelScope)
            }
        }
    }

    private fun reportLetter(letter: String) {
        analyticsManager.sendEvent("kanji_info_open") { put("character", letter) }
    }

}
