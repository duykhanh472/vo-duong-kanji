package ua.syt0r.kanji.core.app_data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import ua.syt0r.kanji.core.CustomVersionSqlSchema
import ua.syt0r.kanji.core.app_data.db.AppDataDatabase
import ua.syt0r.kanji.core.logger.Logger
import ua.syt0r.kanji.core.readUserVersion
import java.io.File

private const val AppDataDatabaseName = "kanji_data"

class AndroidAppDataDatabaseProvider(
    private val context: Context
) : AppDataDatabaseProvider {

    private val coroutineScope = CoroutineScope(context = Dispatchers.IO)

    override fun provideAsync(): Deferred<AppDataDatabase> = coroutineScope.async {
        val dbFile = context.getDatabasePath(AppDataDatabaseName)
        if (!dbFile.exists()) {
            throw AppDataNotInstalledException()
        }

        val result = createDriver(dbFile)
        if (result.wasUpgraded) {
            result.driver.close()
            throw AppDataVersionMismatchException(
                installedVersion = result.version,
                requiredVersion = AppDataDatabaseVersion
            )
        }

        AppDataDatabase(result.driver)
    }

    private data class DriverCreationResult(
        val driver: AndroidSqliteDriver,
        val version: Long,
        val wasUpgraded: Boolean
    )

    private suspend fun createDriver(dbFile: File): DriverCreationResult {
        val schema = CustomVersionSqlSchema(AppDataDatabaseVersion, AppDataDatabase.Schema)
        var wasUpgraded = false
        val onDatabaseOpen = CompletableDeferred<Unit>()
        val driver = AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = dbFile.name,
            callback = object : AndroidSqliteDriver.Callback(schema) {
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    super.onUpgrade(db, oldVersion, newVersion)
                    Logger.d("oldVersion[$oldVersion] newVersion[$newVersion]")
                    wasUpgraded = true
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Logger.logMethod()
                    onDatabaseOpen.complete(Unit)
                }
            },
        )
        Logger.d("driver created")
        val version = driver.readUserVersion() // To trigger onOpen callback
        onDatabaseOpen.await()
        Logger.d("driver ready version[$version] wasUpgraded[$wasUpgraded]")
        return DriverCreationResult(driver, version, wasUpgraded)
    }
}
