package com.apincer.fileserver

import com.apincer.fileserver.ui.browser.resizedJpegName
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizedImageNameTest {
    @Test fun renamedImageAlwaysHasJpegExtension() {
        assertEquals("resized_photo.jpg", resizedJpegName("photo.png"))
        assertEquals("resized_trip.jpg", resizedJpegName("trip.jpeg"))
    }
}
