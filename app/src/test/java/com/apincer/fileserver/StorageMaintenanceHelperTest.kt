package com.apincer.fileserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class StorageMaintenanceHelperTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun emptyCleanupPreservesAndroidSubtreesAndCleansSimilarNames() {
        val protected = listOf("Android/data/app/empty", "Android/media/album/empty", "Android/obb/empty", "nested/Android/empty")
            .map { directory(it) }
        val outside = directory("outside/empty")
        val similar = directory("AndroidBackup/empty")

        val result = StorageMaintenanceHelper.cleanEmptyFolders(temporaryFolder.root)

        protected.forEach { assertTrue(it.path, it.isDirectory) }
        assertFalse(outside.exists())
        assertFalse(similar.exists())
        assertEquals(setOf("outside/empty", "outside", "AndroidBackup/empty", "AndroidBackup"), result.removedFolders.toSet())
        assertEquals(4, result.removedCount)
    }

    @Test fun junkCleanupPreservesAndroidFilesAndCleansSimilarNames() {
        val protected = listOf("Android/data/app/cache.tmp", "Android/media/album/._photo.jpg", "Android/obb/game.bak", "nested/Android/Thumbs.db")
            .map { file(it, "keep") }
        val outside = file("outside/.DS_Store", "abc")
        val similar = file("AndroidBackup/desktop.ini", "12345")
        val normal = file("outside/notes.txt", "normal")

        val result = StorageMaintenanceHelper.cleanJunkFiles(temporaryFolder.root)

        protected.forEach { assertEquals("keep", it.readText()) }
        assertFalse(outside.exists())
        assertFalse(similar.exists())
        assertEquals("normal", normal.readText())
        assertEquals(setOf("outside/.DS_Store", "AndroidBackup/desktop.ini"), result.removedFiles.toSet())
        assertEquals(2, result.removedCount)
        assertEquals(8L, result.freedBytes)
    }

    @Test fun statsIncludeAndroidStorageButExcludeItFromCleanupCounts() {
        directory("Android/cache/empty")
        file("Android/cache/cached.tmp", "a".repeat(11))
        file("Android/media/photo.jpg", "b".repeat(17))
        directory("outside/empty")
        file("outside/notes.txt", "c".repeat(5))
        file("outside/trash.tmp", "d".repeat(3))
        directory("AndroidBackup/empty")
        file("AndroidBackup/trash.bak", "e".repeat(7))

        val result = StorageMaintenanceHelper.computeStorageStats(temporaryFolder.root)

        assertEquals(43L, result.totalSize)
        assertEquals(5, result.totalFiles)
        assertEquals(8, result.totalFolders)
        assertEquals(2, result.emptyFoldersCount)
        assertEquals(2, result.junkFilesCount)
        assertEquals(StorageMaintenanceHelper.CategoryStat(17L, 1), result.images)
        assertEquals(StorageMaintenanceHelper.CategoryStat(5L, 1), result.docs)
        assertEquals(StorageMaintenanceHelper.CategoryStat(21L, 3), result.other)
        assertEquals(listOf("Android/media/photo.jpg", "Android/cache/cached.tmp", "AndroidBackup/trash.bak", "outside/notes.txt", "outside/trash.tmp"), result.topFiles.map { it.relPath })
    }

    @Test fun callerRootInsideAndroidIsProtected() {
        val empty = directory("Android/data/app/empty")
        val junk = file("Android/data/app/state.tmp", "keep")
        val root = requireNotNull(empty.parentFile)

        assertEquals(0, StorageMaintenanceHelper.cleanEmptyFolders(root).removedCount)
        assertEquals(0, StorageMaintenanceHelper.cleanJunkFiles(root).removedCount)
        assertTrue(empty.isDirectory)
        assertEquals("keep", junk.readText())
        val stats = StorageMaintenanceHelper.computeStorageStats(root)
        assertEquals(0, stats.emptyFoldersCount)
        assertEquals(0, stats.junkFilesCount)
        assertEquals(1, stats.totalFiles)
        assertEquals(4L, stats.totalSize)
    }

    @Test fun androidNamedRootRemainsProtectedWhenItIsAnAlias() {
        val empty = directory("app-storage/empty")
        val junk = file("app-storage/state.tmp", "keep")
        val android = File(temporaryFolder.root, "Android")
        Files.createSymbolicLink(android.toPath(), requireNotNull(empty.parentFile).toPath())

        assertEquals(0, StorageMaintenanceHelper.cleanEmptyFolders(android).removedCount)
        assertEquals(0, StorageMaintenanceHelper.cleanJunkFiles(android).removedCount)
        assertTrue(empty.isDirectory)
        assertEquals("keep", junk.readText())
        val stats = StorageMaintenanceHelper.computeStorageStats(android)
        assertEquals(0, stats.emptyFoldersCount)
        assertEquals(0, stats.junkFilesCount)
        assertEquals(4L, stats.totalSize)
    }

    @Test fun aliasesIntoAndroidRemainProtected() {
        val empty = directory("Android/data/app/empty")
        val junk = file("Android/data/app/state.tmp", "keep")
        val directoryAlias = File(temporaryFolder.root, "app-files")
        val fileAlias = File(temporaryFolder.root, "recent.tmp")
        Files.createSymbolicLink(directoryAlias.toPath(), requireNotNull(empty.parentFile).toPath())
        Files.createSymbolicLink(fileAlias.toPath(), junk.toPath())

        assertEquals(0, StorageMaintenanceHelper.cleanEmptyFolders(temporaryFolder.root).removedCount)
        assertEquals(0, StorageMaintenanceHelper.cleanJunkFiles(temporaryFolder.root).removedCount)
        assertEquals(0, StorageMaintenanceHelper.cleanEmptyFolders(directoryAlias).removedCount)
        assertEquals(0, StorageMaintenanceHelper.cleanJunkFiles(directoryAlias).removedCount)
        assertTrue(empty.isDirectory)
        assertEquals("keep", junk.readText())
        assertTrue(Files.isSymbolicLink(directoryAlias.toPath()))
        assertTrue(Files.isSymbolicLink(fileAlias.toPath()))
        val stats = StorageMaintenanceHelper.computeStorageStats(temporaryFolder.root)
        assertEquals(0, stats.emptyFoldersCount)
        assertEquals(0, stats.junkFilesCount)
        assertEquals(3, stats.totalFiles)
        assertEquals(12L, stats.totalSize)
    }

    private fun directory(path: String) = File(temporaryFolder.root, path).apply { assertTrue(mkdirs()) }

    private fun file(path: String, content: String) = File(temporaryFolder.root, path).apply {
        requireNotNull(parentFile).mkdirs()
        writeText(content)
    }
}
