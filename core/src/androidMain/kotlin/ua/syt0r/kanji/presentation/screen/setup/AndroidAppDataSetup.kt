package ua.syt0r.kanji.presentation.screen.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ua.syt0r.kanji.Res
import ua.syt0r.kanji.app_data_setup_checking
import ua.syt0r.kanji.app_data_setup_data_version
import ua.syt0r.kanji.app_data_setup_decks_note
import ua.syt0r.kanji.app_data_setup_description
import ua.syt0r.kanji.app_data_setup_download
import ua.syt0r.kanji.app_data_setup_download_size
import ua.syt0r.kanji.app_data_setup_downloading
import ua.syt0r.kanji.app_data_setup_downloading_indeterminate
import ua.syt0r.kanji.app_data_setup_error_generic
import ua.syt0r.kanji.app_data_setup_error_invalid_file
import ua.syt0r.kanji.app_data_setup_error_network
import ua.syt0r.kanji.app_data_setup_error_no_vietnamese
import ua.syt0r.kanji.app_data_setup_error_not_configured
import ua.syt0r.kanji.app_data_setup_error_space
import ua.syt0r.kanji.app_data_setup_error_storage
import ua.syt0r.kanji.app_data_setup_error_unknown
import ua.syt0r.kanji.app_data_setup_error_version
import ua.syt0r.kanji.app_data_setup_extracting
import ua.syt0r.kanji.app_data_setup_import
import ua.syt0r.kanji.app_data_setup_import_hint
import ua.syt0r.kanji.app_data_setup_importing
import ua.syt0r.kanji.app_data_setup_importing_indeterminate
import ua.syt0r.kanji.app_data_setup_installed
import ua.syt0r.kanji.app_data_setup_installed_description
import ua.syt0r.kanji.app_data_setup_installing
import ua.syt0r.kanji.app_data_setup_outdated
import ua.syt0r.kanji.app_data_setup_preparing
import ua.syt0r.kanji.app_data_setup_title
import ua.syt0r.kanji.app_data_setup_validating
import ua.syt0r.kanji.core.app_data.AppDataDatabaseVersion
import ua.syt0r.kanji.core.app_data.AppDataInstallProgress
import ua.syt0r.kanji.core.app_data.AppDataInstallResult
import ua.syt0r.kanji.core.app_data.AppDataInstaller
import ua.syt0r.kanji.core.app_data.AppDataStatus
import ua.syt0r.kanji.core.app_data.AndroidAppDataInstaller
import ua.syt0r.kanji.core.file.PlatformFile
import ua.syt0r.kanji.core.logger.Logger
import ua.syt0r.kanji.presentation.getMultiplatformViewModel
import ua.syt0r.kanji.presentation.multiplatformViewModel

fun Module.appDataSetupComponents() {

    single<AppDataInstaller> { AndroidAppDataInstaller(androidContext()) }

    single<AppDataSetupContract.Content> { AndroidAppDataSetupContent }

    multiplatformViewModel {
        AndroidAppDataSetupViewModel(
            viewModelScope = it.component1(),
            installer = get()
        )
    }

}

object AndroidAppDataSetupContent : AppDataSetupContract.Content {

    @Composable
    override fun invoke(onReady: () -> Unit) {

        val viewModel = getMultiplatformViewModel<AndroidAppDataSetupViewModel>()
        val state by viewModel.state.collectAsState()

        val filePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            Logger.d("App data setup: file picker result $uri")
            if (uri != null) {
                viewModel.installFrom(PlatformFile(uri))
            }
        }

        LaunchedEffect(Unit) {
            viewModel.start()
        }

        LaunchedEffect(state.phase) {
            if (state.phase == AppDataSetupState.Phase.Ready) {
                onReady()
            }
        }

        AppDataSetupScreen(
            state = state,
            isDownloadAvailable = viewModel.isDownloadAvailable,
            onDownload = viewModel::download,
            onImport = { filePicker.launch(arrayOf("*/*")) }
        )

    }

}

class AndroidAppDataSetupViewModel(
    private val viewModelScope: CoroutineScope,
    private val installer: AppDataInstaller
) {

    val isDownloadAvailable: Boolean = installer.isDownloadAvailable

    private val _state = MutableStateFlow(AppDataSetupState(AppDataSetupState.Phase.Checking))
    val state: StateFlow<AppDataSetupState> = _state.asStateFlow()

    private var started = false
    private var installJob: Job? = null

    fun start() {
        if (started) return
        started = true
        checkStatus()
    }

    fun download() {
        install { onProgress -> installer.download(onProgress) }
    }

    fun installFrom(file: PlatformFile) {
        install { onProgress -> installer.installFrom(file, onProgress) }
    }

    private fun checkStatus() {
        viewModelScope.launch {
            _state.value = AppDataSetupState(AppDataSetupState.Phase.Checking)
            val status = runCatching {
                withContext(Dispatchers.IO) { installer.checkStatus() }
            }.getOrDefault(AppDataStatus.Missing)

            _state.value = if (status is AppDataStatus.Ready) {
                AppDataSetupState(AppDataSetupState.Phase.Ready)
            } else {
                AppDataSetupState(AppDataSetupState.Phase.Setup, status = status)
            }
        }
    }

    private fun install(
        action: suspend ((AppDataInstallProgress) -> Unit) -> AppDataInstallResult
    ) {
        Logger.d("App data setup: install requested")
        if (installJob?.isActive == true) return

        installJob = viewModelScope.launch {
            _state.value = AppDataSetupState(AppDataSetupState.Phase.Installing)

            val result = withContext(Dispatchers.IO) {
                action { progress ->
                    _state.update { it.copy(progress = progress) }
                }
            }
            Logger.d("App data setup: install finished with $result")

            when (result) {
                AppDataInstallResult.Installed -> {
                    _state.value = AppDataSetupState(AppDataSetupState.Phase.Ready)
                }

                is AppDataInstallResult.Failed -> {
                    val status = runCatching {
                        withContext(Dispatchers.IO) { installer.checkStatus() }
                    }.getOrNull()
                    _state.update {
                        it.copy(
                            phase = AppDataSetupState.Phase.Setup,
                            progress = null,
                            error = result,
                            status = status ?: it.status
                        )
                    }
                }
            }
        }
    }

}

data class AppDataSetupState(
    val phase: Phase,
    val status: AppDataStatus? = null,
    val progress: AppDataInstallProgress? = null,
    val error: AppDataInstallResult.Failed? = null
) {
    enum class Phase { Checking, Setup, Installing, Ready }
}

@Composable
fun AppDataSetupScreen(
    state: AppDataSetupState,
    isDownloadAvailable: Boolean,
    onDownload: () -> Unit,
    onImport: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Kanji Dojo",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            when (state.phase) {
                AppDataSetupState.Phase.Checking -> {
                    Text(
                        text = stringResource(Res.string.app_data_setup_checking),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                AppDataSetupState.Phase.Setup -> {
                    SetupContent(
                        status = state.status,
                        error = state.error,
                        isDownloadAvailable = isDownloadAvailable,
                        onDownload = onDownload,
                        onImport = onImport
                    )
                }

                AppDataSetupState.Phase.Installing -> {
                    InstallContent(progress = state.progress)
                }

                AppDataSetupState.Phase.Ready -> {
                    Text(
                        text = stringResource(Res.string.app_data_setup_installed),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.app_data_setup_installed_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

        }
    }

}

@Composable
private fun SetupContent(
    status: AppDataStatus?,
    error: AppDataInstallResult.Failed?,
    isDownloadAvailable: Boolean,
    onDownload: () -> Unit,
    onImport: () -> Unit
) {

    Text(
        text = stringResource(Res.string.app_data_setup_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.app_data_setup_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    StatusMessage(status)

    Spacer(Modifier.height(24.dp))

    if (isDownloadAvailable) {
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.app_data_setup_download))
                Text(
                    text = stringResource(Res.string.app_data_setup_download_size),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    OutlinedButton(
        onClick = onImport,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(stringResource(Res.string.app_data_setup_import))
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.app_data_setup_import_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    if (error != null) {
        Spacer(Modifier.height(16.dp))
        ErrorCard(error = error)
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = stringResource(Res.string.app_data_setup_decks_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.app_data_setup_data_version, AppDataDatabaseVersion),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

}

@Composable
private fun StatusMessage(status: AppDataStatus?) {
    when (status) {
        is AppDataStatus.VersionMismatch -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    Res.string.app_data_setup_outdated,
                    status.installedVersion,
                    status.requiredVersion
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        is AppDataStatus.Invalid -> {
            Spacer(Modifier.height(8.dp))
            val message = when (status.reason) {
                ua.syt0r.kanji.core.app_data.AppDataInvalidReason.MissingVietnameseData ->
                    Res.string.app_data_setup_error_no_vietnamese

                else -> Res.string.app_data_setup_error_invalid_file
            }
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        else -> Unit
    }
}

@Composable
private fun InstallContent(progress: AppDataInstallProgress?) {

    val stage = progress?.stage
    val text = when (stage) {
        AppDataInstallProgress.Stage.Preparing -> stringResource(Res.string.app_data_setup_preparing)
        AppDataInstallProgress.Stage.Importing -> {
            val percent = progress.fraction?.let { (it * 100).toInt() }
            if (percent != null) {
                stringResource(Res.string.app_data_setup_importing, percent)
            } else {
                stringResource(Res.string.app_data_setup_importing_indeterminate)
            }
        }

        AppDataInstallProgress.Stage.Downloading -> {
            val percent = progress.fraction?.let { (it * 100).toInt() }
            if (percent != null) {
                stringResource(Res.string.app_data_setup_downloading, percent)
            } else {
                stringResource(Res.string.app_data_setup_downloading_indeterminate)
            }
        }

        AppDataInstallProgress.Stage.Extracting -> stringResource(Res.string.app_data_setup_extracting)
        AppDataInstallProgress.Stage.Validating -> stringResource(Res.string.app_data_setup_validating)
        AppDataInstallProgress.Stage.Installing -> stringResource(Res.string.app_data_setup_installing)
        null -> stringResource(Res.string.app_data_setup_checking)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(16.dp))

    val fraction = progress?.fraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    if (stage == AppDataInstallProgress.Stage.Downloading ||
        stage == AppDataInstallProgress.Stage.Importing ||
        stage == AppDataInstallProgress.Stage.Extracting
    ) {
        val downloaded = progress.downloadedBytes
        val total = progress.totalBytes
        if (downloaded > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (total > 0) {
                    "${formatMb(downloaded)} / ${formatMb(total)}"
                } else {
                    formatMb(downloaded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

}

@Composable
private fun ErrorCard(
    error: AppDataInstallResult.Failed
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = errorMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

}

@Composable
private fun errorMessage(error: AppDataInstallResult.Failed): String {
    return when (error.reason) {
        AppDataInstallResult.Reason.NotConfigured ->
            stringResource(Res.string.app_data_setup_error_not_configured)

        AppDataInstallResult.Reason.Network ->
            stringResource(Res.string.app_data_setup_error_network)

        AppDataInstallResult.Reason.InvalidFile -> {
            if (error.details == "MissingVietnameseData") {
                stringResource(Res.string.app_data_setup_error_no_vietnamese)
            } else {
                stringResource(Res.string.app_data_setup_error_invalid_file)
            }
        }

        AppDataInstallResult.Reason.VersionMismatch ->
            stringResource(
                Res.string.app_data_setup_error_version,
                error.installedVersion ?: -1,
                error.requiredVersion ?: AppDataDatabaseVersion
            )

        AppDataInstallResult.Reason.NotEnoughSpace ->
            stringResource(Res.string.app_data_setup_error_space)

        AppDataInstallResult.Reason.Storage ->
            stringResource(Res.string.app_data_setup_error_storage)

        AppDataInstallResult.Reason.Unknown ->
            stringResource(Res.string.app_data_setup_error_unknown, error.details ?: "?")
    }
}

private fun formatMb(bytes: Long): String =
    String.format("%.1f MB", bytes / 1024.0 / 1024.0)
