package ua.syt0r.kanji.presentation.screen.main.screen.info

import org.koin.dsl.module
import ua.syt0r.kanji.presentation.multiplatformViewModel
import ua.syt0r.kanji.presentation.screen.main.screen.info.use_case.InfoLoadLetterStateUseCase

val infoScreenModule = module {

    factory<InfoScreenContract.LoadLetterStateUseCase> {
        InfoLoadLetterStateUseCase(
            appDataRepository = get(),
            characterClassifier = get(),
            analyticsManager = get()
        )
    }

    multiplatformViewModel<InfoScreenContract.ViewModel> {
        InfoScreenViewModel(
            viewModelScope = it.component1(),
            screenData = it.component2(),
            loadLetterStateUseCase = get(),
            analyticsManager = get()
        )
    }

}
