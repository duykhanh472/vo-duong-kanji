package ua.syt0r.kanji.presentation.common

import ua.syt0r.kanji.core.srs.LetterPracticeType
import ua.syt0r.kanji.core.user_data.preferences.PreferencesLetterPracticeType
import ua.syt0r.kanji.presentation.common.resources.string.StringResolveScope
import ua.syt0r.kanji.presentation.screen.main.screen.practice_common.DisplayableEnum

interface ScreenPracticeType : DisplayableEnum

enum class ScreenLetterPracticeType(
    val dataType: LetterPracticeType,
    val preferencesType: PreferencesLetterPracticeType,
    override val titleResolver: StringResolveScope<String>,
) : ScreenPracticeType {

    Writing(
        dataType = LetterPracticeType.Writing,
        preferencesType = PreferencesLetterPracticeType.Writing,
        titleResolver = { letterPracticeTypeWriting }
    ),
    Reading(
        dataType = LetterPracticeType.Reading,
        preferencesType = PreferencesLetterPracticeType.Reading,
        titleResolver = { letterPracticeTypeReading }
    );

    companion object {
        fun from(practiceType: LetterPracticeType): ScreenLetterPracticeType {
            return values().first { it.dataType == practiceType }
        }

        fun from(practiceType: PreferencesLetterPracticeType): ScreenLetterPracticeType {
            return values().first { it.preferencesType == practiceType }
        }
    }

}
