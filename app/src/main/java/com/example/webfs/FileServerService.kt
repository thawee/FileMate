package com.example.webfs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.example.webfs.http.NioHttpServer
import java.io.File

class FileServerService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var sharedRoot: File? = null
            private set

        private const val SERVER_PORT = 8080
        private const val PROXY_PORT = 8081
    }

    private var nioServer: NioHttpServer? = null
    private var proxyServer: HttpProxyServer? = null

    private var cachedHtml: ByteArray = ByteArray(0)
    private var cachedCss: ByteArray = ByteArray(0)
    private var cachedJs: ByteArray = ByteArray(0)
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            isRunning = false
            sharedRoot = null
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        AuthHelper.generateNewPin()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "WebFS:ServerWakeLock")
        wakeLock?.acquire()
        val uriString = intent?.getStringExtra("FOLDER_URI")
        if (uriString != null) {
            sharedRoot = Uri.parse(uriString).path?.let { File(it) }
        }

        TrafficMonitor.reset()
        TrafficMonitor.start()
        startForegroundServiceNotification()
        loadAssets()
        startServer()

        return START_STICKY
    }

    private fun loadAssets() {
        try {
            cachedHtml = assets.open("index.html").readBytes()
            cachedCss = assets.open("style.css").readBytes()
            cachedJs = assets.open("script.js").readBytes()
        } catch (e: Exception) {
            Log.e("FileServerService", "Failed to load assets", e)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "webfs_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Shared Server",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, FileServerService::class.java).apply { action = "STOP" }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingOpenAppIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shared Server Running")
            .setContentText("Serving files on local network")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingOpenAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun startServer() {
        if (nioServer != null) return
        try {
            val server = NioHttpServer(SERVER_PORT)
            server.setMaxThread(4)
            server.setKeepAliveTimeout(30_000)
            server.setMaxConnections(50)
            server.setTcpNoDelay(true)
            server.setMaxRequestSize(500 * 1024 * 1024) // 500MB upload limit

            server.registerHttpHandler(NioHttpServer.Handler { request ->
                handleRoute(request)
            })

            nioServer = server
            Thread(server, "NIO-Reactor").start()
            Log.i("FileServerService", "NIO server started on port $SERVER_PORT")

            proxyServer = HttpProxyServer(PROXY_PORT)
            proxyServer?.start()
            Log.i("FileServerService", "HTTP proxy started on port $PROXY_PORT")
        } catch (e: Exception) {
            Log.e("FileServerService", "Failed to start server", e)
            isRunning = false
            stopSelf()
        }
    }

    private fun handleRoute(request: NioHttpServer.HttpRequest): NioHttpServer.HttpResponse {
        val rawPath = request.path ?: "/"
        val queryIdx = rawPath.indexOf('?')
        val path = if (queryIdx >= 0) rawPath.substring(0, queryIdx) else rawPath
        val query = if (queryIdx >= 0) rawPath.substring(queryIdx + 1) else ""

        val isPublicAsset = path == "/" || path == "/style.css" || path == "/script.js" || path.startsWith("/api/auth")
        if (!isPublicAsset) {
            val authHeader = request.getHeader("authorization", "")
            val cookie = request.getHeader("cookie", "")
            val expectedAuth = "Basic " + android.util.Base64.encodeToString("admin:${AuthHelper.currentPin}".toByteArray(), android.util.Base64.NO_WRAP)
            
            var isAuthenticated = authHeader == expectedAuth
            if (!isAuthenticated && cookie.contains("pin=${AuthHelper.currentPin}")) {
                isAuthenticated = true
            }
            
            if (!isAuthenticated) {
                val pinQuery = parseQueryParam(query, "pin")
                if (pinQuery == AuthHelper.currentPin) isAuthenticated = true
            }

            if (!isAuthenticated) {
                return NioHttpServer.HttpResponse()
                    .setStatus(401, "Unauthorized")
                    .addHeader("WWW-Authenticate", "Basic realm=\"FileServer\"")
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"error\":\"Unauthorized\"}".toByteArray())
            }
        }

        val response = when (path) {
            "/" -> htmlResponse(cachedHtml)
            "/style.css" -> cssResponse(cachedCss)
            "/script.js" -> jsResponse(cachedJs)
            "/api/auth" -> authResponse(query)
            "/api/health" -> jsonResponse("{\"status\":\"ok\"}")
            "/api/system" -> jsonResponse(buildSystemJson())
            "/api/files" -> filesResponse(query)
            "/api/upload" -> uploadResponse(request, query)
            "/api/delete" -> deleteResponse(request, query)
            "/api/move" -> moveResponse(query)
            "/api/rename" -> renameResponse(query)
            "/api/mkdir" -> mkdirResponse(query)
            "/api/clean-empty-folders" -> cleanEmptyFoldersResponse(query)
            "/api/clean-junk-files" -> cleanJunkFilesResponse(query)
            "/api/storage-stats" -> storageStatsResponse(query)
            "/api/thumbnail" -> thumbnailResponse(query)
            "/api/download-zip" -> downloadZipResponse(query)
            "/api/unzip" -> unzipResponse(request, query)
            "/api/search" -> searchResponse(query)
            else -> {
                if (path.startsWith("/api/download/")) {
                    downloadResponse(request, path, query)
                } else {
                    notFound()
                }
            }
        }

        val contentType = response.getHeader("Content-Type") ?: ""
        val acceptEncoding = request.getHeader("accept-encoding", "")
        if (acceptEncoding.contains("gzip") && 
            (contentType.contains("text/") || contentType.contains("application/json") || contentType.contains("application/javascript"))) {
            
            val body = response.body
            if (body.isNotEmpty()) {
                val bos = java.io.ByteArrayOutputStream()
                java.util.zip.GZIPOutputStream(bos).use { it.write(body) }
                val gzippedBody = bos.toByteArray()
                response.setBody(gzippedBody)
                response.addHeader("Content-Encoding", "gzip")
            }
        }
        
        return response
    }

    private fun htmlResponse(body: ByteArray) = NioHttpServer.HttpResponse()
        .setStatus(200, "OK")
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .addHeader("Content-Length", body.size.toString())
        .setBody(body)

    private fun cssResponse(body: ByteArray) = NioHttpServer.HttpResponse()
        .setStatus(200, "OK")
        .addHeader("Content-Type", "text/css; charset=utf-8")
        .addHeader("Content-Length", body.size.toString())
        .setBody(body)

    private fun jsResponse(body: ByteArray) = NioHttpServer.HttpResponse()
        .setStatus(200, "OK")
        .addHeader("Content-Type", "application/javascript; charset=utf-8")
        .addHeader("Content-Length", body.size.toString())
        .setBody(body)

    private fun jsonResponse(json: String) = NioHttpServer.HttpResponse()
        .setStatus(200, "OK")
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .addHeader("Content-Length", json.toByteArray().size.toString())
        .setBody(json.toByteArray())

    private fun notFound() = NioHttpServer.HttpResponse()
        .setStatus(404, "Not Found")
        .addHeader("Content-Type", "text/plain")
        .setBody("Not found".toByteArray())

    private fun buildSystemJson(): String {
        val model = org.json.JSONObject.quote(android.os.Build.MODEL)
        val os = org.json.JSONObject.quote(android.os.Build.VERSION.RELEASE)
        val api = android.os.Build.VERSION.SDK_INT
        return "{\"model\":$model,\"osVersion\":$os,\"apiLevel\":$api,\"proxyPort\":$PROXY_PORT}"
    }

    private fun filesResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot
        if (root == null || !root.exists()) {
            return errorResponse(400, "No shared folder selected")
        }

        val path = parseQueryParam(query, "path")
        val folder = resolveFile(root, path)
        if (folder == null || !folder.canRead() || !folder.isDirectory) {
            return errorResponse(404, "Folder not found")
        }

        val entries = folder.listFiles() ?: emptyArray()
        val sb = StringBuilder("[")
        var first = true
        entries.asSequence()
            .filter { !it.name.startsWith(".") && it.name != "Android" }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
            .forEach { file ->
                if (!first) sb.append(",")
                first = false
                val escaped = org.json.JSONObject.quote(file.name)
                val size = if (file.isFile) file.length() else 0L
                val lastModified = file.lastModified()
                sb.append("{\"name\":$escaped,\"size\":$size,\"isDirectory\":${file.isDirectory},\"lastModified\":$lastModified}")
            }
        sb.append("]")

        val jsonStr = sb.toString()
        return NioHttpServer.HttpResponse()
            .setStatus(200, "OK")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Content-Length", jsonStr.toByteArray().size.toString())
            .setBody(jsonStr.toByteArray())
    }

    private fun uploadResponse(request: NioHttpServer.HttpRequest, query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder selected")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Target folder not found")
        if (!folder.isDirectory || !folder.canWrite()) return errorResponse(403, "Cannot write to folder")

        val contentType = request.getHeader("content-type", "")
        val body = request.body ?: ByteArray(0)
        if (body.isEmpty()) return errorResponse(400, "Empty upload body")

        var filename = parseQueryParam(query, "filename")
        var fileData: ByteArray? = null

        if (contentType.contains("multipart/form-data")) {
            val boundary = contentType.substringAfter("boundary=", "").trim()
            if (boundary.isNotEmpty()) {
                val parsed = parseMultipart(body, boundary)
                if (parsed != null) {
                    filename = parsed.first
                    fileData = parsed.second
                }
            }
        }

        if (fileData == null) {
            if (filename.isEmpty()) {
                filename = request.getHeader("x-file-name", "upload_${System.currentTimeMillis()}")
            }
            fileData = body
        }

        if (filename.isEmpty()) return errorResponse(400, "Missing filename")

        val safeName = File(filename.replace('\\', '/')).name
        if (safeName.isEmpty() || safeName == "." || safeName == "..") {
            return errorResponse(400, "Invalid filename")
        }

        val targetFile = File(folder, safeName)
        if (targetFile.parentFile != folder) return errorResponse(403, "Invalid filename path")

        return try {
            targetFile.writeBytes(fileData)
            scanMedia(targetFile.absolutePath)
            jsonResponse("{\"status\":\"ok\",\"filename\":${org.json.JSONObject.quote(safeName)},\"size\":${fileData.size}}")
        } catch (e: Exception) {
            errorResponse(500, "Failed to save file: ${e.message}")
        }
    }

    private fun parseMultipart(body: ByteArray, boundary: String): Pair<String, ByteArray>? {
        try {
            val boundaryBytes = ("--" + boundary).toByteArray(Charsets.UTF_8)
            val headerEndBytes = "\r\n\r\n".toByteArray(Charsets.UTF_8)

            val boundaryPos = indexOfBytes(body, boundaryBytes, 0)
            if (boundaryPos == -1) return null

            val headersStart = boundaryPos + boundaryBytes.size + 2
            val headerEndPos = indexOfBytes(body, headerEndBytes, headersStart)
            if (headerEndPos == -1) return null

            val headerText = String(body, headersStart, headerEndPos - headersStart, Charsets.UTF_8)

            var filename = ""
            val filenameMatch = Regex("filename=\"([^\"]+)\"").find(headerText)
                ?: Regex("filename=([^;\\s]+)").find(headerText)
            if (filenameMatch != null) {
                filename = filenameMatch.groupValues[1]
            }
            if (filename.isEmpty()) return null

            val dataStart = headerEndPos + headerEndBytes.size
            val nextBoundaryPos = indexOfBytes(body, boundaryBytes, dataStart)
            if (nextBoundaryPos == -1) return null

            var dataEnd = nextBoundaryPos
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r'.code.toByte() && body[dataEnd - 1] == '\n'.code.toByte()) {
                dataEnd -= 2
            }

            if (dataEnd < dataStart) return null

            val data = body.copyOfRange(dataStart, dataEnd)
            return Pair(filename, data)
        } catch (e: Exception) {
            Log.e("FileServerService", "Error parsing multipart", e)
            return null
        }
    }

    private fun indexOfBytes(outer: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (fromIndex < 0 || fromIndex >= outer.size || target.isEmpty()) return -1
        outerLoop@ for (i in fromIndex..(outer.size - target.size)) {
            for (j in target.indices) {
                if (outer[i + j] != target[j]) continue@outerLoop
            }
            return i
        }
        return -1
    }

    private fun downloadResponse(request: NioHttpServer.HttpRequest, path: String, query: String): NioHttpServer.HttpResponse {
        val rawFilename = path.removePrefix("/api/download/")
        if (rawFilename.isEmpty()) return errorResponse(400, "Missing filename")
        val filename = try {
            java.net.URLDecoder.decode(rawFilename, "UTF-8")
        } catch (e: Exception) {
            rawFilename
        }

        if (filename.contains("/") || filename.contains("\\") || filename == ".." || filename == ".") {
            return errorResponse(400, "Invalid filename")
        }

        val root = sharedRoot ?: return errorResponse(400, "No shared folder")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Folder not found")
        val file = File(folder, filename)

        val rootPath = root.toPath().toAbsolutePath().normalize()
        val filePath = file.toPath().toAbsolutePath().normalize()
        if (!filePath.startsWith(rootPath) || file.parentFile != folder) return errorResponse(403, "Access denied")

        if (!file.exists() || !file.isFile) return errorResponse(404, "File not found")

        return try {
            nioServer?.createFileResponse(file, request)
                ?: errorResponse(500, "Server not running")
        } catch (e: Exception) {
            errorResponse(404, "File not found")
        }
    }

    private fun deleteResponse(request: NioHttpServer.HttpRequest, query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder")

        var namesStr = parseQueryParam(query, "names").ifEmpty { parseQueryParam(query, "name") }
        var subPath = parseQueryParam(query, "path")

        if (namesStr.isEmpty() && request.body.isNotEmpty()) {
            val bodyStr = String(request.body, Charsets.UTF_8).trim()
            if (bodyStr.startsWith("{")) {
                try {
                    val jsonObj = org.json.JSONObject(bodyStr)
                    if (jsonObj.has("names")) {
                        val arr = jsonObj.optJSONArray("names")
                        if (arr != null) {
                            val list = mutableListOf<String>()
                            for (i in 0 until arr.length()) list.add(arr.getString(i))
                            namesStr = list.joinToString(",")
                        } else {
                            namesStr = jsonObj.optString("names", "")
                        }
                    }
                    if (namesStr.isEmpty() && jsonObj.has("name")) {
                        namesStr = jsonObj.optString("name", "")
                    }
                    if (subPath.isEmpty() && jsonObj.has("path")) {
                        subPath = jsonObj.optString("path", "")
                    }
                } catch (e: Exception) {
                    // Ignore JSON parse exception
                }
            } else if (bodyStr.contains("=")) {
                if (namesStr.isEmpty()) namesStr = parseQueryParam(bodyStr, "names").ifEmpty { parseQueryParam(bodyStr, "name") }
                if (subPath.isEmpty()) subPath = parseQueryParam(bodyStr, "path")
            }
        }

        if (namesStr.isEmpty()) return errorResponse(400, "Missing filename")

        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Folder not found")

        val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var deletedCount = 0
        var errors = 0

        for (filename in names) {
            if (filename.contains("/") || filename.contains("\\") || filename == ".." || filename == ".") {
                errors++
                continue
            }
            val file = File(folder, filename)
            if (file.exists() && file.parentFile == folder) {
                val pathStr = file.absolutePath
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (deleted) {
                    deletedCount++
                    scanMedia(pathStr)
                } else {
                    errors++
                }
            } else {
                errors++
            }
        }

        return jsonResponse("{\"success\":true,\"status\":\"ok\",\"deleted\":$deletedCount,\"errors\":$errors}")
    }

    private fun moveResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder selected")
        val namesStr = parseQueryParam(query, "names").ifEmpty { parseQueryParam(query, "name") }
        if (namesStr.isEmpty()) return errorResponse(400, "Missing filename")

        val fromPath = parseQueryParam(query, "fromPath")
        val targetPath = parseQueryParam(query, "targetPath")

        val srcFolder = resolveFile(root, fromPath) ?: return errorResponse(404, "Source folder not found")
        val destFolder = resolveFile(root, targetPath) ?: return errorResponse(404, "Target folder not found")
        if (!destFolder.isDirectory) return errorResponse(404, "Target path is not a folder")

        val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var movedCount = 0
        var errors = 0

        val destPath = destFolder.toPath().toAbsolutePath().normalize()

        for (filename in names) {
            if (filename.contains("/") || filename.contains("\\") || filename == ".." || filename == ".") {
                errors++
                continue
            }
            val srcFile = File(srcFolder, filename)
            if (!srcFile.exists() || srcFile.parentFile != srcFolder) {
                errors++
                continue
            }

            val srcPath = srcFile.toPath().toAbsolutePath().normalize()

            if (srcFile.isDirectory && destPath.startsWith(srcPath)) {
                errors++
                continue
            }

            val destFile = File(destFolder, filename)
            if (!destFile.exists()) {
                val moved = moveFileOrDir(srcFile, destFile)
                if (moved) {
                    movedCount++
                    scanMedia(srcFile.absolutePath, destFile.absolutePath)
                } else {
                    errors++
                }
            } else {
                errors++
            }
        }

        return jsonResponse("{\"status\":\"ok\",\"moved\":$movedCount,\"errors\":$errors}")
    }

    private fun moveFileOrDir(src: File, dest: File): Boolean {
        val srcPath = src.toPath().toAbsolutePath().normalize()
        val destPath = dest.toPath().toAbsolutePath().normalize()

        try {
            java.nio.file.Files.move(
                srcPath,
                destPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            return true
        } catch (_: Exception) {}

        if (src.renameTo(dest)) return true

        return try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true) && src.deleteRecursively()
            } else {
                src.copyTo(dest, overwrite = true).exists() && src.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun scanMedia(vararg paths: String) {
        try {
            android.media.MediaScannerConnection.scanFile(applicationContext, paths, null, null)
        } catch (_: Exception) {}
    }

    private fun mkdirResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder selected")
        val folderName = parseQueryParam(query, "name").trim()
        if (folderName.isEmpty()) return errorResponse(400, "Missing folder name")

        if (folderName.contains("/") || folderName.contains("\\") || folderName == "." || folderName == "..") {
            return errorResponse(400, "Invalid folder name")
        }

        val subPath = parseQueryParam(query, "path")
        val parentFolder = resolveFile(root, subPath)

        if (parentFolder == null || !parentFolder.exists() || !parentFolder.isDirectory) {
            return errorResponse(404, "Parent folder not found")
        }

        val newFolder = File(parentFolder, folderName)
        if (newFolder.exists()) {
            return errorResponse(409, "A folder or file with this name already exists")
        }

        val created = newFolder.mkdir()
        return if (created) {
            jsonResponse("{\"status\":\"ok\"}")
        } else {
            errorResponse(500, "Failed to create folder")
        }
    }

    private fun cleanEmptyFoldersResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder selected")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Target folder not found")

        val res = StorageMaintenanceHelper.cleanEmptyFolders(folder, applicationContext)
        val escapedList = res.removedFolders.map { org.json.JSONObject.quote(it) }.joinToString(",")
        val json = "{\"status\":\"ok\",\"removedCount\":${res.removedCount},\"removedFolders\":[$escapedList]}"
        return jsonResponse(json)
    }

    private fun cleanJunkFilesResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder selected")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Target folder not found")

        val res = StorageMaintenanceHelper.cleanJunkFiles(folder, applicationContext)
        val json = "{\"status\":\"ok\",\"removedCount\":${res.removedCount},\"freedBytes\":${res.freedBytes}}"
        return jsonResponse(json)
    }

    private fun storageStatsResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder selected")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Target folder not found")

        val stats = StorageMaintenanceHelper.computeStorageStats(folder)
        val top10Sb = StringBuilder("[")
        stats.topFiles.forEachIndexed { idx, entry ->
            if (idx > 0) top10Sb.append(",")
            top10Sb.append("{\"name\":${org.json.JSONObject.quote(entry.name)},\"path\":${org.json.JSONObject.quote(entry.relPath)},\"size\":${entry.size},\"lastModified\":${entry.lastModified}}")
        }
        top10Sb.append("]")

        val json = """
            {
                "status": "ok",
                "totalSize": ${stats.totalSize},
                "totalFiles": ${stats.totalFiles},
                "totalFolders": ${stats.totalFolders},
                "emptyFoldersCount": ${stats.emptyFoldersCount},
                "junkFilesCount": ${stats.junkFilesCount},
                "categories": {
                    "images": {"size": ${stats.images.size}, "count": ${stats.images.count}},
                    "videos": {"size": ${stats.videos.size}, "count": ${stats.videos.count}},
                    "audio": {"size": ${stats.audio.size}, "count": ${stats.audio.count}},
                    "docs": {"size": ${stats.docs.size}, "count": ${stats.docs.count}},
                    "archives": {"size": ${stats.archives.size}, "count": ${stats.archives.count}},
                    "other": {"size": ${stats.other.size}, "count": ${stats.other.count}}
                },
                "topFiles": $top10Sb
            }
        """.trimIndent()

        return jsonResponse(json)
    }

    private fun authResponse(query: String): NioHttpServer.HttpResponse {
        val pin = parseQueryParam(query, "pin")
        if (AuthHelper.verifyPin(pin)) {
            val response = jsonResponse("{\"status\":\"ok\"}")
            response.addHeader("Set-Cookie", "pin=$pin; Path=/; HttpOnly")
            return response
        }
        return errorResponse(401, "Invalid PIN")
    }

    private fun thumbnailResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder")
        val subPath = parseQueryParam(query, "path")
        val file = resolveFile(root, subPath) ?: return errorResponse(404, "File not found")
        
        val bytes = ThumbnailHelper.generateThumbnail(file)
        if (bytes != null) {
            val res = NioHttpServer.HttpResponse()
                .setStatus(200, "OK")
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("Content-Length", bytes.size.toString())
                .addHeader("Cache-Control", "public, max-age=86400")
                .setBody(bytes)
            return res
        }
        return errorResponse(404, "No thumbnail available")
    }

    private fun downloadZipResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Folder not found")
        
        val namesStr = parseQueryParam(query, "names").ifEmpty { parseQueryParam(query, "name") }
        if (namesStr.isEmpty()) return errorResponse(400, "Missing filenames")
        
        val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val filesToZip = names.mapNotNull {
            if (it.contains("/") || it.contains("\\") || it == ".." || it == ".") null
            else File(folder, it).takeIf { f -> f.exists() && f.parentFile == folder }
        }
        
        if (filesToZip.isEmpty()) return errorResponse(404, "No valid files found")
        
        try {
            val tempZip = File.createTempFile("download", ".zip", applicationContext.cacheDir)
            val fos = java.io.FileOutputStream(tempZip)
            ZipHelper.zipFiles(filesToZip, fos)
            fos.close()
            
            val res = nioServer?.createFileResponse(tempZip, NioHttpServer.HttpRequest())
                ?: return errorResponse(500, "Server error")
            res.addHeader("Content-Disposition", "attachment; filename=\"archive.zip\"")
            return res
        } catch (e: Exception) {
            return errorResponse(500, "Zip error: ${e.message}")
        }
    }

    private fun unzipResponse(request: NioHttpServer.HttpRequest, query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Folder not found")
        
        val namesStr = parseQueryParam(query, "names").ifEmpty { parseQueryParam(query, "name") }
        if (namesStr.isEmpty()) return errorResponse(400, "Missing filename")
        
        val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var successCount = 0
        
        for (name in names) {
            val zipFile = File(folder, name)
            if (zipFile.exists() && zipFile.isFile && zipFile.extension.lowercase() == "zip") {
                val destDir = File(folder, zipFile.nameWithoutExtension)
                java.io.FileInputStream(zipFile).use { fis ->
                    if (ZipHelper.unzipFile(fis, destDir)) {
                        successCount++
                        scanMedia(destDir.absolutePath)
                    }
                }
            }
        }
        return jsonResponse("{\"status\":\"ok\",\"unzipped\":$successCount}")
    }

    private fun renameResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Folder not found")
        
        val oldName = parseQueryParam(query, "oldName")
        val newName = parseQueryParam(query, "newName")
        
        if (oldName.isEmpty() || newName.isEmpty()) return errorResponse(400, "Missing names")
        if (oldName.contains("/") || newName.contains("/")) return errorResponse(400, "Invalid names")
        
        val oldFile = File(folder, oldName)
        val newFile = File(folder, newName)
        
        if (!oldFile.exists()) return errorResponse(404, "File not found")
        if (newFile.exists()) return errorResponse(409, "Target name already exists")
        
        if (oldFile.renameTo(newFile)) {
            scanMedia(newFile.absolutePath)
            return jsonResponse("{\"status\":\"ok\"}")
        }
        return errorResponse(500, "Failed to rename")
    }

    private fun searchResponse(query: String): NioHttpServer.HttpResponse {
        val root = sharedRoot ?: return errorResponse(400, "No shared folder")
        val subPath = parseQueryParam(query, "path")
        val folder = resolveFile(root, subPath) ?: return errorResponse(404, "Folder not found")
        
        val q = parseQueryParam(query, "q").lowercase()
        if (q.isEmpty()) return errorResponse(400, "Missing query")
        
        val results = mutableListOf<String>()
        val maxResults = 100
        
        folder.walkTopDown().forEach { file ->
            if (results.size >= maxResults) return@forEach
            if (file.name.lowercase().contains(q)) {
                val relPath = file.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                val escaped = org.json.JSONObject.quote(relPath)
                val size = if (file.isFile) file.length() else 0L
                val isDir = file.isDirectory
                results.add("{\"name\":$escaped,\"size\":$size,\"isDirectory\":$isDir}")
            }
        }
        
        val jsonStr = "[${results.joinToString(",")}]"
        return jsonResponse("{\"status\":\"ok\",\"results\":$jsonStr}")
    }

    private fun errorResponse(code: Int, message: String): NioHttpServer.HttpResponse {
        val json = "{\"success\":false,\"status\":\"error\",\"error\":${org.json.JSONObject.quote(message)}}"
        val body = json.toByteArray(Charsets.UTF_8)
        return NioHttpServer.HttpResponse()
            .setStatus(code, message)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Content-Length", body.size.toString())
            .setBody(body)
    }

    private fun parseQueryParam(query: String, key: String): String {
        if (query.isEmpty()) return ""
        return query.split("&")
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
    }

    private fun resolveFile(root: File, path: String?): File? {
        try {
            val rootPath = root.toPath().toAbsolutePath().normalize()
            if (path.isNullOrEmpty() || path == "/") return root
            var current = root
            val segments = path.trim('/').split('/')
            for (segment in segments) {
                if (segment.isEmpty() || segment == ".") continue
                val next = File(current, segment)
                val nextPath = next.toPath().toAbsolutePath().normalize()
                if (!nextPath.startsWith(rootPath)) return null
                if (!next.exists()) return null
                current = next
            }
            val finalPath = current.toPath().toAbsolutePath().normalize()
            if (!finalPath.startsWith(rootPath)) return null
            return current
        } catch (e: Exception) {
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        nioServer?.stop()
        nioServer = null
        proxyServer?.stop()
        proxyServer = null
        TrafficMonitor.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
