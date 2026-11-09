package ua.syt0r.kanji.presentation.screen.main.screen.home.screen.settings.items

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import ua.syt0r.kanji.Res
import ua.syt0r.kanji.core.kanji_readings.KanjiReadingsSettings
import ua.syt0r.kanji.presentation.screen.main.MainNavigationState
import ua.syt0r.kanji.presentation.screen.main.screen.home.screen.settings.SettingsScreenContract
import ua.syt0r.kanji.presentation.screen.main.screen.home.screen.settings.SettingsSwitchRow
import ua.syt0r.kanji.setting_show_on_kun_message
import ua.syt0r.kanji.setting_show_on_kun_title

class KanjiReadingsSettingItem(
    private val readingsSettings: KanjiReadingsSettings
) : SettingsScreenContract.ListItem {

    @Composable
    override fun content(mainNavigationState: MainNavigationState) {

        SettingsSwitchRow(
            title = stringResource(Res.string.setting_show_on_kun_title),
            message = stringResource(Res.string.setting_show_on_kun_message),
            isEnabled = readingsSettings.showOnKunReadings.value,
            onToggled = {
                readingsSettings.setShowOnKunReadings(
                    !readingsSettings.showOnKunReadings.value
                )
            }
        )

    }

}
