package com.buhat.tracker.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream

internal fun readBackupText(resolver: ContentResolver, uri: Uri): String =
    readTextFile(
        resolver = resolver,
        uri = uri,
        missingMessage = "Could not read the backup file.",
        tooLargeMessage = "This backup file is larger than 20 MB.",
    )

internal fun readStrongCsvText(resolver: ContentResolver, uri: Uri): String =
    readTextFile(
        resolver = resolver,
        uri = uri,
        missingMessage = "Could not read the CSV file.",
        tooLargeMessage = "This CSV file is larger than 20 MB.",
    )

private fun readTextFile(
    resolver: ContentResolver,
    uri: Uri,
    missingMessage: String,
    tooLargeMessage: String,
): String {
    val stream = resolver.openInputStream(uri) ?: throw BackupException(missingMessage)
    return stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) {
                throw BackupException(tooLargeMessage)
            }
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }
}
