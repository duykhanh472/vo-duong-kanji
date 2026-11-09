package ua.syt0r.kanji.presentation.screen.main.screen.info

import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope

interface InfoScreenContract {

    companion object {

        const val ListPageItemsCount: Int = 50
        const val ListPrefetchDistance: Int = 50

        fun getJishoSearchUrl(searchTerm: String): String {
            return "https://jisho.org/search/${searchTerm.percentEncode()}"
        }

    }

    interface ViewModel {
        val state: State<ScreenState>
    }

    sealed interface ScreenState {

        data object Loading : ScreenState

        data class NoData(
            val data: InfoScreenData
        ) : ScreenState

        sealed interface Loaded : ScreenState {

            data class Letter(
                val data: LetterInfoData
            ) : Loaded

        }

    }

    interface LoadLetterStateUseCase {
        suspend operator fun invoke(character: String, coroutineScope: CoroutineScope): ScreenState
    }

}

private const val UrlHexDigits = "0123456789ABCDEF"

private fun String.percentEncode(): String = buildString {
    for (byte in encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        val isUnreserved = char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '-' || char == '.' || char == '_' || char == '~'

        if (isUnreserved) {
            append(char)
        } else {
            append('%')
            append(UrlHexDigits[value shr 4])
            append(UrlHexDigits[value and 0x0F])
        }
    }
}

