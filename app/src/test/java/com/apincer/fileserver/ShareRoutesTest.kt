package com.apincer.fileserver

import com.apincer.fileserver.http.NioHttpServer
import com.apincer.fileserver.sharing.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors

class ShareRoutesTest {
    private lateinit var root: File
    private lateinit var folder: File
    private lateinit var server: NioHttpServer
    private lateinit var thread: Thread
    private lateinit var store: ShareSessionStore
    private var port = 0
    private val now = AtomicLong(1_000_000)

    @Before fun start() {
        root = Files.createTempDirectory("sharemate-routes").toFile().canonicalFile
        folder = File(root, "chosen").apply { mkdir() }
        File(folder, "photo.jpg").writeText("shared-image")
        File(root, "private.txt").writeText("owner-only")
        store = ShareSessionStore(now::get)
        port = ServerSocket(0).use { it.localPort }
        server = NioHttpServer(port).apply { setMaxThread(2); setMaxRequestSize(500 * 1024 * 1024) }
        val routes = ShareRoutes(store, { "guest-resource".toByteArray() }, { file, _ -> NioHttpServer.HttpResponse().setBody(file.readBytes()) })
        server.registerHttpHandler { request ->
            val target = NioHttpServer.splitRequestTarget(request.path)
            val path = target[0]
            val query = target[1]
            when {
                path == "/share" || path.startsWith("/share/") -> routes.guest(request, path, query)
                !OwnerRoutePolicy.isPublic(path) && !OwnerRoutePolicy.authenticated(request, query, "123456") -> NioHttpServer.HttpResponse().setStatus(401, "Unauthorized").setBody("denied".toByteArray())
                path == "/api/shares" || path.startsWith("/api/shares/") -> routes.owner(request, path, root)
                OwnerRoutePolicy.isPublic(path) -> NioHttpServer.HttpResponse().setBody("public".toByteArray())
                else -> NioHttpServer.HttpResponse().setStatus(404, "Not Found").setBody("missing".toByteArray())
            }
        }
        val ready = CountDownLatch(1)
        server.setOnReady { ready.countDown() }
        thread = Thread(server).apply { isDaemon = true; start() }
        assertTrue("Server must bind before issuing requests", ready.await(5, TimeUnit.SECONDS))
    }

    @After fun stop() { server.stop(); thread.join(5000); root.deleteRecursively() }

    private data class Response(val status: Int, val headers: String, val body: String)
    private fun request(path: String, method: String = "GET", body: String = "", headers: String = "", length: String? = body.toByteArray().size.toString()): Response {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val bytes = body.toByteArray()
            val head = "$method $path HTTP/1.1\r\nHost: localhost\r\n" + (length?.let { "Content-Length: $it\r\n" } ?: "") + headers + "\r\n"
            socket.getOutputStream().write(head.toByteArray() + bytes)
            socket.getOutputStream().flush()
            val input = socket.getInputStream().buffered()
            val collected = java.io.ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                check(value >= 0) { "Closed before response headers" }
                collected.write(value)
                val current = collected.toByteArray()
                if (current.size >= 4 && current.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
            }
            val responseHeaders = collected.toString("UTF-8")
            val responseLength = responseHeaders.lineSequence().first { it.startsWith("Content-Length:", ignoreCase = true) }.substringAfter(':').trim().toInt()
            val responseBody = input.readNBytes(responseLength).toString(Charsets.UTF_8)
            return Response(responseHeaders.substringAfter(' ').substringBefore(' ').toInt(), responseHeaders, responseBody)
        }
    }
    private fun create(mode: ShareMode = ShareMode.DOWNLOAD): String = store.create(root, folder, emptyList(), mode, 60).urlPath.trimEnd('/')

    @Test fun guestScopeAndOwnerRoutesStaySeparate() {
        val base = create()
        assertEquals(200, request("$base/").status)
        assertEquals(200, request("$base/share.js").status)
        val items = request("$base/api/items")
        assertEquals(200, items.status)
        assertTrue(items.body.contains("photo.jpg"))
        assertFalse(items.body.contains("private.txt"))
        assertTrue(items.headers.contains("Cache-Control: no-store"))
        assertEquals("shared-image", request("$base/download?path=photo.jpg").body)
        assertEquals(400, request("$base/download?path=..%2Fprivate.txt").status)
        assertEquals(400, request("$base/download?path=%2Fprivate.txt").status)
        assertEquals(404, request("$base/api/system").status)
        assertEquals(404, request("$base/api/auth/x/api/files").status)
        assertEquals(401, request("/api/files?token=${base.substringAfterLast('/')}").status)
        listOf("/api/auth/x/api/files", "/api/auth/x/api/delete", "/api/auth/x/api/download/private.txt", "/prefix/api/upload", "/prefix/script.js").forEach { assertEquals(it, 401, request(it).status) }
        assertEquals(401, request("/api/shares", headers = "Cookie: pin=1234567\r\n").status)
    }

    @Test fun selectedFilesCannotExposeTheirParentOrSiblings() {
        val base = store.create(root, null, listOf(File(folder, "photo.jpg")), ShareMode.DOWNLOAD, 15).urlPath.trimEnd('/')
        assertEquals("shared-image", request("$base/download?path=photo.jpg").body)
        assertEquals(404, request("$base/download?path=private.txt").status)
        assertEquals(400, request("$base/api/items?path=chosen").status)
        assertEquals(403, request("$base/upload?name=x.txt", "POST", "x").status)
    }

    @Test fun uploadOnlyDoesNotListReadOrOverwrite() {
        val base = create(ShareMode.UPLOAD)
        val listing = Json.parseToJsonElement(request("$base/api/items").body).jsonObject
        assertTrue(listing["items"]!!.jsonArray.isEmpty())
        assertEquals(403, request("$base/download?path=photo.jpg").status)
        assertEquals(201, request("$base/upload?name=new.txt", "POST", "new-content").status)
        assertEquals("new-content", File(folder, "new.txt").readText())
        assertEquals(409, request("$base/upload?name=new.txt", "POST", "replace").status)
        assertEquals("new-content", File(folder, "new.txt").readText())
        assertEquals(400, request("$base/upload?name=..%2Fx.txt", "POST", "x").status)
        assertFalse(File(root, "x.txt").exists())
        assertTrue(folder.listFiles()!!.none { it.name.startsWith(".sharemate-") })
    }

    @Test fun revokedExpiredAndStoppedSharesDenyNextRequest() {
        val created = store.create(root, folder, emptyList(), ShareMode.DOWNLOAD, 15)
        val base = created.urlPath.trimEnd('/')
        assertEquals(200, request("$base/api/items").status)
        assertTrue(store.revoke(created.session.id))
        assertEquals(410, request("$base/download?path=photo.jpg").status)
        val expired = create()
        now.addAndGet(60 * 60_000)
        assertEquals(410, request("$expired/api/items").status)
        val stopped = create()
        store.clear()
        assertEquals(410, request("$stopped/api/items").status)
    }

    @Test fun symlinkEscapeAndLaterReplacementAreRejected() {
        Files.createSymbolicLink(File(folder, "escape.txt").toPath(), File(root, "private.txt").toPath())
        val base = create()
        assertEquals(403, request("$base/download?path=escape.txt").status)
        assertFalse(request("$base/api/items").body.contains("escape.txt"))
        val selected = store.create(root, null, listOf(File(folder, "photo.jpg")), ShareMode.DOWNLOAD, 60).urlPath.trimEnd('/')
        File(folder, "photo.jpg").delete()
        Files.createSymbolicLink(File(folder, "photo.jpg").toPath(), File(root, "private.txt").toPath())
        assertEquals(403, request("$selected/download?path=photo.jpg").status)
    }

    @Test fun guestBodyLimitIsRejectedFromHeadersWithoutReadingBody() {
        val base = create(ShareMode.UPLOAD)
        assertEquals(413, request("$base/upload?name=large", "POST", length = (ShareRoutes.MAX_UPLOAD_BYTES + 1).toString()).status)
        assertEquals(400, request("$base/upload?name=x", "POST", length = "-1").status)
        assertEquals(400, request("$base/upload?name=x", "POST", length = "hello").status)
        assertEquals(411, request("$base/upload?name=x", "POST", length = null).status)
        assertEquals(400, request("$base/upload?name=x", "POST", headers = "Transfer-Encoding: chunked\r\n", length = null).status)
        assertEquals(413, request("http://localhost:$port$base/upload?name=large", "POST", length = (ShareRoutes.MAX_UPLOAD_BYTES + 1).toString()).status)
        assertFalse(File(folder, "large").exists())
    }

    @Test fun absoluteTargetQueryCannotBecomeAGuestPath() {
        val base = create(ShareMode.UPLOAD)
        val target = "http://localhost?x=$base/upload?name=forged.txt"
        assertEquals("/", NioHttpServer.splitRequestTarget(target)[0])
        assertEquals(401, request(target, "POST", "x").status)
        assertFalse(File(folder, "forged.txt").exists())
        assertEquals(413, request(target, "POST", length = (500L * 1024 * 1024 + 1).toString()).status)
    }

    @Test fun activeGuestDownloadsAreAttachmentsWithASandbox() {
        File(folder, "untrusted.html").writeText("<script>fetch('/api/delete')</script>")
        val base = create()
        val html = request("$base/download?path=untrusted.html")
        assertEquals(200, html.status)
        assertTrue(html.headers.contains("Content-Disposition: attachment;"))
        assertTrue(html.headers.contains("Content-Security-Policy: sandbox; default-src 'none'"))
        val image = request("$base/download?path=photo.jpg")
        assertTrue(image.headers.contains("Content-Disposition: inline;"))
        assertEquals("shared-image", image.body)
    }

    @Test fun concurrentUploadsPublishExactlyOneCompleteFile() {
        val base = create(ShareMode.UPLOAD)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val outcomes = listOf("first".repeat(1000), "second".repeat(1000)).map { value -> executor.submit<Response> { request("$base/upload?name=race.txt", "POST", value) } }.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(listOf(201, 409), outcomes.map { it.status }.sorted())
            assertTrue(File(folder, "race.txt").readText() in listOf("first".repeat(1000), "second".repeat(1000)))
            assertTrue(folder.listFiles()!!.none { it.name.startsWith(".sharemate-") })
        } finally { executor.shutdownNow() }
    }

    @Test fun ownerCreateListAndRevokeHaveActualEffects() {
        val created = request("/api/shares", "POST", """{"folder":"/chosen","mode":"DOWNLOAD_AND_UPLOAD","lifetimeMinutes":60}""", "X-Pin: 123456\r\n")
        assertEquals(201, created.status)
        val value = Json.parseToJsonElement(created.body).jsonObject
        val path = value["urlPath"]!!.jsonPrimitive.content.trimEnd('/')
        assertEquals(200, request("$path/api/items").status)
        val listed = request("/api/shares", headers = "X-Pin: 123456\r\n")
        assertTrue(listed.body.contains(value["id"]!!.jsonPrimitive.content))
        assertFalse(listed.body.contains(path.substringAfterLast('/')))
        assertEquals(200, request("/api/shares/${value["id"]!!.jsonPrimitive.content}", "DELETE", headers = "X-Pin: 123456\r\n").status)
        assertEquals(410, request("$path/api/items").status)
        assertEquals(400, request("/api/shares", "POST", """{"folder":"/../outside","mode":"DOWNLOAD","lifetimeMinutes":60}""", "X-Pin: 123456\r\n").status)
    }

    @Test fun publicationPreservesExistingAndCleansFailures() {
        try {
            publishNewFile(folder, "partial.txt") { output -> output.write("partial".toByteArray()); throw java.io.IOException("Source interrupted") }
            fail("Interrupted copy must fail")
        } catch (_: java.io.IOException) { }
        assertFalse(File(folder, "partial.txt").exists())
        assertTrue(folder.listFiles()!!.none { it.name.startsWith(".sharemate-") })
        try { publishNewFile(folder, "photo.jpg") { it.write("replace".toByteArray()) }; fail("Existing file must be kept") }
        catch (failure: ShareFailure) { assertEquals(409, failure.status) }
        assertEquals("shared-image", File(folder, "photo.jpg").readText())
    }
}
