package com.apincer.fileserver

import com.apincer.fileserver.http.NioHttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HttpRequestBodyTest {
    @Test
    fun bodyIncludesBytesReceivedAfterHeaders() {
        val headers = "POST /api/upload HTTP/1.1\r\nContent-Length: 5\r\n\r\n".toByteArray()
        val request = NioHttpServer.HttpRequest()
        request.parse(headers + "ab".toByteArray(), headers.size, "127.0.0.1")
        request.updateBody(headers + "abcde".toByteArray())
        assertArrayEquals("abcde".toByteArray(), request.body)
    }
}
