package ua.syt0r.kanji.presentation.screen.main.screen.deck_picker.use_case

import ua.syt0r.kanji.presentation.screen.main.screen.deck_picker.data.DeckPickerCategory
import ua.syt0r.kanji.presentation.screen.main.screen.deck_picker.data.DeckPickerLetterCategories
import ua.syt0r.kanji.presentation.screen.main.screen.deck_picker.data.DeckPickerScreenConfiguration

interface GetDeckPickerCategoriesUseCase {
    suspend operator fun invoke(configuration: DeckPickerScreenConfiguration): List<DeckPickerCategory>
}

class DefaultGetDeckPickerCategoriesUseCase : GetDeckPickerCategoriesUseCase {

    override suspend fun invoke(
        configuration: DeckPickerScreenConfiguration
    ): List<DeckPickerCategory> {
        return DeckPickerLetterCategories
    }

}
