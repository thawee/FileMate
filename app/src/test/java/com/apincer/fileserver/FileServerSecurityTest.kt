package com.apincer.fileserver

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileServerSecurityTest {

    @Test
    fun testPathNormalizationSecurity() {
        val root = File("/tmp/webfs_root")
        root.mkdirs()
        try {
            val rootPath = root.toPath().toAbsolutePath().normalize()
            val safeChild = File(root, "subfolder")
            val safePath = safeChild.toPath().toAbsolutePath().normalize()
            assertTrue(safePath.startsWith(rootPath))

            val badChild = File(root, "../outside")
            val badPath = badChild.toPath().toAbsolutePath().normalize()
            assertFalse(badPath.startsWith(rootPath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testFilenameSanitization() {
        val invalidNames = listOf("../etc/passwd", "..\\boot.ini", "/etc/shadow", "test/../../malicious.txt")
        for (name in invalidNames) {
            val safeName = File(name.replace('\\', '/')).name
            val hasPathSep = safeName.contains("/") || safeName.contains("\\") || safeName == ".." || safeName == "."
            assertFalse("Path separator leaked in $safeName", hasPathSep)
        }
    }

    @Test
    fun testJunkFileDetection() {
        val junkNames = listOf(".DS_Store", "Thumbs.db", "desktop.ini", "cache.tmp", "draft.bak", "file~", "._image.png")
        val normalNames = listOf("image.png", "document.pdf", "script.js")

        fun isJunk(name: String): Boolean {
            val n = name.lowercase()
            return n == ".ds_store" || n == "thumbs.db" || n == "desktop.ini" || n.endsWith(".tmp") || n.endsWith(".bak") || n.endsWith("~") || n.startsWith("._")
        }

        for (name in junkNames) {
            assertTrue("Expected $name to be identified as junk", isJunk(name))
        }
        for (name in normalNames) {
            assertFalse("Expected $name to NOT be identified as junk", isJunk(name))
        }
    }
}
