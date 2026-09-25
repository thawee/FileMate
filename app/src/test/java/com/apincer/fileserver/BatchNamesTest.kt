package com.apincer.fileserver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder

class BatchNamesTest {
    @Test fun repeatedNameParametersPreserveCommasAndSpaces() {
        val names = listOf("report,final.txt", " spaced name.txt")
        val query = names.joinToString("&") { "name=" + URLEncoder.encode(it, "UTF-8") }
        assertEquals(names, parseBatchNames(query))
    }

    @Test fun legacyCommaSeparatedNamesRemainSupported() {
        assertEquals(listOf("first", "second"), parseBatchNames("names=first%2Csecond"))
    }
}
