package com.apincer.fileserver.sharing

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

enum class ShareMode(val canDownload: Boolean, val canUpload: Boolean) {
    DOWNLOAD(true, false), UPLOAD(false, true), DOWNLOAD_AND_UPLOAD(true, true)
}

sealed interface ShareScope {
    data class Tree(val directory: File) : ShareScope
    data class Entries(val files: Map<String, File>) : ShareScope
}

data class ShareSession(val id: String, val label: String, val scope: ShareScope, val mode: ShareMode, val expiresAtMillis: Long)
data class CreatedShare(val session: ShareSession, val urlPath: String)
class ShareFailure(val status: Int, override val message: String) : Exception(message)

class ShareSessionStore(private val clock: () -> Long = System::currentTimeMillis) {
    private val random = SecureRandom()
    private var acceptingCreates = true
    private val sessions = mutableMapOf<String, ShareSession>()

    @Synchronized
    fun create(root: File, folder: File?, files: List<File>, mode: ShareMode, lifetimeMinutes: Int): CreatedShare {
        check(acceptingCreates) { "Start the server to create a share" }
        require(lifetimeMinutes in listOf(15, 60, 1440)) { "Choose 15 minutes, 1 hour, or 24 hours" }
        val canonicalRoot = root.canonicalFile
        fun bounded(file: File): File {
            val canonical = file.canonicalFile
            require(canonical.toPath().startsWith(canonicalRoot.toPath())) { "Target is outside shared storage" }
            require(file.toPath().toAbsolutePath().normalize() == canonical.toPath()) { "Symbolic links are not supported" }
            require(canonical.exists()) { "Target does not exist" }
            return canonical
        }
        val scope = if (folder != null) {
            require(files.isEmpty()) { "Choose a folder or selected files" }
            val directory = bounded(folder)
            require(directory.isDirectory) { "Target must be a folder" }
            ShareScope.Tree(directory)
        } else {
            require(mode == ShareMode.DOWNLOAD) { "Selected files support downloads only" }
            require(files.isNotEmpty() && files.size <= 200) { "Choose between 1 and 200 files" }
            val selected = files.map { bounded(it).also { file -> require(file.isFile) { "Select files only; use Share folder for folders" } } }
            require(selected.map { it.name }.distinct().size == selected.size) { "Selected files must have different names" }
            ShareScope.Entries(java.util.Collections.unmodifiableMap(selected.associateBy { it.name }))
        }
        purge()
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val label = when (scope) {
            is ShareScope.Tree -> scope.directory.name.ifEmpty { "Shared folder" }
            is ShareScope.Entries -> "${scope.files.size} selected files"
        }
        val session = ShareSession(UUID.randomUUID().toString(), label, scope, mode, clock() + lifetimeMinutes * 60_000L)
        sessions[hash(token)] = session
        return CreatedShare(session, "/share/$token/")
    }

    @Synchronized fun lookup(token: String): ShareSession? {
        purge()
        return sessions[hash(token)]
    }
    @Synchronized fun list(): List<ShareSession> { purge(); return sessions.values.toList() }
    @Synchronized fun revoke(id: String): Boolean = sessions.entries.removeAll { it.value.id == id }
    @Synchronized fun clear() { acceptingCreates = false; sessions.clear() }
    @Synchronized fun reset() { sessions.clear(); acceptingCreates = true }
    private fun purge() { val now = clock(); sessions.entries.removeAll { it.value.expiresAtMillis <= now } }
    private fun hash(token: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    fun resolve(session: ShareSession, path: String): File {
        if (path.startsWith('/') || path.contains('\\') || path.split('/').any { it == ".." || it == "." } || path.contains('\u0000')) {
            throw ShareFailure(400, "Invalid share path")
        }
        val file = when (val scope = session.scope) {
            is ShareScope.Tree -> File(scope.directory, path)
            is ShareScope.Entries -> scope.files[path] ?: throw ShareFailure(404, "File is not shared")
        }
        val canonical = file.canonicalFile
        if (file.toPath().toAbsolutePath().normalize() != canonical.toPath()) throw ShareFailure(403, "Symbolic links are not shared")
        if (session.scope is ShareScope.Tree && !canonical.toPath().startsWith(session.scope.directory.toPath())) throw ShareFailure(403, "Outside shared folder")
        if (!canonical.exists()) throw ShareFailure(404, "Shared item is unavailable")
        return canonical
    }

    @Synchronized
    fun upload(token: String, name: String, body: ByteArray): File {
        val session = lookup(token) ?: throw ShareFailure(410, "Share has expired or was revoked")
        if (!session.mode.canUpload) throw ShareFailure(403, "Uploads are not allowed")
        val scope = session.scope as? ShareScope.Tree ?: throw ShareFailure(403, "Uploads need a folder")
        if (!safeBasename(name)) throw ShareFailure(400, "Invalid filename")
        val directory = resolve(session, "")
        if (!directory.isDirectory || directory != scope.directory) throw ShareFailure(403, "Upload folder is unavailable")
        return publishNewFile(directory, name) { it.write(body) }
    }
}

fun safeBasename(name: String): Boolean = name.isNotBlank() && name.length <= 240 && name != "." && name != ".." && !name.startsWith(".sharemate-") && name.none { it == '/' || it == '\\' || it.isISOControl() }

private val publicationLock = Any()

fun publishNewFile(directory: File, name: String, write: (java.io.OutputStream) -> Unit): File = synchronized(publicationLock) {
    require(safeBasename(name)) { "Invalid filename" }
    val destination = File(directory, name)
    if (Files.exists(destination.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw ShareFailure(409, "A file with this name already exists")
    val temporary = File.createTempFile(".sharemate-", ".part", directory)
    try {
        temporary.outputStream().use(write)
        Files.move(temporary.toPath(), destination.toPath())
        destination
    } catch (e: java.nio.file.FileAlreadyExistsException) {
        throw ShareFailure(409, "A file with this name already exists")
    } finally {
        temporary.delete()
    }
}
