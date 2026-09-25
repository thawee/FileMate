package com.apincer.fileserver

import com.apincer.fileserver.ui.browser.isValidLocalFileName
import org.junit.Assert.*
import org.junit.Test

class FileNameValidationTest {
    @Test fun acceptsSingleFileNameIncludingCommas() {
        assertTrue(isValidLocalFileName("report,final.txt"))
    }

    @Test fun rejectsPathSegmentsAndSpecialNames() {
        listOf("../outside", "a/b", "a\\b", ".", "..", " ", "x\u0000y").forEach {
            assertFalse(it, isValidLocalFileName(it))
        }
    }
}
