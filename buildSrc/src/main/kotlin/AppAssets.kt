data class Asset(
    val fileName: String,
    val url: String?
)

data class AssetLocation(
    val expectedAssets: List<Asset>
)

object AppAssets {

    const val AppDataDatabaseVersion = 16
    const val AppDataAssetFileName = "kanji-dojo-data-base-v$AppDataDatabaseVersion.sql"

    /**
     * GitHub Release asset URL of the gzipped app data database.
     *
     * The data is no longer bundled into the APK. Set this before publishing a
     * release, or pass `-PkanjiDataUrl=...` at build time. When empty, the
     * first launch screen only offers file import.
     */
    const val AppDataDownloadUrl = ""

    val kanaVoiceOpus = Asset(
        fileName = "ja-JP-Neural2-B.opus",
        url = "https://github.com/syt0r/Kanji-Dojo-Data/releases/download/voice-v1/ja-JP-Neural2-B.opus"
    )

    val AndroidAssetsLocation = AssetLocation(
        expectedAssets = listOf(kanaVoiceOpus)
    )

}
