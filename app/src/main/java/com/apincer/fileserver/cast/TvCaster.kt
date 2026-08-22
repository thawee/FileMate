package com.apincer.fileserver.cast

/**
 * Common interface for casting media to a TV (DLNA, AirPlay, etc.)
 */
interface TvCaster {
    val deviceId: String
    val deviceName: String
    
    /**
     * Casts an image to the TV.
     * @param imageUrl The URL where the TV can download the image (e.g., from SonicNIO),
     *                 OR it might be unused if the implementation pushes raw bytes.
     * @param imageBytes Optional raw JPEG bytes (useful for AirPlay /photo endpoint).
     */
    suspend fun showImage(imageUrl: String, imageBytes: ByteArray? = null)
    
    /**
     * Stop casting and disconnect
     */
    suspend fun stop()
}
