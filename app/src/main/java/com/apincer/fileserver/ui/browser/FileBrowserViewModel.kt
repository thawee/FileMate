package com.apincer.fileserver.ui.browser

import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.apincer.fileserver.FileServerService

class FileBrowserViewModel : ViewModel() {

    val rootDir = Environment.getExternalStorageDirectory()

    private val _currentPath = MutableStateFlow(rootDir)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isListView = MutableStateFlow(false)
    val isListView: StateFlow<Boolean> = _isListView.asStateFlow()

    enum class SortOption { NAME, SIZE, DATE }
    enum class SortOrder { ASCENDING, DESCENDING }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    fun toggleViewMode() {
        _isListView.value = !_isListView.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        loadDirectory(_currentPath.value)
    }

    fun updateSort(option: SortOption, order: SortOrder) {
        _sortOption.value = option
        _sortOrder.value = order
        loadDirectory(_currentPath.value)
    }

    fun toggleHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        loadDirectory(_currentPath.value)
    }

    fun reload() {
        loadDirectory(_currentPath.value)
    }


    init {
        loadDirectory(FileServerService.sharedRoot ?: rootDir)
    }

    fun loadDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        _currentPath.value = directory
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val fileList = directory.listFiles()?.toList() ?: emptyList()
            
            val query = _searchQuery.value.lowercase()
            val sortOpt = _sortOption.value
            val sortOrd = _sortOrder.value
            val showHidden = _showHiddenFiles.value
            
            var mappedFiles = fileList
                .filter { showHidden || !it.name.startsWith(".") }
                .filter { it.name.lowercase() != "android" } // Hide Android dir
                .filter { query.isEmpty() || it.name.lowercase().contains(query) }
                .map { file ->
                    FileItem(
                        file = file,
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0,
                        lastModified = file.lastModified(),
                        mimeType = getMimeType(file.name)
                    )
                }
                
            mappedFiles = when (sortOpt) {
                SortOption.NAME -> if (sortOrd == SortOrder.ASCENDING) {
                    mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } else {
                    mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).reversed()
                }
                SortOption.SIZE -> if (sortOrd == SortOrder.ASCENDING) {
                    mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
                } else {
                    mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.size })).reversed()
                }
                SortOption.DATE -> if (sortOrd == SortOrder.ASCENDING) {
                    mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
                } else {
                    mappedFiles.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified })).reversed()
                }
            }
                
            _files.value = mappedFiles
            _isLoading.value = false
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current.absolutePath != rootDir.absolutePath && current.parentFile != null) {
            loadDirectory(current.parentFile!!)
        }
    }
    
    fun navigateToRoot() {
        loadDirectory(rootDir)
    }

    private fun getMimeType(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName) ?: ""
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
    }
}
