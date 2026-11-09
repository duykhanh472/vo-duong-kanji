package ua.syt0r.kanji.core.file

/**
 * Minimal platform-independent byte source used by backup restore.
 */
interface ContentSource {

    /**
     * Reads up to [buffer].size bytes into [buffer].
     * Returns the number of bytes read, or -1 when the end of the source is reached.
     */
    suspend fun read(buffer: ByteArray): Int

    suspend fun close()

}
