package com.apincer.fileserver.ui.browser

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = viewModel(),
    bottomPadding: androidx.compose.ui.unit.Dp = 8.dp,
    onFileClick: (FileItem) -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isListView by viewModel.isListView.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var selectedFile by remember { mutableStateOf<FileItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Intercept system back gestures to navigate up folders until root
    val canGoBack = currentPath.absolutePath != viewModel.rootDir.absolutePath
    androidx.activity.compose.BackHandler(enabled = canGoBack) {
        viewModel.navigateUp()
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Sort Bar
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
            Spacer(modifier = Modifier.width(8.dp))
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

        // Breadcrumbs
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Upload files via browser or create new folders",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                val listPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = bottomPadding)
                if (isListView) {
                    LazyColumn(
                        contentPadding = listPadding,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        lazyItems(files, key = { it.path }) { item ->
                            FileListItem(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.loadDirectory(item.file)
                                    } else {
                                        onFileClick(item)
                                    }
                                },
                                onLongClick = { selectedFile = item }
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
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.loadDirectory(item.file)
                                    } else {
                                        onFileClick(item)
                                    }
                                },
                                onLongClick = { selectedFile = item }
                            )
                        }
                    }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(16.dp))
            
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
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
                headlineContent = { Text("Delete") },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    if (item.file.deleteRecursively()) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        onReload()
                    } else {
                        Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(item: FileItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isDirectory) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            } else if (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.mimeType.startsWith("video/")) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File",
                    tint = Color.Gray,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(item: FileItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isDirectory) {
                Icon(Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            } else if (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.mimeType.startsWith("video/")) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.BottomStart).padding(2.dp).size(16.dp))
                }
            } else {
                Icon(Icons.Default.InsertDriveFile, contentDescription = "File", tint = Color.Gray, modifier = Modifier.size(24.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            val df = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            val dateStr = df.format(Date(item.lastModified))
            val sizeStr = if (item.isDirectory) "" else formatSize(item.size)
            val details = if (item.isDirectory) dateStr else "$dateStr • $sizeStr"
            
            Text(text = details, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
