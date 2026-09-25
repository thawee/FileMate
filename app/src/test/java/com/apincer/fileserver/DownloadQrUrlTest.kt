package com.apincer.fileserver

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQrUrlTest {
    @Test fun nestedFileUsesNameOnlyInDownloadPath() {
        assertEquals(
            "http://localhost:8080/api/download/report%20final.pdf?path=Documents%2F2026",
            downloadQrUrl("http://localhost:8080", "Documents/2026", "report final.pdf")
        )
    }
}
