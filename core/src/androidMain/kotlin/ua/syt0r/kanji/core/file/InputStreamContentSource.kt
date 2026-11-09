package ua.syt0r.kanji.core.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.InputStream

class InputStreamContentSource(
    private val inputStream: InputStream
) : ContentSource {

    override suspend fun read(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
        inputStream.read(buffer)
    }

    override suspend fun close() {
        inputStream.close()
    }

}
