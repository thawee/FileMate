package com.apincer.fileserver.ui.browser

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun isValidLocalFileName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." &&
        '/' !in name && '\\' !in name && '\u0000' !in name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = viewModel(),
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 8.dp,
    onFileClick: (FileItem) -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val directoryError by viewModel.directoryError.collectAsState()
    val isListView by viewModel.isListView.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val isSelectionMode = selectedPaths.isNotEmpty()

    var selectedFile by remember { mutableStateOf<FileItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Intercept system back gestures: exit selection mode first, then navigate up
    val canGoBack = currentPath.absolutePath != viewModel.rootDir.absolutePath
    androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }
    androidx.activity.compose.BackHandler(enabled = !isSelectionMode && canGoBack) {
        viewModel.navigateUp()
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    // ── New Folder Dialog ─────────────────────────────────────────────────────
    if (showMkdirDialog) {
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Documents") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (!isValidLocalFileName(trimmed)) {
                            Toast.makeText(context, "Enter a name without path separators", Toast.LENGTH_SHORT).show()
                        } else {
                            val newDir = File(currentPath, trimmed)
                            if (newDir.exists()) {
                                Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
                            } else if (newDir.mkdir()) {
                                Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                                viewModel.reload()
                                showMkdirDialog = false
                            } else {
                                Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Batch Delete Confirm Dialog ───────────────────────────────────────────
    if (showDeleteSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectionDialog = false },
            title = { Text("Delete ${selectedPaths.size} item${if (selectedPaths.size != 1) "s" else ""}?") },
            text = { Text("This will permanently delete all selected items. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSelectionDialog = false
                        coroutineScope.launch {
                            val (success, failed) = viewModel.deleteSelected()
                            val msg = if (failed == 0) "Deleted $success item${if (success != 1) "s" else ""}"
                                      else "Deleted $success, failed $failed"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Search and Sort Bar ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search files...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                singleLine = true,
                shape = RoundedCornerShape(26.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { newFolderName = ""; showMkdirDialog = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = MaterialTheme.colorScheme.primary)
            }
            var sortExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort Options", tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Name (A-Z)") }, onClick = { viewModel.updateSort(FileBrowserViewModel.SortOption.NAME, FileBrowserViewModel.SortOrder.ASCENDING); sortExpanded = false })
                    DropdownMenuItem(text = { Text("Name (Z-A)") }, onClick = { viewModel.updateSort(FileBrowserViewModel.SortOption.NAME, FileBrowserViewModel.SortOrder.DESCENDING); sortExpanded = false })
                    DropdownMenuItem(text = { Text("Size (Ascending)") }, onClick = { viewModel.updateSort(FileBrowserViewModel.SortOption.SIZE, FileBrowserViewModel.SortOrder.ASCENDING); sortExpanded = false })
                    DropdownMenuItem(text = { Text("Size (Descending)") }, onClick = { viewModel.updateSort(FileBrowserViewModel.SortOption.SIZE, FileBrowserViewModel.SortOrder.DESCENDING); sortExpanded = false })
                    DropdownMenuItem(text = { Text("Date (Oldest)") }, onClick = { viewModel.updateSort(FileBrowserViewModel.SortOption.DATE, FileBrowserViewModel.SortOrder.ASCENDING); sortExpanded = false })
                    DropdownMenuItem(text = { Text("Date (Newest)") }, onClick = { viewModel.updateSort(FileBrowserViewModel.SortOption.DATE, FileBrowserViewModel.SortOrder.DESCENDING); sortExpanded = false })
                }
            }
        }

        // ── Breadcrumbs ───────────────────────────────────────────────────────
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rootPath = viewModel.rootDir.absolutePath
            val currentAbsolutePath = currentPath.absolutePath
            val relativePath = currentAbsolutePath.removePrefix(rootPath)
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.clickable { viewModel.navigateToRoot() }
            ) {
                Text(
                    text = "🏠 Home",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (relativePath.isNotEmpty()) {
                val segments = relativePath.split("/").filter { it.isNotEmpty() }
                var pathBuilder = rootPath
                segments.forEach { segment ->
                    pathBuilder += "/$segment"
                    val segmentFile = File(pathBuilder)
                    Text(" / ", color = Color.Gray, modifier = Modifier.padding(horizontal = 2.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.clickable { viewModel.loadDirectory(segmentFile) }
                    ) {
                        Text(
                            text = segment,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.reload()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading && files.isEmpty() && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (directoryError != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(directoryError ?: "Cannot read this folder", textAlign = TextAlign.Center)
                        TextButton(onClick = { viewModel.reload() }) { Text("Retry") }
                    }
                } else if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching files found" else "Folder is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                            )
                            if (searchQuery.isEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Upload files via browser or create new folders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { newFolderName = ""; showMkdirDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New Folder")
                                }
                            }
                        }
                    }
                } else {
                    val listPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = bottomPadding + 80.dp)
                    if (isListView) {
                        LazyColumn(
                            contentPadding = listPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            lazyItems(files, key = { it.path }) { item ->
                                FileListItem(
                                    item = item,
                                    isSelected = item.path in selectedPaths,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(item.path)
                                        } else if (item.isDirectory) {
                                            viewModel.loadDirectory(item.file)
                                        } else {
                                            onFileClick(item)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelection(item.path)
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            contentPadding = listPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(files, key = { it.path }) { item ->
                                FileGridItem(
                                    item = item,
                                    isSelected = item.path in selectedPaths,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(item.path)
                                        } else if (item.isDirectory) {
                                            viewModel.loadDirectory(item.file)
                                        } else {
                                            onFileClick(item)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelection(item.path)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Batch Toolbar (shown in selection mode) ───────────────────────
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 12.dp,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close / count
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                        Text(
                            text = "${selectedPaths.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        // Select All
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.primary)
                        }
                        // Share
                        IconButton(onClick = {
                            val uris = files
                                .filter { it.path in selectedPaths }
                                .map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it.file) }
                            if (uris.isNotEmpty()) {
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${uris.size} files"))
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected", tint = MaterialTheme.colorScheme.primary)
                        }
                        // Delete
                        IconButton(onClick = { showDeleteSelectionDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // ── FAB: New Folder (hidden in selection mode) ────────────────────
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { newFolderName = ""; showMkdirDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = bottomPadding + 16.dp, end = 16.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                }
            }
        }
    }

    if (selectedFile != null) {
        FileContextMenu(
            item = selectedFile!!,
            onDismiss = { selectedFile = null },
            onReload = { viewModel.reload() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextMenu(item: FileItem, onDismiss: () -> Unit, onReload: () -> Unit) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(item.name) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename ${if (item.isDirectory) "Folder" else "File"}") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (!isValidLocalFileName(trimmed)) {
                            Toast.makeText(context, "Enter a name without path separators", Toast.LENGTH_SHORT).show()
                        } else if (trimmed != item.name) {
                            val target = File(item.file.parentFile, trimmed)
                            if (target.exists()) {
                                Toast.makeText(context, "An item with this name already exists", Toast.LENGTH_SHORT).show()
                            } else if (item.file.renameTo(target)) {
                                Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                                onReload()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showRenameDialog = false
                    },
                    enabled = renameText.isNotBlank() && renameText.trim() != item.name
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${if (item.isDirectory) "Folder" else "File"}?") },
            text = { Text("Are you sure you want to delete '${item.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        if (item.file.deleteRecursively()) {
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            onReload()
                        } else {
                            Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPropertiesDialog) {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${item.name}", fontWeight = FontWeight.Bold)
                    Text("Location: ${item.file.parent ?: "/"}")
                    Text("Type: ${if (item.isDirectory) "Folder" else item.mimeType}")
                    Text("Size: ${if (item.isDirectory) "--" else formatSize(item.size)} (${item.size} bytes)")
                    Text("Modified: ${df.format(Date(item.lastModified))}")
                }
            },
            confirmButton = {
                Button(onClick = { showPropertiesDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(16.dp))
            
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share"))
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Rename") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    showRenameDialog = true
                }
            )
            ListItem(
                headlineContent = { Text("Properties") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    showPropertiesDialog = true
                }
            )
            ListItem(
                headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    showDeleteConfirmDialog = true
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    item: FileItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectionBorderColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.border(2.dp, selectionBorderColor, RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.mimeType.startsWith("video/")) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                FileTypeIconBadge(
                    item = item,
                    iconSize = 44.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Selection checkbox overlay
            if (isSelectionMode || isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FileTypeIconBadge(
    item: FileItem,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val ext = item.name.substringAfterLast('.', "").lowercase()
    val isArchive = item.mimeType.contains("zip") || item.mimeType.contains("tar") || item.mimeType.contains("compressed") || ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    val isPdf = item.mimeType == "application/pdf" || ext == "pdf"
    val isAudio = item.mimeType.startsWith("audio/") || ext in listOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "wma")
    val isCode = ext in listOf("kt", "java", "js", "ts", "py", "c", "cpp", "h", "cs", "php", "rb", "go", "rs", "swift", "html", "css", "xml", "json", "yaml", "yml", "sql", "sh", "bat")
    val isDoc = ext in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "log", "md")
    val isApk = ext in listOf("apk", "aab", "xapk")

    val (icon, color) = when {
        item.isDirectory -> Icons.Default.Folder to Color(0xFFFFA000)
        isPdf -> Icons.Default.PictureAsPdf to Color(0xFFE53935)
        isAudio -> Icons.Default.AudioFile to Color(0xFF8E24AA)
        isArchive -> Icons.Default.FolderZip to Color(0xFFFB8C00)
        isApk -> Icons.Default.Android to Color(0xFF43A047)
        isCode -> Icons.Default.Code to Color(0xFF00897B)
        isDoc -> Icons.Default.Description to Color(0xFF1E88E5)
        else -> Icons.Default.InsertDriveFile to Color(0xFF78909C)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectionBorderColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox or thumbnail
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(28.dp))
                } else {
                    FileTypeIconBadge(item = item, iconSize = 28.dp, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")) {
                    AsyncImage(
                        model = item.file,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.mimeType.startsWith("video/")) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(2.dp)
                                .size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                } else {
                    FileTypeIconBadge(
                        item = item,
                        iconSize = 28.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            
            val df = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            val dateStr = df.format(Date(item.lastModified))
            val sizeStr = if (item.isDirectory) "" else formatSize(item.size)
            val details = if (item.isDirectory) dateStr else "$dateStr • $sizeStr"
            
            Text(text = details, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
