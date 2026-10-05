package com.apincer.fileserver.sharing

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File

fun incomingContentUris(intent: Intent): List<Uri> {
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
    val streams = if (intent.action == Intent.ACTION_SEND) {
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        listOfNotNull(uri)
    } else {
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toList() ?: emptyList()
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
        }
    }
    return streams.filter { it.scheme == "content" }.distinct().take(200)
}

fun incomingBasename(displayName: String?, index: Int): String {
    val name = displayName.orEmpty().map { if (it == '/' || it == '\\' || it.isISOControl()) '_' else it }.joinToString("").trim().take(240)
    return if (safeBasename(name)) name else "received-${index + 1}"
}

data class IncomingResult(val saved: Int, val failures: List<String>)

fun receiveFiles(resolver: ContentResolver, uris: List<Uri>, destination: File): IncomingResult {
    require(destination.isDirectory && destination.canWrite()) { "Choose a writable folder" }
    val directory = destination.canonicalFile
    require(directory.toPath() == destination.toPath().toAbsolutePath().normalize()) { "Choose a folder without symbolic links" }
    var saved = 0
    val failures = mutableListOf<String>()
    uris.forEachIndexed { index, uri ->
        var name = "received-${index + 1}"
        try {
            require(uri.scheme == "content") { "Unsupported URI" }
            val displayName = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull()
            name = incomingBasename(displayName, index)
            resolver.openInputStream(uri)?.use { input -> publishNewFile(directory, name) { output -> input.copyTo(output) } }
                ?: throw IllegalStateException("Source cannot be opened")
            saved++
        } catch (e: Exception) { failures += "$name: ${e.message ?: "Copy failed"}" }
    }
    return IncomingResult(saved, failures)
}
