package com.apincer.fileserver.sharing

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class IncomingFilesInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var source: File
    private lateinit var target: File

    @Before fun prepare() {
        directory = File(context.getExternalFilesDir(null), "receive-test-${System.nanoTime()}").apply { mkdirs() }
        source = File(directory, "sources").apply { mkdir() }
        target = File(directory, "chosen-destination").apply { mkdir() }
    }
    @After fun cleanup() { directory.deleteRecursively() }
    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    @Test fun singleContentUriCopiesOnlyToChosenFolderAndKeepsExistingFile() {
        val original = File(source, "photo.jpg").apply { writeText("image-content") }
        val contentUri = uri(original)
        val intent = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, contentUri) }
        assertEquals(listOf(contentUri), incomingContentUris(intent))
        val first = receiveFiles(context.contentResolver, incomingContentUris(intent), target)
        assertEquals(1, first.saved)
        assertTrue(first.failures.isEmpty())
        assertEquals("image-content", File(target, "photo.jpg").readText())
        original.writeText("replacement")
        val second = receiveFiles(context.contentResolver, listOf(contentUri), target)
        assertEquals(0, second.saved)
        assertEquals(1, second.failures.size)
        assertEquals("image-content", File(target, "photo.jpg").readText())
        assertTrue(target.listFiles()!!.none { it.name.startsWith(".sharemate-") })
    }

    @Test fun multipleUrisReportMissingSourceAndNeverLeavePartialFiles() {
        val a = File(source, "first.txt").apply { writeText("first") }
        val b = File(source, "second.txt").apply { writeText("second") }
        val absent = File(source, "missing.txt")
        val expected = listOf(uri(a), uri(b), uri(absent))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "text/plain"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(expected)) }
        assertEquals(expected, incomingContentUris(intent))
        val result = receiveFiles(context.contentResolver, incomingContentUris(intent), target)
        assertEquals(2, result.saved)
        assertEquals(1, result.failures.size)
        assertEquals(setOf("first.txt", "second.txt"), target.listFiles()!!.map { it.name }.toSet())
        assertFalse(File(target, "missing.txt").exists())
    }

    @Test fun fileUrisAndUnrelatedActionsDoNotBecomeReceiveSources() {
        val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(source, "local.txt"))) }
        assertTrue(incomingContentUris(intent).isEmpty())
        assertTrue(incomingContentUris(Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://example/file"))).isEmpty())
    }
}
