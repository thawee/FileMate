package com.apincer.fileserver.sharing

import com.apincer.fileserver.http.NioHttpServer
import kotlinx.serialization.json.*
import java.io.File
import java.net.URLDecoder
import java.util.Base64

object OwnerRoutePolicy {
    private val publicPaths = setOf("/style.css", "/script.js", "/marked.min.js", "/api/auth")
    fun isPublic(path: String) = path in publicPaths
    fun authenticated(request: NioHttpServer.HttpRequest, query: String, pin: String): Boolean {
        val expected = "Basic " + Base64.getEncoder().encodeToString("admin:$pin".toByteArray())
        return request.getHeader("authorization", "") == expected ||
            request.getHeader("x-pin", "") == pin ||
            request.getHeader("cookie", "").split(';').any { it.trim() == "pin=$pin" } ||
            queryValue(query, "pin") == pin
    }
}

fun queryValue(query: String, name: String): String = query.split('&').firstOrNull { it.substringBefore('=') == name }
    ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

class ShareRoutes(
    private val store: ShareSessionStore,
    private val assets: (String) -> ByteArray,
    private val fileResponse: (File, NioHttpServer.HttpRequest) -> NioHttpServer.HttpResponse,
    private val onChange: () -> Unit = {},
    private val onUpload: (File) -> Unit = {}
) {
    companion object { const val MAX_UPLOAD_BYTES = 20 * 1024 * 1024 }

    fun guest(request: NioHttpServer.HttpRequest, path: String, query: String): NioHttpServer.HttpResponse {
        var downloadedFile: File? = null
        val response = try {
            val parts = path.removePrefix("/share/").split('/')
            if (parts.isEmpty() || parts[0].isEmpty()) throw ShareFailure(404, "Share not found")
            val token = parts[0]
            val session = store.lookup(token) ?: throw ShareFailure(410, "Share has expired or was revoked")
            val suffix = parts.drop(1).joinToString("/")
            when {
                request.method == "GET" && suffix.isEmpty() -> {
                    if (!path.endsWith('/')) NioHttpServer.HttpResponse().setStatus(302, "Found").addHeader("Location", "$path/").setBody(ByteArray(0))
                    else body(200, "text/html; charset=utf-8", assets("share.html"))
                }
                request.method == "GET" && suffix in setOf("share.css", "share.js") -> body(200, if (suffix.endsWith("css")) "text/css" else "application/javascript", assets(suffix))
                request.method == "GET" && suffix == "api/items" -> {
                    val items = if (session.mode.canDownload) {
                        val relative = queryValue(query, "path")
                        when (val scope = session.scope) {
                            is ShareScope.Entries -> {
                                if (relative.isNotEmpty()) throw ShareFailure(400, "Selected files have no subfolders")
                                scope.files.map { (name, _) -> name to store.resolve(session, name) }
                            }
                            is ShareScope.Tree -> {
                                val directory = store.resolve(session, relative)
                                if (!directory.isDirectory) throw ShareFailure(400, "Choose a folder")
                                directory.listFiles()?.filter { !it.name.startsWith(".sharemate-") }?.mapNotNull { file ->
                                    val childPath = listOf(relative, file.name).filter(String::isNotEmpty).joinToString("/")
                                    try { childPath to store.resolve(session, childPath) } catch (_: ShareFailure) { null }
                                } ?: throw ShareFailure(403, "Cannot read shared folder")
                            }
                        }
                    } else emptyList()
                    json(buildJsonObject {
                        put("label", session.label); put("mode", session.mode.name); put("expiresAtMillis", session.expiresAtMillis)
                        put("maxUploadBytes", MAX_UPLOAD_BYTES)
                        putJsonArray("items") {
                            items.sortedWith(compareBy<Pair<String, File>> { !it.second.isDirectory }.thenBy { it.second.name.lowercase() }).forEach { (relative, file) ->
                                add(buildJsonObject { put("name", file.name); put("path", relative); put("isDirectory", file.isDirectory); put("size", file.length()) })
                            }
                        }
                    })
                }
                request.method == "GET" && suffix == "download" -> {
                    if (!session.mode.canDownload) throw ShareFailure(403, "Downloads are not allowed")
                    val file = store.resolve(session, queryValue(query, "path"))
                    if (!file.isFile) throw ShareFailure(400, "Choose a file")
                    downloadedFile = file
                    fileResponse(file, request)
                }
                request.method == "POST" && suffix == "upload" -> {
                    if (request.getHeader("transfer-encoding", "").isNotEmpty()) throw ShareFailure(400, "Transfer-Encoding is unsupported")
                    val length = request.getHeader("content-length", "").toLongOrNull() ?: throw ShareFailure(411, "Content-Length required")
                    if (length < 0 || length > MAX_UPLOAD_BYTES) throw ShareFailure(413, "Upload limit is 20 MiB")
                    if (request.body.size.toLong() != length) throw ShareFailure(400, "Incomplete upload")
                    val file = store.upload(token, queryValue(query, "name"), request.body)
                    onUpload(file)
                    json(buildJsonObject { put("name", file.name) }, 201)
                }
                else -> throw ShareFailure(404, "Guest route not found")
            }
        } catch (e: ShareFailure) { error(e.status, e.message) }
          catch (e: IllegalArgumentException) { error(400, e.message ?: "Invalid request") }
          catch (_: Exception) { error(500, "Unable to complete share request") }
        secure(response)
        downloadedFile?.let { file ->
            val raster = file.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
            val filename = java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            response.addHeader("Content-Security-Policy", "sandbox; default-src 'none'; frame-ancestors 'none'")
                .addHeader("Content-Disposition", "${if (raster) "inline" else "attachment"}; filename*=UTF-8''$filename")
        }
        return response
    }

    fun owner(request: NioHttpServer.HttpRequest, path: String, root: File): NioHttpServer.HttpResponse {
        val response = try {
            when {
                path == "/api/shares" && request.method == "GET" -> json(buildJsonObject {
                    putJsonArray("shares") { store.list().forEach { add(metadata(it)) } }
                })
                path == "/api/shares" && request.method == "POST" -> {
                    val input = Json.parseToJsonElement(request.body.toString(Charsets.UTF_8)).jsonObject
                    fun resolveOwnerPath(value: String): File {
                        val candidate = File(root, value.removePrefix("/"))
                        require(candidate.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) { "Outside shared storage" }
                        return candidate
                    }
                    val folder = input["folder"]?.jsonPrimitive?.content?.let(::resolveOwnerPath)
                    val files = input["paths"]?.jsonArray?.map { resolveOwnerPath(it.jsonPrimitive.content) } ?: emptyList()
                    val mode = ShareMode.valueOf(input["mode"]?.jsonPrimitive?.content ?: "DOWNLOAD")
                    val minutes = input["lifetimeMinutes"]?.jsonPrimitive?.int ?: 60
                    val created = store.create(root, folder, files, mode, minutes)
                    onChange()
                    json(JsonObject(metadata(created.session) + ("urlPath" to JsonPrimitive(created.urlPath))), 201)
                }
                path.startsWith("/api/shares/") && request.method == "DELETE" && path.removePrefix("/api/shares/").let { it.isNotEmpty() && '/' !in it } -> {
                    if (!store.revoke(path.removePrefix("/api/shares/"))) throw ShareFailure(404, "Share not found")
                    onChange()
                    json(buildJsonObject { put("revoked", true) })
                }
                else -> throw ShareFailure(405, "Method not allowed")
            }
        } catch (e: ShareFailure) { error(e.status, e.message) }
          catch (e: IllegalArgumentException) { error(400, e.message ?: "Invalid request") }
          catch (_: Exception) { error(500, "Unable to update shares") }
        return secure(response)
    }

    private fun metadata(session: ShareSession) = buildJsonObject {
        put("id", session.id); put("label", session.label); put("mode", session.mode.name); put("expiresAtMillis", session.expiresAtMillis)
    }
    private fun json(value: JsonElement, status: Int = 200) = body(status, "application/json; charset=utf-8", value.toString().toByteArray())
    private fun error(status: Int, message: String) = json(buildJsonObject { put("error", message) }, status)
    private fun body(status: Int, type: String, bytes: ByteArray) = NioHttpServer.HttpResponse().setStatus(status, if (status < 400) "OK" else "Error").addHeader("Content-Type", type).setBody(bytes)
    private fun secure(response: NioHttpServer.HttpResponse) = response.addHeader("Cache-Control", "no-store").addHeader("Referrer-Policy", "no-referrer")
        .addHeader("X-Content-Type-Options", "nosniff").addHeader("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' blob:; object-src 'none'; frame-ancestors 'none'; base-uri 'none'")
}
