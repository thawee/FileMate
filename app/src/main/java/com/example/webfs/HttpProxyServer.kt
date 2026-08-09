package com.example.webfs

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

            if (method.equals("CONNECT", ignoreCase = true)) {
                val hostPort = url.split(":")
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toInt() else 443

                val addr = resolveIPv4(host) ?: throw UnknownHostException("Cannot resolve: $host")
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
                    if (line.isNotEmpty() && !line.startsWith("Proxy-Connection:", ignoreCase = true)) {
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

    private fun resolveIPv4(host: String): InetAddress? {
        try {
            val all = InetAddress.getAllByName(host)
            val ipv4 = all.firstOrNull { it is java.net.Inet4Address }
            if (ipv4 != null) return ipv4
            return all.firstOrNull()
        } catch (e: UnknownHostException) {
            return null
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
