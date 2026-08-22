package com.apincer.fileserver.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AirPlayCaster(
    override val deviceId: String,
    override val deviceName: String,
    private val ipAddress: String,
    private val port: Int
) : TvCaster {

    private val client = OkHttpClient()
    private val sessionId = java.util.UUID.randomUUID().toString()

    override suspend fun showImage(imageUrl: String, imageBytes: ByteArray?) {
        if (imageBytes == null) {
            throw IllegalArgumentException("AirPlay requires raw imageBytes")
        }

        withContext(Dispatchers.IO) {
            CastingState.isTransferring.value = true
            val url = "http://$ipAddress:$port/photo"
            val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .addHeader("User-Agent", "MediaControl/1.0")
                .addHeader("X-Apple-Session-ID", sessionId)
                .addHeader("X-Apple-Transition", "None")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        println("AirPlay error: ${response.code} ${response.message}")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                CastingState.isTransferring.value = false
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            val url = "http://$ipAddress:$port/stop"
            val request = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody(null))
                .addHeader("User-Agent", "MediaControl/1.0")
                .addHeader("X-Apple-Session-ID", sessionId)
                .build()

            try {
                client.newCall(request).execute().close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
