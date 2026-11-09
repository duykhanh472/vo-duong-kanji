package ua.syt0r.kanji.core.app_data

import ua.syt0r.kanji.core.file.PlatformFile

/**
 * Installs the external app data database (kanji, readings, vocab, ...).
 *
 * The database is not bundled into the APK anymore, so the app has to download
 * it or import a file provided by the user before the main screen can start.
 */
interface AppDataInstaller {

    /** Whether this build has a download URL configured. */
    val isDownloadAvailable: Boolean

    /** Checks the currently installed database. */
    suspend fun checkStatus(): AppDataStatus

    /** Installs a user-selected database file (.sql, .sqlite, .db or .sql.gz). */
    suspend fun installFrom(
        file: PlatformFile,
        onProgress: (AppDataInstallProgress) -> Unit = {}
    ): AppDataInstallResult

    /** Downloads and installs the database configured for this build. */
    suspend fun download(
        onProgress: (AppDataInstallProgress) -> Unit = {}
    ): AppDataInstallResult

}

sealed interface AppDataStatus {

    data object Ready : AppDataStatus

    data object Missing : AppDataStatus

    data class VersionMismatch(
        val installedVersion: Long,
        val requiredVersion: Long
    ) : AppDataStatus

    data class Invalid(
        val reason: AppDataInvalidReason
    ) : AppDataStatus

}

enum class AppDataInvalidReason {
    NotADatabase,
    Corrupt,
    MissingTables,
    MissingVietnameseData,
    Unknown
}

data class AppDataInstallProgress(
    val stage: Stage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1
) {

    val fraction: Float?
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            null
        }

    enum class Stage {
        Preparing,
        Importing,
        Downloading,
        Extracting,
        Validating,
        Installing
    }

}

sealed interface AppDataInstallResult {

    data object Installed : AppDataInstallResult

    data class Failed(
        val reason: Reason,
        val details: String? = null,
        val installedVersion: Long? = null,
        val requiredVersion: Long? = null
    ) : AppDataInstallResult

    enum class Reason {
        NotConfigured,
        Network,
        InvalidFile,
        VersionMismatch,
        NotEnoughSpace,
        Storage,
        Unknown
    }

}

class AppDataNotInstalledException : IllegalStateException(
    "App data database is not installed"
)

class AppDataVersionMismatchException(
    val installedVersion: Long,
    val requiredVersion: Long
) : IllegalStateException(
    "App data version mismatch: installed=$installedVersion required=$requiredVersion"
)
