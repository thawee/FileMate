package com.apincer.fileserver.sharing

import com.apincer.fileserver.FileServerService
import java.io.File
import java.net.URLEncoder

fun photoShareUrl(file: File, ipAddress: String): String? {
    if (!FileServerService.isRunning) return null
    val root = FileServerService.sharedRoot ?: return null
    return runCatching {
        val share = FileServerService.shares.create(root, null, listOf(file), ShareMode.DOWNLOAD, 60)
        FileServerService.sharesChanged()
        val name = URLEncoder.encode(file.name, "UTF-8")
        "http://$ipAddress:8080${share.urlPath}download?path=$name"
    }.getOrNull()
}
