package com.apincer.fileserver

import com.apincer.fileserver.http.NioHttpServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageMimeTypeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun originalImageFormatsHaveBrowserMimeTypes() {
        assertEquals("image/svg+xml", NioHttpServer.MimeTypeUtil.getMimeType("art.svg"))
        assertEquals("image/bmp", NioHttpServer.MimeTypeUtil.getMimeType("photo.bmp"))
        assertEquals("image/x-icon", NioHttpServer.MimeTypeUtil.getMimeType("favicon.ico"))
        assertEquals("image/svg+xml", NioHttpServer.MimeTypeUtil.getMimeType("ART.SVG"))
        assertEquals("image/bmp", NioHttpServer.MimeTypeUtil.getMimeType("PHOTO.BMP"))
        assertEquals("image/x-icon", NioHttpServer.MimeTypeUtil.getMimeType("FAVICON.ICO"))
    }

    @Test
    fun svgDownloadUsesSvgMimeType() {
        val image = temporaryFolder.newFile("art.svg")
        image.writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="red"/></svg>""")

        assertEquals("image/svg+xml", NioHttpServer.MimeTypeUtil.readContentForMime(image))
    }
}
