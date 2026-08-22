package com.apincer.fileserver

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {

    fun zipFiles(files: List<File>, outputStream: OutputStream) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            for (file in files) {
                if (file.exists()) {
                    zipRecursive(file, file.name, zos)
                }
            }
        }
    }

    private fun zipRecursive(fileToZip: File, fileName: String, zos: ZipOutputStream) {
        if (fileToZip.isHidden) return
        
        if (fileToZip.isDirectory) {
            val children = fileToZip.listFiles() ?: return
            val dirName = if (fileName.endsWith("/")) fileName else "$fileName/"
            zos.putNextEntry(ZipEntry(dirName))
            zos.closeEntry()
            for (childFile in children) {
                zipRecursive(childFile, "$dirName${childFile.name}", zos)
            }
            return
        }
        
        FileInputStream(fileToZip).use { fis ->
            val zipEntry = ZipEntry(fileName)
            zos.putNextEntry(zipEntry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    fun unzipFile(inputStream: InputStream, destDir: File): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val destFile = File(destDir, entry.name)
                    // Security check against zip slip
                    val destDirPath = destDir.canonicalPath
                    val destFilePath = destFile.canonicalPath
                    if (!destFilePath.startsWith(destDirPath + File.separator)) {
                        entry = zis.nextEntry
                        continue
                    }
                    
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
