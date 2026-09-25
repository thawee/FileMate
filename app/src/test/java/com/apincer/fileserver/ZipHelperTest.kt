package com.apincer.fileserver

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipHelperTest {
    @Test fun existingDestinationIsNotOverwritten() {
        val parent = Files.createTempDirectory("zip-test").toFile()
        try {
            val destination = File(parent, "photos").apply { mkdir() }
            File(destination, "a.jpg").writeText("original")
            assertFalse(ZipHelper.unzipFile(ByteArrayInputStream(zip("a.jpg", "replacement")), destination))
            assertEquals("original", File(destination, "a.jpg").readText())
        } finally { parent.deleteRecursively() }
    }

    @Test fun failedExtractionDoesNotLeavePartialDestination() {
        val parent = Files.createTempDirectory("zip-test").toFile()
        try {
            val bytes = zip("a.txt", "content")
            val failing = object : InputStream() {
                var offset = 0
                override fun read(): Int {
                    if (offset >= bytes.size / 2) throw IOException("broken stream")
                    return bytes[offset++].toInt() and 0xff
                }
            }
            val destination = File(parent, "archive")
            assertFalse(ZipHelper.unzipFile(failing, destination))
            assertFalse(destination.exists())
        } finally { parent.deleteRecursively() }
    }

    private fun zip(name: String, content: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { it.putNextEntry(ZipEntry(name)); it.write(content.toByteArray()); it.closeEntry() }
        return output.toByteArray()
    }
}
