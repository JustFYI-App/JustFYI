package app.justfyi.platform

import android.content.Context
import app.justfyi.domain.model.ExportData
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Android implementation of ZipService.
 * Uses java.util.zip.ZipOutputStream for ZIP creation.
 * Files are written to the app's cache directory.
 */
@Inject
class AndroidZipService(
    private val context: Context,
) : ZipService {
    private val prettyJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    override suspend fun createZip(
        data: ExportData,
        fileName: String,
    ): FileResult =
        withContext(Dispatchers.IO) {
            val zipFile = File(context.cacheDir, fileName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                // Add user.json
                addJsonEntry(
                    zipOut,
                    "user.json",
                    prettyJson.encodeToString(data.user),
                )

                // Add interactions.json
                addJsonEntry(
                    zipOut,
                    "interactions.json",
                    prettyJson.encodeToString(data.interactions),
                )

                // Add notifications.json
                addJsonEntry(
                    zipOut,
                    "notifications.json",
                    prettyJson.encodeToString(data.notifications),
                )

                // Add reports.json
                addJsonEntry(
                    zipOut,
                    "reports.json",
                    prettyJson.encodeToString(data.reports),
                )
            }

            FileResult(
                filePath = zipFile.absolutePath,
                mimeType = MIME_TYPE_ZIP,
            )
        }

    private fun addJsonEntry(
        zipOut: ZipOutputStream,
        entryName: String,
        jsonContent: String,
    ) {
        val entry = ZipEntry(entryName)
        zipOut.putNextEntry(entry)
        zipOut.write(jsonContent.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }

    companion object {
        const val MIME_TYPE_ZIP = "application/zip"
    }
}
