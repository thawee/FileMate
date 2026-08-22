
package com.apincer.fileserver.ui.browser
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.transformable
import com.apincer.fileserver.cast.TvCaster


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.apincer.fileserver.cast.CastingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    initialIndex: Int,
    mediaFiles: List<FileItem>,
    onClose: () -> Unit,
    onFileDeleted: () -> Unit,
    onFileResized: () -> Unit,
    discoveredDevices: List<TvCaster> = emptyList()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeCaster by CastingState.activeCaster.collectAsState()
    
    var isUiVisible by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var castStatus by remember { mutableStateOf("") }
    val showCastSheet by CastingState.showCastSheet.collectAsState()

    
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaFiles.size }
    )
    LaunchedEffect(pagerState.settledPage, activeCaster) {
        activeCaster?.let { caster ->
            val currentItem = mediaFiles[pagerState.settledPage]
            val primaryIp = com.apincer.fileserver.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
            val imageUrl = "http://$primaryIp:8080/files/${currentItem.path}"
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { currentItem.file.readBytes() } catch (e: Exception) { null }
            }
            caster.showImage(imageUrl, bytes)
        }
    }


    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            LaunchedEffect(CastingState.currentIndex.value) {
                if (pagerState.currentPage != CastingState.currentIndex.value) {
                    pagerState.animateScrollToPage(CastingState.currentIndex.value)
                }
            }
            LaunchedEffect(pagerState.settledPage) {
                if (CastingState.currentIndex.value != pagerState.settledPage) {
                    CastingState.currentIndex.value = pagerState.settledPage
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val fileItem = mediaFiles[page]
                ZoomableImage(
                    fileItem = fileItem,
                    onTap = { isUiVisible = !isUiVisible }
                )
            }

            // Top Bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = mediaFiles[pagerState.currentPage].name,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                    val isPlaying by CastingState.isPlaying.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.apincer.fileserver.ui.UnifiedCastButton(iconTint = Color.White)
                        IconButton(onClick = { CastingState.isPlaying.value = !isPlaying }) {
                            Icon(if (isPlaying) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White)
                        }
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                    }
                }

            }

            // Bottom Bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .padding(top = 16.dp, bottom = 48.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentFile = mediaFiles[pagerState.currentPage]

                    // Open With External App
                    IconButton(onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", currentFile.file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, currentFile.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open in app", tint = Color.White)
                    }

                    // Share
                    IconButton(onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", currentFile.file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = currentFile.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }

                    // Resize
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val bitmap = BitmapFactory.decodeFile(currentFile.file.absolutePath)
                                val resized = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
                                val newFile = File(currentFile.file.parent, "resized_${currentFile.name}")
                                val out = FileOutputStream(newFile)
                                resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                out.flush()
                                out.close()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Resized & saved to ${newFile.name}", Toast.LENGTH_SHORT).show()
                                    onFileResized()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Resize failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = "Resize", tint = Color.White)
                    }

                    // Delete
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (currentFile.file.delete()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                    if (mediaFiles.size == 1) {
                                        onClose()
                                    }
                                    onFileDeleted()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }

                    // Cast
                    if (activeCaster != null) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                try {
                                    castStatus = "Casting..."
                                    withContext(Dispatchers.IO) {
                                        val bytes = currentFile.file.readBytes()
                                        activeCaster?.showImage("http://dummy/", bytes)
                                    }
                                    castStatus = "Casting to ${activeCaster?.deviceName}"
                                } catch (e: Exception) {
                                    castStatus = "Cast failed: ${e.message}"
                                }
                            }
                        }) {
                            Icon(Icons.Default.CastConnected, contentDescription = "Cast", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Status Toast
            if (castStatus.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Text(text = castStatus, color = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
        
        // Info Bottom Sheet
        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInfoSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val currentFile = mediaFiles[pagerState.currentPage]
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                    Text("File Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Name: ${currentFile.name}")
                    Spacer(Modifier.height(4.dp))
                    Text("Path: ${currentFile.path}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Size: ${currentFile.size / 1024} KB")
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    Text("Modified: ${df.format(Date(currentFile.lastModified))}")
                    Text("MIME Type: ${currentFile.mimeType}")
                }
            }
        }

        if (showCastSheet) {
            com.apincer.fileserver.ui.UnifiedCastSheet(
                discoveredDevices = discoveredDevices,
                onDismiss = { CastingState.showCastSheet.value = false }
            )
        }

        }
    }

@Composable
fun ZoomableImage(fileItem: FileItem, onTap: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val isVideo = fileItem.mimeType.startsWith("video/") || fileItem.mimeType.startsWith("audio/")
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { _ ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isZooming = false
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        
                        val pointers = event.changes.size
                        if (pointers > 1) {
                            isZooming = true
                        }
                        
                        if (isZooming || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2
                            offset = androidx.compose.ui.geometry.Offset(
                                x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                            )
                            event.changes.forEach { it.consume() }
                        } else {
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = fileItem.file,
            contentDescription = fileItem.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )

        if (isVideo) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(72.dp)
                    .clickable {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileItem.file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, fileItem.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Play with"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No player found to open media", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
    }
}
