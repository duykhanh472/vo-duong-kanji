package ua.syt0r.kanji.core.app_data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import ua.syt0r.kanji.BuildConfig
import ua.syt0r.kanji.core.file.PlatformFile
import ua.syt0r.kanji.core.logger.Logger
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

private const val AppDataDatabaseName = "kanji_data"
private const val AppDataDatabasePendingName = "kanji_data.pending"

private const val SqliteHeader = "SQLite format 3\u0000"
private const val MinimumFreeSpaceBytes = 60L * 1024 * 1024
private const val ProgressUpdateStepBytes = 256L * 1024

private val RequiredTables = listOf(
    "kanji_data",
    "kanji_meaning",
    "kanji_reading",
    "character_stroke",
    "kanji_radical",
    "letter_vocab_example",
    "vocab_entity",
    "sentence"
)

class AndroidAppDataInstaller(
    private val context: Context
) : AppDataInstaller {

    override val isDownloadAvailable: Boolean
        get() = BuildConfig.appDataDownloadUrl.isNotBlank()

    override suspend fun checkStatus(): AppDataStatus {
        val databaseFile = databaseFile()
        if (!databaseFile.exists() || databaseFile.length() == 0L) {
            return AppDataStatus.Missing
        }

        return validate(databaseFile, fullCheck = false)
    }

    override suspend fun installFrom(
        file: PlatformFile,
        onProgress: (AppDataInstallProgress) -> Unit
    ): AppDataInstallResult {
        Logger.d("App data import: starting for ${file.uri}")
        val pendingFile = preparePendingFile()

        return try {
            onProgress(AppDataInstallProgress(stage = AppDataInstallProgress.Stage.Preparing))

            val inputStream = context.contentResolver.openInputStream(file.uri)
            if (inputStream == null) {
                Logger.d("App data import: content resolver returned no stream")
                return failure(pendingFile, AppDataInstallResult.Reason.InvalidFile)
            }

            val totalBytes = contentSize(file.uri)
            Logger.d("App data import: stream opened, size=$totalBytes")

            inputStream.use { stream ->
                copyDatabase(stream, pendingFile, totalBytes) { downloaded, total ->
                    onProgress(
                        AppDataInstallProgress(
                            stage = AppDataInstallProgress.Stage.Importing,
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    )
                }
            }

            Logger.d("App data import: copied ${pendingFile.length()} bytes, validating")
            finalizeInstall(pendingFile, onProgress)
        } catch (e: IOException) {
            Logger.d("App data import failed: ${e.message}")
            pendingFile.delete()
            failure(pendingFile, AppDataInstallResult.Reason.Storage, e)
        } catch (e: Exception) {
            Logger.d("App data import failed: ${e.message}")
            pendingFile.delete()
            failure(pendingFile, AppDataInstallResult.Reason.Unknown, e)
        }
    }

    override suspend fun download(
        onProgress: (AppDataInstallProgress) -> Unit
    ): AppDataInstallResult {
        val url = BuildConfig.appDataDownloadUrl
        if (url.isBlank()) {
            return AppDataInstallResult.Failed(AppDataInstallResult.Reason.NotConfigured)
        }

        val downloadFile = File(context.cacheDir, "app_data_download.tmp")
        val pendingFile = preparePendingFile()
        downloadFile.delete()

        return try {
            onProgress(AppDataInstallProgress(stage = AppDataInstallProgress.Stage.Preparing))

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "KanjiDojo/${BuildConfig.versionName}")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    return AppDataInstallResult.Failed(
                        AppDataInstallResult.Reason.Network,
                        "Server returned HTTP $responseCode"
                    )
                }

                val totalBytes = connection.contentLengthLong
                if (!hasFreeSpace(totalBytes + MinimumFreeSpaceBytes)) {
                    return AppDataInstallResult.Failed(AppDataInstallResult.Reason.NotEnoughSpace)
                }

                connection.inputStream.use { inputStream ->
                    downloadFile.outputStream().use { outputStream ->
                        copyWithProgress(inputStream, outputStream, totalBytes) { downloaded, total ->
                            onProgress(
                                AppDataInstallProgress(
                                    stage = AppDataInstallProgress.Stage.Downloading,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            )
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            onProgress(AppDataInstallProgress(stage = AppDataInstallProgress.Stage.Extracting))
            downloadFile.inputStream().use { stream ->
                copyDatabase(stream, pendingFile)
            }
            downloadFile.delete()

            finalizeInstall(pendingFile, onProgress)
        } catch (e: IOException) {
            Logger.d("App data download failed: ${e.message}")
            downloadFile.delete()
            pendingFile.delete()
            failure(pendingFile, AppDataInstallResult.Reason.Network, e)
        } catch (e: Exception) {
            Logger.d("App data download failed: ${e.message}")
            downloadFile.delete()
            pendingFile.delete()
            failure(pendingFile, AppDataInstallResult.Reason.Unknown, e)
        }
    }

    private fun finalizeInstall(
        pendingFile: File,
        onProgress: (AppDataInstallProgress) -> Unit
    ): AppDataInstallResult {
        onProgress(AppDataInstallProgress(stage = AppDataInstallProgress.Stage.Validating))

        val validation = validate(pendingFile, fullCheck = true)
        Logger.d("App data import: validation result $validation")

        when (val status = validation) {
            AppDataStatus.Ready -> Unit
            is AppDataStatus.VersionMismatch -> {
                pendingFile.delete()
                return AppDataInstallResult.Failed(
                    AppDataInstallResult.Reason.VersionMismatch,
                    "Installed data version is ${status.installedVersion}, required is ${status.requiredVersion}",
                    installedVersion = status.installedVersion,
                    requiredVersion = status.requiredVersion
                )
            }

            is AppDataStatus.Invalid -> {
                pendingFile.delete()
                return AppDataInstallResult.Failed(
                    AppDataInstallResult.Reason.InvalidFile,
                    status.reason.name
                )
            }

            AppDataStatus.Missing -> {
                pendingFile.delete()
                return AppDataInstallResult.Failed(AppDataInstallResult.Reason.InvalidFile)
            }
        }

        onProgress(AppDataInstallProgress(stage = AppDataInstallProgress.Stage.Installing))

        val databaseFile = databaseFile()
        databaseFile.parentFile?.mkdirs()

        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        if (databaseFile.exists() && !databaseFile.delete()) {
            pendingFile.delete()
            return AppDataInstallResult.Failed(AppDataInstallResult.Reason.Storage)
        }

        val installed = if (pendingFile.renameTo(databaseFile)) {
            true
        } else {
            runCatching {
                pendingFile.copyTo(databaseFile, overwrite = true)
                pendingFile.delete()
            }.isSuccess
        }

        return if (installed) {
            Logger.d("App data installed at ${databaseFile.absolutePath}")
            AppDataInstallResult.Installed
        } else {
            pendingFile.delete()
            AppDataInstallResult.Failed(AppDataInstallResult.Reason.Storage)
        }
    }

    private fun validate(databaseFile: File, fullCheck: Boolean): AppDataStatus {
        if (!databaseFile.exists() || databaseFile.length() == 0L) {
            return AppDataStatus.Missing
        }

        if (!hasSqliteHeader(databaseFile)) {
            return AppDataStatus.Invalid(AppDataInvalidReason.NotADatabase)
        }

        return try {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                val installedVersion = database.version.toLong()
                if (installedVersion != AppDataDatabaseVersion) {
                    return AppDataStatus.VersionMismatch(
                        installedVersion = installedVersion,
                        requiredVersion = AppDataDatabaseVersion
                    )
                }

                if (!database.tableExists("kanji_hanviet")) {
                    return AppDataStatus.Invalid(AppDataInvalidReason.MissingVietnameseData)
                }

                val missingTables = RequiredTables.filterNot { database.tableExists(it) }
                if (missingTables.isNotEmpty()) {
                    Logger.d("App data is missing tables: $missingTables")
                    return AppDataStatus.Invalid(AppDataInvalidReason.MissingTables)
                }

                val hanVietCount = database.count("kanji_hanviet")
                if (hanVietCount < 1000) {
                    return AppDataStatus.Invalid(AppDataInvalidReason.MissingVietnameseData)
                }

                if (fullCheck && !database.integrityCheck()) {
                    return AppDataStatus.Invalid(AppDataInvalidReason.Corrupt)
                }

                AppDataStatus.Ready
            }
        } catch (e: SQLiteException) {
            Logger.d("App data validation failed: ${e.message}")
            AppDataStatus.Invalid(AppDataInvalidReason.Corrupt)
        } catch (e: Exception) {
            Logger.d("App data validation failed: ${e.message}")
            AppDataStatus.Invalid(AppDataInvalidReason.Unknown)
        }
    }

    /**
     * Copies [inputStream] to [destination], transparently decompressing gzip data.
     *
     * [totalBytes] is the size of the source file as reported by the content
     * provider, used only for progress reporting.
     */
    private fun copyDatabase(
        inputStream: InputStream,
        destination: File,
        totalBytes: Long = -1,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) {
        destination.delete()
        destination.parentFile?.mkdirs()

        val buffered = BufferedInputStream(inputStream)
        buffered.mark(2)
        val firstByte = buffered.read()
        val secondByte = buffered.read()
        buffered.reset()

        // Count the bytes read from the source file (compressed bytes for gzip),
        // so the progress matches the size reported by the content provider.
        val counting = CountingInputStream(buffered)
        val stream: InputStream = if (firstByte == 0x1f && secondByte == 0x8b) {
            GZIPInputStream(counting)
        } else {
            counting
        }

        onProgress(0L, totalBytes)

        stream.use { input ->
            destination.outputStream().use { output ->
                copyWithProgress(input, output, totalBytes) { _, _ ->
                    onProgress(counting.count, totalBytes)
                }
            }
        }
    }

    private fun copyWithProgress(
        inputStream: InputStream,
        outputStream: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        var downloaded = 0L
        var lastReported = 0L

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break

            outputStream.write(buffer, 0, read)
            downloaded += read

            if (downloaded - lastReported >= ProgressUpdateStepBytes) {
                lastReported = downloaded
                onProgress(downloaded, totalBytes)
            }
        }

        outputStream.flush()
        onProgress(downloaded, totalBytes)
    }

    private fun contentSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use -1L
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index < 0 || cursor.isNull(index)) -1L else cursor.getLong(index)
                } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun hasSqliteHeader(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(SqliteHeader.length)
                var read = 0
                while (read < header.size) {
                    val count = input.read(header, read, header.size - read)
                    if (count == -1) break
                    read += count
                }
                read == header.size && String(header, Charsets.US_ASCII) == SqliteHeader
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun SQLiteDatabase.tableExists(tableName: String): Boolean {
        rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getLong(0) > 0
        }
    }

    private fun SQLiteDatabase.count(tableName: String): Long {
        rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun SQLiteDatabase.integrityCheck(): Boolean {
        rawQuery("PRAGMA quick_check", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) != "ok") return false
            }
        }
        return true
    }

    private fun databaseFile(): File = context.getDatabasePath(AppDataDatabaseName)

    private fun preparePendingFile(): File {
        val databaseFile = databaseFile()
        databaseFile.parentFile?.mkdirs()
        val pendingFile = File(databaseFile.parentFile, AppDataDatabasePendingName)
        pendingFile.delete()
        return pendingFile
    }

    private fun hasFreeSpace(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true
        val stat = StatFs(context.filesDir.path)
        return stat.availableBytes >= requiredBytes
    }

    private fun failure(
        pendingFile: File,
        reason: AppDataInstallResult.Reason,
        exception: Exception? = null
    ): AppDataInstallResult.Failed {
        pendingFile.delete()
        return AppDataInstallResult.Failed(reason, exception?.message)
    }

}

private class CountingInputStream(
    private val delegate: InputStream
) : InputStream() {

    var count: Long = 0L
        private set

    override fun read(): Int {
        val value = delegate.read()
        if (value != -1) count++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = delegate.read(b, off, len)
        if (read > 0) count += read
        return read
    }

    override fun close() {
        delegate.close()
    }

}
