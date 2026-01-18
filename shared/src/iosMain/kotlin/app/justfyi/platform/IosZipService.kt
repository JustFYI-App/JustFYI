package app.justfyi.platform

import app.justfyi.domain.model.ExportData
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSTemporaryDirectory
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * iOS implementation of ZipService.
 * Uses Foundation for file path operations and posix for file writing.
 * Creates a valid ZIP archive with stored (uncompressed) entries.
 * Files are written to NSTemporaryDirectory.
 */
class IosZipService : ZipService {
    private val prettyJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun createZip(
        data: ExportData,
        fileName: String,
    ): FileResult =
        withContext(Dispatchers.IO) {
            val tempDir = NSTemporaryDirectory()
            val zipPath = "$tempDir$fileName"

            // Prepare JSON content for each file
            val entries =
                listOf(
                    ZipEntry("user.json", prettyJson.encodeToString(data.user)),
                    ZipEntry("interactions.json", prettyJson.encodeToString(data.interactions)),
                    ZipEntry("notifications.json", prettyJson.encodeToString(data.notifications)),
                    ZipEntry("reports.json", prettyJson.encodeToString(data.reports)),
                )

            // Create ZIP file
            createZipFile(zipPath, entries)

            FileResult(
                filePath = zipPath,
                mimeType = MIME_TYPE_ZIP,
            )
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun createZipFile(
        zipPath: String,
        entries: List<ZipEntry>,
    ) {
        val filePtr = fopen(zipPath, "wb") ?: throw RuntimeException("Failed to create ZIP file")

        try {
            val centralDirectory = mutableListOf<ByteArray>()
            var currentOffset = 0L

            // Write local file headers and data
            for (entry in entries) {
                val localHeaderOffset = currentOffset
                val contentBytes = entry.content.encodeToByteArray()
                val crc32Value = calculateCrc32(contentBytes)
                val localHeader = createLocalFileHeader(entry.name, contentBytes.size, crc32Value)

                writeToFile(filePtr, localHeader)
                writeToFile(filePtr, contentBytes)

                currentOffset += localHeader.size + contentBytes.size

                // Create central directory entry
                val centralEntry =
                    createCentralDirectoryEntry(
                        fileName = entry.name,
                        uncompressedSize = contentBytes.size,
                        crc32 = crc32Value,
                        localHeaderOffset = localHeaderOffset.toInt(),
                    )
                centralDirectory.add(centralEntry)
            }

            // Write central directory
            val centralDirStart = currentOffset
            for (centralEntry in centralDirectory) {
                writeToFile(filePtr, centralEntry)
                currentOffset += centralEntry.size
            }

            // Write end of central directory record
            val centralDirSize = (currentOffset - centralDirStart).toInt()
            val endRecord =
                createEndOfCentralDirectory(
                    numEntries = entries.size,
                    centralDirSize = centralDirSize,
                    centralDirOffset = centralDirStart.toInt(),
                )
            writeToFile(filePtr, endRecord)
        } finally {
            fclose(filePtr)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeToFile(
        filePtr: CPointer<FILE>,
        bytes: ByteArray,
    ) {
        if (bytes.isEmpty()) return
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u.convert(), bytes.size.convert(), filePtr)
        }
    }

    private fun createLocalFileHeader(
        fileName: String,
        uncompressedSize: Int,
        crc32: Long,
    ): ByteArray {
        val fileNameBytes = fileName.encodeToByteArray()

        return ByteArray(30 + fileNameBytes.size).apply {
            // Local file header signature (0x04034b50)
            this[0] = 0x50
            this[1] = 0x4b
            this[2] = 0x03
            this[3] = 0x04

            // Version needed to extract (1.0 for stored)
            this[4] = 10
            this[5] = 0

            // General purpose bit flag
            this[6] = 0
            this[7] = 0

            // Compression method (0 = stored, no compression)
            this[8] = 0
            this[9] = 0

            // Last mod file time (zero for simplicity)
            this[10] = 0
            this[11] = 0

            // Last mod file date (zero for simplicity)
            this[12] = 0
            this[13] = 0

            // CRC-32
            writeLittleEndian32(this, 14, crc32.toInt())

            // Compressed size (same as uncompressed for stored)
            writeLittleEndian32(this, 18, uncompressedSize)

            // Uncompressed size
            writeLittleEndian32(this, 22, uncompressedSize)

            // File name length
            writeLittleEndian16(this, 26, fileNameBytes.size)

            // Extra field length
            writeLittleEndian16(this, 28, 0)

            // File name
            fileNameBytes.copyInto(this, 30)
        }
    }

    private fun createCentralDirectoryEntry(
        fileName: String,
        uncompressedSize: Int,
        crc32: Long,
        localHeaderOffset: Int,
    ): ByteArray {
        val fileNameBytes = fileName.encodeToByteArray()

        return ByteArray(46 + fileNameBytes.size).apply {
            // Central file header signature (0x02014b50)
            this[0] = 0x50
            this[1] = 0x4b
            this[2] = 0x01
            this[3] = 0x02

            // Version made by (1.0, Unix)
            this[4] = 10
            this[5] = 3

            // Version needed to extract (1.0)
            this[6] = 10
            this[7] = 0

            // General purpose bit flag
            this[8] = 0
            this[9] = 0

            // Compression method (0 = stored)
            this[10] = 0
            this[11] = 0

            // Last mod file time
            this[12] = 0
            this[13] = 0

            // Last mod file date
            this[14] = 0
            this[15] = 0

            // CRC-32
            writeLittleEndian32(this, 16, crc32.toInt())

            // Compressed size (same as uncompressed for stored)
            writeLittleEndian32(this, 20, uncompressedSize)

            // Uncompressed size
            writeLittleEndian32(this, 24, uncompressedSize)

            // File name length
            writeLittleEndian16(this, 28, fileNameBytes.size)

            // Extra field length
            writeLittleEndian16(this, 30, 0)

            // File comment length
            writeLittleEndian16(this, 32, 0)

            // Disk number start
            writeLittleEndian16(this, 34, 0)

            // Internal file attributes
            writeLittleEndian16(this, 36, 0)

            // External file attributes
            writeLittleEndian32(this, 38, 0)

            // Relative offset of local header
            writeLittleEndian32(this, 42, localHeaderOffset)

            // File name
            fileNameBytes.copyInto(this, 46)
        }
    }

    private fun createEndOfCentralDirectory(
        numEntries: Int,
        centralDirSize: Int,
        centralDirOffset: Int,
    ): ByteArray =
        ByteArray(22).apply {
            // End of central directory signature (0x06054b50)
            this[0] = 0x50
            this[1] = 0x4b
            this[2] = 0x05
            this[3] = 0x06

            // Number of this disk
            writeLittleEndian16(this, 4, 0)

            // Disk where central directory starts
            writeLittleEndian16(this, 6, 0)

            // Number of central directory records on this disk
            writeLittleEndian16(this, 8, numEntries)

            // Total number of central directory records
            writeLittleEndian16(this, 10, numEntries)

            // Size of central directory
            writeLittleEndian32(this, 12, centralDirSize)

            // Offset of start of central directory
            writeLittleEndian32(this, 16, centralDirOffset)

            // Comment length
            writeLittleEndian16(this, 20, 0)
        }

    /**
     * Calculate CRC-32 checksum using the standard polynomial.
     * This is a pure Kotlin implementation of the CRC-32 algorithm.
     */
    private fun calculateCrc32(data: ByteArray): Long {
        if (data.isEmpty()) return 0L

        var crc = 0xFFFFFFFFL

        for (byte in data) {
            val b = byte.toLong() and 0xFF
            crc = crc xor b
            for (i in 0 until 8) {
                crc =
                    if ((crc and 1L) != 0L) {
                        (crc ushr 1) xor CRC32_POLYNOMIAL
                    } else {
                        crc ushr 1
                    }
            }
        }

        return crc xor 0xFFFFFFFFL
    }

    private fun writeLittleEndian16(
        array: ByteArray,
        offset: Int,
        value: Int,
    ) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeLittleEndian32(
        array: ByteArray,
        offset: Int,
        value: Int,
    ) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
        array[offset + 2] = ((value shr 16) and 0xFF).toByte()
        array[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private data class ZipEntry(
        val name: String,
        val content: String,
    )

    companion object {
        const val MIME_TYPE_ZIP = "application/zip"

        // CRC-32 polynomial (IEEE 802.3)
        private const val CRC32_POLYNOMIAL = 0xEDB88320L
    }
}
