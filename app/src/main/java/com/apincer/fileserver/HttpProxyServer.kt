package com.apincer.fileserver

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.Base64

class HttpProxyServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private var proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS = 30_000
    }

    fun start() {
        serverSocket = ServerSocket(port)
        isRunning = true
        Log.i("HttpProxyServer", "Proxy server started on port $port")
        proxyScope.launch {
            while (isRunning) {
                try {
                    val clientSocket = withContext(Dispatchers.IO) { serverSocket?.accept() } ?: break
                    launch { handleClient(clientSocket) }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        proxyScope.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        var remoteSocket: Socket? = null
        try {
            clientSocket.soTimeout = SO_TIMEOUT_MS

            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerBuilder = StringBuilder()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            var headersEndIndex = -1
            while (headersEndIndex == -1) {
                bytesRead = clientIn.read(buffer)
                if (bytesRead == -1) return@withContext
                headerBuilder.append(String(buffer, 0, bytesRead))
                headersEndIndex = headerBuilder.indexOf("\r\n\r\n")
                if (headerBuilder.length > 65536) return@withContext
            }

            val request = headerBuilder.toString()
            val firstLineEnd = request.indexOf("\r\n")
            if (firstLineEnd == -1) return@withContext
            
            val requestLine = request.substring(0, firstLineEnd)
            val parts = requestLine.split(" ")
            if (parts.size < 3) return@withContext

            val method = parts[0]
            val url = parts[1]

            val clientIp = clientSocket.inetAddress?.hostAddress ?: ""
            // The proxy shares the owner PIN with the web UI, so brute force here must hit the
            // same lockout. Refuse early when the IP is already locked out.
            if (AuthThrottle.retryAfterMs(clientIp) > 0) {
                clientOut.write("HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
                return@withContext
            }

            val headers = parseHeaders(request, firstLineEnd, headersEndIndex)
            if (!isProxyAuthorized(headers)) {
                // Only a wrong credential counts as a brute-force attempt. The credential-less
                // first request (the normal 407 challenge handshake, which browsers/curl always
                // send, and which credential-incapable clients retry) must not self-lock the IP.
                if (headers.containsKey("proxy-authorization")) AuthThrottle.recordFailure(clientIp)
                clientOut.write(("HTTP/1.1 407 Proxy Authentication Required\r\n" +
                        "Proxy-Authenticate: Basic realm=\"ShareMate\"\r\n" +
                        "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                clientOut.flush()
                return@withContext
            }
            AuthThrottle.recordSuccess(clientIp)

            if (method.equals("CONNECT", ignoreCase = true)) {
                val hostPort = url.split(":")
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toInt() else 443

                val addr = resolveIPv4(host) ?: throw UnknownHostException("Cannot resolve: $host")
                if (isBlockedDestination(addr)) {
                    clientOut.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    clientOut.flush()
                    return@withContext
                }
                remoteSocket = Socket()
                remoteSocket.connect(InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS)
                remoteSocket.soTimeout = SO_TIMEOUT_MS
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOut.flush()

                relay(clientSocket, remoteSocket)
            } else {
                val uri = URI(url)
                val host = uri.host ?: return@withContext
                val port = if (uri.port != -1) uri.port else 80

                val addr = resolveIPv4(host) ?: throw UnknownHostException("Cannot resolve: $host")
                if (isBlockedDestination(addr)) {
                    clientOut.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    clientOut.flush()
                    return@withContext
                }
                remoteSocket = Socket()
                remoteSocket.connect(InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS)
                remoteSocket.soTimeout = SO_TIMEOUT_MS
                val remoteOut = remoteSocket.getOutputStream()

                val relativePath = uri.rawPath + (if (uri.rawQuery != null) "?" + uri.rawQuery else "")
                val path = if (relativePath.isEmpty()) "/" else relativePath
                val newRequestLine = "$method $path ${parts[2]}\r\n"

                remoteOut.write(newRequestLine.toByteArray())
                
                val remainingHeadersStart = firstLineEnd + 2
                val lines = request.substring(remainingHeadersStart, headersEndIndex).split("\r\n")
                for (line in lines) {
                    if (line.isNotEmpty() &&
                        !line.startsWith("Proxy-Connection:", ignoreCase = true) &&
                        !line.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                        remoteOut.write((line + "\r\n").toByteArray())
                    }
                }
                
                remoteOut.write("Connection: close\r\n".toByteArray())
                remoteOut.write("\r\n".toByteArray())
                remoteOut.flush()
                
                val bodyStart = headersEndIndex + 4
                if (bodyStart < headerBuilder.length) {
                    val initialBody = headerBuilder.substring(bodyStart).toByteArray()
                    remoteOut.write(initialBody)
                    remoteOut.flush()
                }

                relay(clientSocket, remoteSocket)
            }
        } catch (e: Exception) {
            Log.w("HttpProxyServer", "Client connection error: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { remoteSocket?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun relay(client: Socket, remote: Socket) = coroutineScope {
        launch(Dispatchers.IO) {
            copyStream(client.getInputStream(), remote.getOutputStream()) { bytes ->
                TrafficMonitor.addProxyRx(bytes.toLong())
            }
        }
        launch(Dispatchers.IO) {
            copyStream(remote.getInputStream(), client.getOutputStream()) { bytes ->
                TrafficMonitor.addProxyTx(bytes.toLong())
            }
        }
    }

    private fun parseHeaders(request: String, firstLineEnd: Int, headersEndIndex: Int): Map<String, String> {
        val map = HashMap<String, String>()
        val start = firstLineEnd + 2
        if (start >= headersEndIndex) return map
        for (line in request.substring(start, headersEndIndex).split("\r\n")) {
            val idx = line.indexOf(':')
            if (idx > 0) map[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return map
    }

    // The proxy is reachable by every host on the LAN. Require the owner PIN so it cannot be
    // abused as an open relay, and refuse to connect to the device itself or to internal
    // networks so it cannot be used for SSRF / internal pivoting.
    private fun isProxyAuthorized(headers: Map<String, String>): Boolean {
        val provided = headers["proxy-authorization"] ?: return false
        val expected = "Basic " + Base64.getEncoder().encodeToString("admin:${AuthHelper.currentPin.value}".toByteArray())
        return java.security.MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())
    }

    private fun isBlockedDestination(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress) return true
        val b = addr.address
        // Only IPv4 is allowed through (see resolveIPv4). Block ranges the standard predicates
        // miss: 0.0.0.0/8 and CGNAT 100.64.0.0/10.
        if (b.size == 4) {
            val o0 = b[0].toInt() and 0xFF
            val o1 = b[1].toInt() and 0xFF
            if (o0 == 0) return true
            if (o0 == 100 && o1 in 64..127) return true
            return false
        }
        // Not IPv4 (e.g. IPv6 ULA fc00::/7, which isSiteLocalAddress does not cover): block.
        return true
    }

    /** Resolve to an IPv4 address only; return null if the host has no usable IPv4. */
    private fun resolveIPv4(host: String): InetAddress? {
        return try {
            InetAddress.getAllByName(host).firstOrNull { it is java.net.Inet4Address }
        } catch (e: UnknownHostException) {
            null
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream, onBytesRead: (Int) -> Unit = {}) {
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                output.flush()
                onBytesRead(bytesRead)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.d("HttpProxyServer", "Stream read timeout")
        } catch (e: java.net.SocketException) {
            Log.d("HttpProxyServer", "Stream closed: ${e.message}")
        } catch (e: Exception) {
            Log.d("HttpProxyServer", "Stream copy ended: ${e.message}")
        }
    }
}
