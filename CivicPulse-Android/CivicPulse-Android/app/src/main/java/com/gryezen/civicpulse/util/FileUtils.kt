package com.gryezen.civicpulse.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * Multipart upload needs a real [File] (see ComplaintRepository.fileComplaint),
 * but the system file/photo picker only ever hands back a content:// [Uri].
 * This copies each picked Uri into the app's cache dir under its original
 * (or a generated) filename so Retrofit can read it as a request body.
 *
 * Call from a background dispatcher — this does blocking I/O.
 */
fun resolveUrisToCacheFiles(context: Context, uris: List<Uri>): List<File> {
    val cacheDir = File(context.cacheDir, "complaint_attachments").apply { mkdirs() }
    return uris.mapNotNull { uri ->
        runCatching {
            val name = queryDisplayName(context, uri) ?: "attachment_${UUID.randomUUID()}"
            val target = File(cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme != "content") return uri.lastPathSegment
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}
