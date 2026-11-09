package ua.syt0r.kanji.presentation.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ua.syt0r.kanji.core.app_data.data.JapaneseWord
import ua.syt0r.kanji.core.app_data.data.VocabReading
import ua.syt0r.kanji.core.app_data.data.formattedVocabDefinition
import ua.syt0r.kanji.presentation.common.ui.ClickableFuriganaText
import ua.syt0r.kanji.presentation.common.ui.FuriganaText

@Composable
fun JapaneseWordUI(
    index: Int,
    onClick: (() -> Unit)? = null,
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {

    AppListItem(
        modifier = modifier,
        leadingContent = { Text((index + 1).toString()) },
        headlineContent = headline,
        trailingContent = null,
        onClick = onClick
    )

}

@Composable
fun JapaneseWordUI(
    index: Int,
    word: JapaneseWord,
    onClick: (() -> Unit)? = null,
    onFuriganaClick: ((String) -> Unit)? = null,
    headline: @Composable () -> Unit = {
        FuriganaWordHeadline(word.reading, word.combinedGlossary(), onFuriganaClick)
    },
    modifier: Modifier = Modifier
) = JapaneseWordUI(
    index = index,
    onClick = onClick,
    headline = headline,
    modifier = modifier
)

@Composable
fun FuriganaWordHeadline(
    reading: VocabReading,
    glossary: String,
    onFuriganaClick: ((String) -> Unit)? = null
) {
    val furigana = formattedVocabDefinition(reading, glossary)
    if (onFuriganaClick != null) ClickableFuriganaText(furigana, onFuriganaClick)
    else FuriganaText(furigana)
}
