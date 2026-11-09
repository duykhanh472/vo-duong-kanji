package ua.syt0r.kanji.core.user_data

import android.content.Context
import androidx.core.net.toUri
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.syt0r.kanji.core.file.ContentSource
import ua.syt0r.kanji.core.file.PlatformFile
import ua.syt0r.kanji.core.user_data.database.DatabaseConnection
import ua.syt0r.kanji.core.user_data.database.UserDataDatabaseContract
import ua.syt0r.kanji.core.user_data.db.UserDataDatabase
import java.io.File

class AndroidUserDataDatabasePlatformHandler(
    private val context: Context,
    private val migrationProvider: UserDataDatabaseContract.MigrationProvider
) : UserDataDatabaseContract.PlatformHandler {

    companion object {
        private const val DEFAULT_DB_NAME = "user_data"
    }

    override suspend fun newConnection(): DatabaseConnection = withContext(Dispatchers.Main) {
        val driver = AndroidSqliteDriver(
            schema = UserDataDatabase.Schema,
            context = context,
            name = DEFAULT_DB_NAME,
            callback = AndroidSqliteDriver.Callback(
                UserDataDatabase.Schema,
                *migrationProvider()
            )
        )
        DatabaseConnection(
            sqlDriver = driver,
            database = UserDataDatabase(driver)
        )
    }

    override fun getDatabaseAsFile(): PlatformFile {
        return PlatformFile(getDBFile().toUri())
    }

    override suspend fun replaceDatabaseFile(content: ContentSource) {
        getDBFile().outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = content.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
            }
        }
    }

    private fun getDBFile(): File {
        return context.getDatabasePath(DEFAULT_DB_NAME)!!
    }

}