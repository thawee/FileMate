package com.example.webfs.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class DlnaCaster(
    override val deviceId: String,
    override val deviceName: String,
    private val controlUrl: String // The AVTransport control URL discovered via SSDP
) : TvCaster {

    private val client = OkHttpClient()

    override suspend fun showImage(imageUrl: String, imageBytes: ByteArray?) {
        withContext(Dispatchers.IO) {
            // 1. SetAVTransportURI
            val setUriSoap = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <InstanceID>0</InstanceID>
                      <CurrentURI>$imageUrl</CurrentURI>
                      <CurrentURIMetaData></CurrentURIMetaData>
                    </u:SetAVTransportURI>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            sendSoapAction("SetAVTransportURI", setUriSoap)

            // 2. Play
            val playSoap = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <InstanceID>0</InstanceID>
                      <Speed>1</Speed>
                    </u:Play>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            sendSoapAction("Play", playSoap)
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            val stopSoap = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <InstanceID>0</InstanceID>
                    </u:Stop>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            sendSoapAction("Stop", stopSoap)
        }
    }

    private fun sendSoapAction(action: String, body: String) {
        val requestBody = body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType())
        val request = Request.Builder()
            .url(controlUrl)
            .post(requestBody)
            .addHeader("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("DLNA error for $action: ${response.code} ${response.message}")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
