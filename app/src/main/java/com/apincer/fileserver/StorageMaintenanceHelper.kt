package com.apincer.fileserver

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

object StorageMaintenanceHelper {

    private val PROTECTED_EMPTY_FOLDERS = setOf(
        "Android", "Android/data", "Android/media", "Android/obb",
        "DCIM", "Pictures", "Movies", "Music", "Download", "Documents",
        "Alarms", "Notifications", "Ringtones", "Podcasts", "Audiobooks"
    )

    data class CleanEmptyResult(val removedCount: Int, val removedFolders: List<String>)
    data class CleanJunkResult(val removedCount: Int, val freedBytes: Long, val removedFiles: List<String>)

    data class CategoryStat(val size: Long, val count: Int)
    data class FileEntry(val name: String, val relPath: String, val size: Long, val lastModified: Long)

    data class StorageStatsResult(
        val totalSize: Long,
        val totalFiles: Int,
        val totalFolders: Int,
        val emptyFoldersCount: Int,
        val junkFilesCount: Int,
        val images: CategoryStat,
        val videos: CategoryStat,
        val audio: CategoryStat,
        val docs: CategoryStat,
        val archives: CategoryStat,
        val other: CategoryStat,
        val topFiles: List<FileEntry>
    )

    fun cleanEmptyFolders(root: File, context: Context? = null): CleanEmptyResult {
        val removedList = mutableListOf<String>()
        cleanEmptyFoldersRecursive(root, root, removedList, context)
        return CleanEmptyResult(removedList.size, removedList)
    }

    private fun cleanEmptyFoldersRecursive(dir: File, root: File, removedList: MutableList<String>, context: Context?): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val children = dir.listFiles() ?: emptyArray()
        var isEmpty = true

        for (child in children) {
            if (child.isDirectory) {
                val childIsEmpty = cleanEmptyFoldersRecursive(child, root, removedList, context)
                if (!childIsEmpty) isEmpty = false
            } else {
                isEmpty = false
            }
        }

        if (isEmpty && dir != root) {
            val rootNorm = root.toPath().toAbsolutePath().normalize().toString()
            val dirNorm = dir.toPath().toAbsolutePath().normalize().toString()
            val relPath = dirNorm.removePrefix(rootNorm).trimStart('/', '\\').replace('\\', '/')
            
            if (PROTECTED_EMPTY_FOLDERS.contains(relPath)) {
                return false
            }

            val pathStr = dir.absolutePath
            if (dir.delete()) {
                removedList.add(if (relPath.isEmpty()) dir.name else relPath)
                if (context != null) {
                    try { MediaScannerConnection.scanFile(context, arrayOf(pathStr), null, null) } catch (_: Exception) {}
                }
                return true
            }
        }
        return false
    }

    fun isJunkFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name == ".ds_store" ||
                name == "thumbs.db" ||
                name == "desktop.ini" ||
                name.endsWith(".tmp") ||
                name.endsWith(".bak") ||
                name.endsWith("~") ||
                name.startsWith("._")
    }

    fun cleanJunkFiles(root: File, context: Context? = null): CleanJunkResult {
        var removedCount = 0
        var freedBytes = 0L
        val removedFiles = mutableListOf<String>()
        val rootNorm = root.toPath().toAbsolutePath().normalize().toString()

        fun scanAndCleanJunk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    scanAndCleanJunk(child)
                } else if (child.isFile && isJunkFile(child)) {
                    val size = child.length()
                    val pathStr = child.absolutePath
                    val childNorm = child.toPath().toAbsolutePath().normalize().toString()
                    val relPath = childNorm.removePrefix(rootNorm).trimStart('/', '\\')
                    if (child.delete()) {
                        removedCount++
                        freedBytes += size
                        removedFiles.add(if (relPath.isEmpty()) child.name else relPath)
                        if (context != null) {
                            try { MediaScannerConnection.scanFile(context, arrayOf(pathStr), null, null) } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        scanAndCleanJunk(root)
        return CleanJunkResult(removedCount, freedBytes, removedFiles)
    }

    fun computeStorageStats(root: File): StorageStatsResult {
        var totalSize = 0L
        var totalFiles = 0
        var totalFolders = 0
        var emptyFoldersCount = 0
        var junkFilesCount = 0

        var imagesSize = 0L; var imagesCount = 0
        var videosSize = 0L; var videosCount = 0
        var audioSize = 0L; var audioCount = 0
        var docsSize = 0L; var docsCount = 0
        var archivesSize = 0L; var archivesCount = 0
        var otherSize = 0L; var otherCount = 0

        val allFilesList = mutableListOf<FileEntry>()

        fun analyze(dir: File) {
            val children = dir.listFiles() ?: return
            if (children.isEmpty() && dir != root) {
                val rootNorm = root.toPath().toAbsolutePath().normalize().toString()
                val dirNorm = dir.toPath().toAbsolutePath().normalize().toString()
                val relPath = dirNorm.removePrefix(rootNorm).trimStart('/', '\\').replace('\\', '/')
                if (!PROTECTED_EMPTY_FOLDERS.contains(relPath)) {
                    emptyFoldersCount++
                }
            }

            for (child in children) {
                if (child.isDirectory) {
                    totalFolders++
                    analyze(child)
                } else if (child.isFile) {
                    totalFiles++
                    val length = child.length()
                    totalSize += length

                    if (isJunkFile(child)) junkFilesCount++

                    val ext = child.extension.lowercase()
                    when {
                        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "heic", "raw") -> { imagesSize += length; imagesCount++ }
                        ext in listOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "m4v") -> { videosSize += length; videosCount++ }
                        ext in listOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus", "wma") -> { audioSize += length; audioCount++ }
                        ext in listOf("pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "htm") -> { docsSize += length; docsCount++ }
                        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg") -> { archivesSize += length; archivesCount++ }
                        else -> { otherSize += length; otherCount++ }
                    }

                    val rootNorm = root.toPath().toAbsolutePath().normalize().toString()
                    val childNorm = child.toPath().toAbsolutePath().normalize().toString()
                    val relPath = childNorm.removePrefix(rootNorm).trimStart('/', '\\')

                    allFilesList.add(FileEntry(child.name, relPath, length, child.lastModified()))
                }
            }
        }

        analyze(root)
        allFilesList.sortByDescending { it.size }
        val top10 = allFilesList.take(10)

        return StorageStatsResult(
            totalSize, totalFiles, totalFolders, emptyFoldersCount, junkFilesCount,
            CategoryStat(imagesSize, imagesCount),
            CategoryStat(videosSize, videosCount),
            CategoryStat(audioSize, audioCount),
            CategoryStat(docsSize, docsCount),
            CategoryStat(archivesSize, archivesCount),
            CategoryStat(otherSize, otherCount),
            top10
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        val value = bytes / Math.pow(1024.0, exp.toDouble())
        return String.format("%.1f", value) + " ${pre}B"
    }
}
