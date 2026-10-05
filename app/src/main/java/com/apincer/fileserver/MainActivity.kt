package com.apincer.fileserver

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.apincer.fileserver.sharing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import com.apincer.fileserver.cast.CastingManager
import com.apincer.fileserver.cast.TvCaster
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.apincer.fileserver.ui.browser.FileBrowserScreen
import com.apincer.fileserver.ui.browser.FileBrowserViewModel


import com.apincer.fileserver.cast.CastingState
import com.apincer.fileserver.ui.browser.TextEditorScreen
import com.apincer.fileserver.ui.browser.PreviewScreen
import com.apincer.fileserver.ui.browser.FileItem


import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ChevronRight

import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : AppCompatActivity() {
    private var incomingFiles by mutableStateOf<List<Uri>>(emptyList())

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingFiles = incomingContentUris(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingFiles = incomingContentUris(intent)
        setContent {
            com.apincer.fileserver.theme.FileMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebFSScreen(incomingFiles = incomingFiles, onIncomingHandled = {
                        incomingFiles = emptyList()
                        setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                    })
                }
            }
        }
    }
}

fun generateQrCode(text: String, size: Int = 512): Bitmap? {
    if (text.isEmpty()) return null
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun getLocalIpAddresses(): List<String> {
    val list = NetworkUtils.getLocalNetworkAddresses().map { it.ip }
    return if (list.isEmpty()) listOf("127.0.0.1") else list
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    val value = bytes / Math.pow(1024.0, exp.toDouble())
    return String.format("%.1f", value) + " ${pre}B"
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebFSScreen(incomingFiles: List<Uri> = emptyList(), onIncomingHandled: () -> Unit = {}) {
    var showHostTools by remember { mutableStateOf(false) }
    var hostToolsInitialTab by remember { mutableStateOf(0) }
    val viewModel: FileBrowserViewModel = viewModel()
    val files by viewModel.files.collectAsState()
    
    val currentPath by viewModel.currentPath.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    var confirmReceive by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    var receiveStatus by remember { mutableStateOf<String?>(null) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var editorFileItem by remember { mutableStateOf<FileItem?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val castingManager = remember { CastingManager(context) }
    val discoveredDevices by castingManager.devices.collectAsState()

    DisposableEffect(Unit) {
        castingManager.startDiscovery()
        onDispose { castingManager.stopDiscovery() }
    }

    val isServerRunning by FileServerService.isRunningFlow.collectAsState()
    var networkAddresses by remember { mutableStateOf(NetworkUtils.getLocalNetworkAddresses()) }
    var selectedIpIndex by remember { mutableStateOf(0) }
    val primaryIp = networkAddresses.getOrNull(selectedIpIndex)?.ip
        ?: networkAddresses.firstOrNull()?.ip
        ?: "127.0.0.1"

    val currentPin by AuthHelper.currentPin.collectAsState()
    var showQuickQrDialog by remember { mutableStateOf(false) }
    var showSlideshowScreen by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        fun refreshAddresses() {
            networkAddresses = NetworkUtils.getLocalNetworkAddresses()
            if (selectedIpIndex >= networkAddresses.size) selectedIpIndex = 0
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAddresses()
                viewModel.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { refreshAddresses() }
            override fun onLost(network: android.net.Network) { refreshAddresses() }
            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) { refreshAddresses() }
            override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: android.net.LinkProperties) { refreshAddresses() }
        }
        val request = android.net.NetworkRequest.Builder().build()
        try { cm?.registerNetworkCallback(request, callback) } catch (e: Exception) {}
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { cm?.unregisterNetworkCallback(callback) } catch (e: Exception) {}
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startServer() {
        networkAddresses = NetworkUtils.getLocalNetworkAddresses()
        if (selectedIpIndex >= networkAddresses.size) selectedIpIndex = 0
        val rootUri = Uri.fromFile(Environment.getExternalStorageDirectory())
        val intent = Intent(context, FileServerService::class.java).apply {
            putExtra("FOLDER_URI", rootUri.toString())
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    val manageStorageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.reload()
                startServer()
            }
        }
    }

    var showPermissionRationale by remember { mutableStateOf(false) }

    fun requestAllFilesAccessAndStart() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startServer()
            } else {
                showPermissionRationale = true
            }
        } else {
            startServer()
        }
    }

    fun stopServer() {
        val intent = Intent(context, FileServerService::class.java).apply {
            action = "STOP"
        }
        context.startService(intent)
        networkAddresses = NetworkUtils.getLocalNetworkAddresses()
        if (selectedIpIndex >= networkAddresses.size) selectedIpIndex = 0
    }

    val slideshowActive by CastingState.slideshowActive.collectAsState()
    val slideshowSlides by CastingState.slides.collectAsState()
    val slideshowIndex by CastingState.currentIndex.collectAsState()
    val slideshowPlaying by CastingState.isPlaying.collectAsState()
    val slideshowTimer by CastingState.timerSeconds.collectAsState()
    val showCastSheet by CastingState.showCastSheet.collectAsState()

    val activeCaster by CastingState.activeCaster.collectAsState()

    // Global Auto-advance timer
    LaunchedEffect(slideshowActive, slideshowPlaying, slideshowIndex, slideshowTimer) {
        if (slideshowActive && slideshowPlaying && slideshowSlides.isNotEmpty()) {
            kotlinx.coroutines.delay(slideshowTimer * 1000L)
            CastingState.currentIndex.value = (slideshowIndex + 1) % slideshowSlides.size
        }
    }

    // Global Cast image when page changes
    LaunchedEffect(slideshowActive, slideshowIndex, activeCaster, previewIndex, isServerRunning) {
        if (slideshowActive && slideshowSlides.isNotEmpty() && previewIndex == null) {
            activeCaster?.let { caster ->
                val slide = slideshowSlides.getOrNull(slideshowIndex) ?: return@LaunchedEffect
                val imageUrl = if (caster is com.apincer.fileserver.cast.DlnaCaster) {
                    withContext(Dispatchers.IO) { photoShareUrl(java.io.File(slide.id), primaryIp) }
                } else slide.imageUrl
                if (imageUrl == null) {
                    Toast.makeText(context, "Start sharing to cast photos to this TV", Toast.LENGTH_SHORT).show()
                    return@LaunchedEffect
                }
                val bytes = slide.fetchImageBytes?.invoke()
                caster.showImage(imageUrl, bytes)
            }
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Permission Required") },
            text = { Text("ShareMate needs 'All files access' permission to serve files from your device storage to your local network.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionRationale = false
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    }
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQuickQrDialog) {
        QuickQrDialog(
            url = "http://$primaryIp:8080",
            pin = currentPin,
            onDismiss = { showQuickQrDialog = false }
        )
    }

    if (showSlideshowScreen) {
        Dialog(
            onDismissRequest = { showSlideshowScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false
            )
        ) {
            com.apincer.fileserver.ui.PremiumSlideshowScreen(
                discoveredDevices = discoveredDevices,
                onClose = { showSlideshowScreen = false }
            )
        }
    }

    if (showHostTools) {
        Dialog(
            onDismissRequest = { showHostTools = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ToolsAndProxyHubContent(
                initialTab = hostToolsInitialTab,
                onToggleServer = { shouldRun ->
                    if (shouldRun) requestAllFilesAccessAndStart() else stopServer()
                },
                onClose = { showHostTools = false }
            )
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = { Text("ShareMate", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Up")
                        }
                    },
                    actions = {
                        val isListView by viewModel.isListView.collectAsState()
                        com.apincer.fileserver.ui.UnifiedCastButton()
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(if (isListView) Icons.Default.GridView else Icons.Default.List, contentDescription = "Toggle View")
                        }
                        IconButton(onClick = { 
                            hostToolsInitialTab = 0
                            showHostTools = true 
                        }) {
                            Icon(Icons.Default.Build, contentDescription = "Tools & Proxy Hub")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
                
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            val hasFloatingMiniPlayer = slideshowActive && previewIndex == null && slideshowSlides.isNotEmpty()
            Column(modifier = Modifier.fillMaxSize()) {
                ServerDashboardCard(
                    isRunning = isServerRunning,
                    primaryIp = primaryIp,
                    networkAddresses = networkAddresses,
                    selectedIpIndex = selectedIpIndex,
                    onSelectIpIndex = { selectedIpIndex = it },
                    onToggleServer = { shouldRun ->
                        if (shouldRun) {
                            requestAllFilesAccessAndStart()
                        } else {
                            stopServer()
                        }
                    },
                    onShowQr = { showQuickQrDialog = true },
                    onOpenTools = { tab ->
                        hostToolsInitialTab = tab
                        showHostTools = true
                    }
                )

                ShareControls(
                    currentFolder = currentPath, selectedPaths = selectedPaths, root = viewModel.rootDir,
                    primaryIp = primaryIp, onStartServer = { requestAllFilesAccessAndStart() },
                    onPresent = {
                        val photos = files.filter { !it.isDirectory && it.mimeType.startsWith("image/") }
                        if (photos.isNotEmpty()) {
                            CastingState.slides.value = photos.map { photo ->
                                com.apincer.fileserver.ui.SlideItem(
                                    id = photo.path, imageUrl = Uri.fromFile(photo.file).toString(),
                                    title = photo.name, description = formatBytes(photo.size),
                                    fetchImageBytes = { withContext(Dispatchers.IO) { runCatching { photo.file.readBytes() }.getOrNull() } }
                                )
                            }
                            CastingState.currentIndex.value = 0
                            CastingState.slideshowActive.value = true
                            CastingState.isPlaying.value = true
                            showSlideshowScreen = true
                        } else Toast.makeText(context, "Open a folder with photos to present", Toast.LENGTH_SHORT).show()
                    }
                )
                if (incomingFiles.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("${incomingFiles.size} incoming ${if (incomingFiles.size == 1) "file" else "files"}. Choose a destination folder.", style = MaterialTheme.typography.bodySmall)
                        Text(currentPath.absolutePath, style = MaterialTheme.typography.bodySmall)
                        Row {
                            Button(enabled = !receiving, onClick = { confirmReceive = true }) { Text(if (receiving) "Saving…" else "Save here") }
                            TextButton(enabled = !receiving, onClick = onIncomingHandled) { Text("Cancel") }
                        }
                    }
                }
                receiveStatus?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }

                FileBrowserScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                    bottomPadding = if (hasFloatingMiniPlayer) 88.dp else 8.dp,
                    onFileClick = { fileItem -> 
                        val ext = fileItem.name.substringAfterLast('.', "").lowercase()

                        val textExtensions = listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "csv", "log")
                        if (fileItem.mimeType.startsWith("text/") || ext in textExtensions) {
                            editorFileItem = fileItem
                        } else if (fileItem.mimeType.startsWith("image/")) {
                            val mediaFiles = files.filter { !it.isDirectory && it.mimeType.startsWith("image/") }
                            val targetIndex = mediaFiles.indexOf(fileItem).takeIf { it >= 0 } ?: 0
                            CastingState.currentIndex.value = targetIndex
                            CastingState.isPlaying.value = false
                            previewIndex = targetIndex
                        } else {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileItem.file)
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, fileItem.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            } catch (e: Exception) {
                                Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    if (confirmReceive) AlertDialog(onDismissRequest = { confirmReceive = false },
        title = { Text("Save received files?") },
        text = { Text("Save ${incomingFiles.size} ${if (incomingFiles.size == 1) "file" else "files"} to ${currentPath.absolutePath}. Existing files stay unchanged; matching names are reported as conflicts.") },
        confirmButton = { TextButton(onClick = {
            confirmReceive = false; receiving = true
            val destination = currentPath
            val sources = incomingFiles.toList()
            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { receiveFiles(context.contentResolver, sources, destination) }
                    receiveStatus = "Saved ${result.saved} ${if (result.saved == 1) "file" else "files"}. ${result.failures.size} failed." + if (result.failures.isNotEmpty()) "\n" + result.failures.joinToString("\n") else ""
                    if (result.saved > 0) android.media.MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), null, null)
                    onIncomingHandled()
                    viewModel.reload()
                } catch (e: Exception) { receiveStatus = e.message ?: "Could not save files" }
                finally { receiving = false }
            }
        }) { Text("Save files") } },
        dismissButton = { TextButton(onClick = { confirmReceive = false }) { Text("Cancel") } })

    previewIndex?.let { index ->
        val mediaFiles = files.filter { !it.isDirectory && it.mimeType.startsWith("image/") }
        LaunchedEffect(mediaFiles) {
            val slides = mediaFiles.map { file ->
                com.apincer.fileserver.ui.SlideItem(
                    id = file.path,
                    imageUrl = Uri.fromFile(file.file).toString(),
                    title = file.name,
                    description = "${file.size / 1024} KB",
                    fetchImageBytes = {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try { file.file.readBytes() } catch (e: Exception) { null }
                        }
                    }
                )
            }
            CastingState.slides.value = slides
            CastingState.slideshowActive.value = true
        }
        PreviewScreen(
            initialIndex = index,
            mediaFiles = mediaFiles,
            onClose = { 
                previewIndex = null 
                CastingState.isPlaying.value = false
            },
            discoveredDevices = discoveredDevices,
            onFileDeleted = { deletedIndex -> 
                viewModel.loadDirectory(viewModel.currentPath.value) 
                val remainingSize = mediaFiles.size - 1
                if (remainingSize <= 0) {
                    previewIndex = null
                    CastingState.isPlaying.value = false
                } else {
                    val nextIndex = if (deletedIndex >= remainingSize) remainingSize - 1 else deletedIndex
                    previewIndex = nextIndex
                    CastingState.currentIndex.value = nextIndex
                }
            },
            onFileResized = {
                viewModel.loadDirectory(viewModel.currentPath.value) 
            },
            onStartSlideshow = {
                showSlideshowScreen = true
            }
        )
    }
    
    editorFileItem?.let { fileItem ->
        TextEditorScreen(
            fileItem = fileItem,
            onClose = { editorFileItem = null }
        )
    }

    if (slideshowActive && previewIndex == null && slideshowSlides.isNotEmpty()) {
        val currentSlide = slideshowSlides[slideshowIndex]
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showSlideshowScreen = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = java.io.File(currentSlide.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentSlide.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(if (activeCaster != null) "Casting to ${activeCaster?.deviceName}" else "Local Slideshow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { CastingState.isPlaying.value = !slideshowPlaying }) {
                    Icon(if (slideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause")
                }
                IconButton(onClick = { 
                    CastingState.slideshowActive.value = false
                    CastingState.isPlaying.value = false
                    coroutineScope.launch { CastingState.activeCaster.value?.stop() }
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Stop")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDashboardCard(
    isRunning: Boolean,
    primaryIp: String,
    networkAddresses: List<NetworkAddressInfo>,
    selectedIpIndex: Int,
    onSelectIpIndex: (Int) -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onShowQr: () -> Unit,
    onOpenTools: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "server_status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Main Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator dot
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRunning) "Server Active" else "Server Offline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRunning) "Ready for browser connections" else "Turn on to share files on Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                if (isRunning) {
                    IconButton(
                        onClick = onShowQr,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Show QR Code",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    IconButton(
                        onClick = { onOpenTools(1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Open Tools & Storage Cleaner",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Switch(
                    checked = isRunning,
                    onCheckedChange = onToggleServer,
                    modifier = Modifier.scale(0.85f)
                )

                if (isRunning && networkAddresses.size > 1) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Network Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // URL Pill Row when server is running
            AnimatedVisibility(visible = isRunning) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val serverUrl = "http://$primaryIp:8080"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(serverUrl))
                                Toast.makeText(context, "Copied $serverUrl", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy URL",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Quick Tool Access Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenTools(0) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Proxy :8081",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenTools(1) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Storage Cleaner",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Expanded: multiple network interfaces
                    if (isExpanded && networkAddresses.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select Network Interface:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(networkAddresses.indices.toList()) { idx ->
                                val net = networkAddresses[idx]
                                FilterChip(
                                    selected = idx == selectedIpIndex,
                                    onClick = { onSelectIpIndex(idx) },
                                    label = { Text("${net.displayName} (${net.ip})", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickQrDialog(
    url: String,
    pin: String = AuthHelper.currentPin.value,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val livePin by AuthHelper.currentPin.collectAsState()
    val activePin = if (livePin.isNotEmpty()) livePin else pin

    // Auto-login URL encoded into the QR code
    val qrConnectUrl = if (activePin.isNotEmpty()) "$url/?pin=$activePin" else url
    val qrBitmap = remember(qrConnectUrl) { generateQrCode(qrConnectUrl, 600) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan to Connect", fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Port 8080",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(190.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Direct Web URL Surface with 1-tap copy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(url))
                            Toast.makeText(context, "Copied $url", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Login Credentials Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LOGIN CREDENTIALS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "⚡ In QR Code",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Username
                            Column {
                                Text(
                                    text = "Username",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable {
                                        clipboardManager.setText(AnnotatedString("admin"))
                                        Toast.makeText(context, "Copied username 'admin'", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "admin",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            // PIN
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PIN (Password)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(activePin))
                                            Toast.makeText(context, "Copied PIN $activePin", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = activePin.chunked(1).joinToString(" "),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy PIN",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            AuthHelper.generateNewPin()
                                            Toast.makeText(context, "New PIN generated!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Regenerate PIN",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "📱 Camera scan connects automatically with zero login prompts. When entering URL manually on PC, enter username 'admin' and the PIN above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsAndProxyHubContent(
    initialTab: Int = 0,
    onToggleServer: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val isServerRunning by FileServerService.isRunningFlow.collectAsState()

    val webfsRx by TrafficMonitor.webfsRxBytes.collectAsState()
    val webfsTx by TrafficMonitor.webfsTxBytes.collectAsState()
    val proxyRx by TrafficMonitor.proxyRxBytes.collectAsState()
    val proxyTx by TrafficMonitor.proxyTxBytes.collectAsState()

    var networkAddresses by remember { mutableStateOf(NetworkUtils.getLocalNetworkAddresses()) }
    var selectedIpIndex by remember { mutableStateOf(0) }
    val primaryIp = networkAddresses.getOrNull(selectedIpIndex)?.ip
        ?: networkAddresses.firstOrNull()?.ip
        ?: "127.0.0.1"

    val currentPin by AuthHelper.currentPin.collectAsState()
    var selectedTab by remember { mutableStateOf(initialTab) }
    val coroutineScope = rememberCoroutineScope()
    var isCleaning by remember { mutableStateOf(false) }
    var cleaningStatus by remember { mutableStateOf("") }

    var showResultDialog by remember { mutableStateOf(false) }
    var showJunkConfirmation by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultSummary by remember { mutableStateOf("") }
    var resultItems by remember { mutableStateOf<List<String>>(emptyList()) }

    var storageRefreshTrigger by remember { mutableStateOf(0) }
    val storageStats = remember(storageRefreshTrigger) {
        try {
            val root = Environment.getExternalStorageDirectory()
            val stat = android.os.StatFs(root.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            Pair(freeBytes, totalBytes)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    val castingManager = remember { CastingManager(context) }
    val discoveredDevices by castingManager.devices.collectAsState()
    val activeCaster by CastingState.activeCaster.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        fun refreshAddresses() {
            networkAddresses = NetworkUtils.getLocalNetworkAddresses()
            if (selectedIpIndex >= networkAddresses.size) {
                selectedIpIndex = 0
            }
        }

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAddresses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { refreshAddresses() }
            override fun onLost(network: android.net.Network) { refreshAddresses() }
            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) { refreshAddresses() }
            override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: android.net.LinkProperties) { refreshAddresses() }
        }
        val request = android.net.NetworkRequest.Builder().build()
        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to register network callback", e)
        }

        castingManager.startDiscovery()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
            castingManager.stopDiscovery()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0B0F19)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header: Title, Server Quick Toggle & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tools & Proxy Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServerRunning) Color(0xFF10B981) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServerRunning) "Online • HTTP :8080 | Proxy :8081" else "Offline • Background services idle",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServerRunning) Color(0xFF34D399) else Color.LightGray
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = onToggleServer,
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3 Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabs = listOf(
                    Triple(0, "HTTP Proxy", Icons.Default.VpnKey),
                    Triple(1, "Storage", Icons.Default.CleaningServices),
                    Triple(2, "Diagnostics", Icons.Default.Security)
                )
                tabs.forEach { (index, title, icon) ->
                    val isSel = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = index },
                        color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSel) MaterialTheme.colorScheme.onPrimary else Color.LightGray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else Color.LightGray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: HTTP PROXY SUITE
                    val proxyUrl = "http://$primaryIp:8081"
                    val exportSnippet = "export http_proxy=\"http://$primaryIp:8081\"\nexport https_proxy=\"http://$primaryIp:8081\""
                    val curlSnippet = "curl -x http://$primaryIp:8081 https://icanhazip.com"

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Proxy Endpoint Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isServerRunning) Color(0xFF10B981) else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "HTTP/HTTPS Proxy Service",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = "PORT 8081",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Route Wi-Fi, LAN, or Hotspot client traffic through this device. Supports HTTP and transparent HTTPS CONNECT tunneling.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(proxyUrl))
                                            Toast.makeText(context, "Copied proxy address: $proxyUrl", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "PROXY ADDRESS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = proxyUrl,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Proxy",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Live Bandwidth
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Proxy Traffic Throughput",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("↑ ", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                            Text("Upload (Tx)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        Text(
                                            text = StorageMaintenanceHelper.formatBytes(proxyTx),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("↓ ", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                            Text("Download (Rx)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        Text(
                                            text = StorageMaintenanceHelper.formatBytes(proxyRx),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // CLI / Developer Snippets
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💻 Developer & Terminal CLI",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray
                                    )
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(exportSnippet))
                                            Toast.makeText(context, "Copied environment export snippet", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Export", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = exportSnippet,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Divider(color = Color.White.copy(alpha = 0.1f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "# Quick test using curl:",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = curlSnippet,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFA7F3D0)
                                        )
                                    }
                                }
                            }
                        }

                        // Client Setup Guides
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📱 Client Setup Guide",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val guides = listOf(
                                    Pair("iOS / iPadOS", "Settings → Wi-Fi → Tap (i) on network → Configure Proxy → Manual\n• Server: $primaryIp\n• Port: 8081"),
                                    Pair("Android", "Settings → Wi-Fi → Tap network gear/pencil → Advanced → Proxy: Manual\n• Proxy hostname: $primaryIp\n• Proxy port: 8081"),
                                    Pair("macOS", "System Settings → Network → Wi-Fi → Details → Proxies\n• Turn on 'Web Proxy (HTTP)' and 'Secure Web Proxy (HTTPS)'\n• Server: $primaryIp, Port: 8081"),
                                    Pair("Windows 10 / 11", "Settings → Network & Internet → Proxy\n• Manual proxy setup → Turn on 'Use a proxy server'\n• IP: $primaryIp, Port: 8081")
                                )

                                guides.forEachIndexed { idx, (os, steps) ->
                                    if (idx > 0) Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                                    Column {
                                        Text(
                                            text = os,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE2E8F0)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = steps,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: STORAGE CLEANER & MAINTENANCE
                    val (freeBytes, totalBytes) = storageStats
                    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
                    val usedPct = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Capacity Meter
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Device Storage Capacity",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "${(usedPct * 100).toInt()}% Used",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (usedPct > 0.9f) Color(0xFFF43F5E) else if (usedPct > 0.75f) Color(0xFFF59E0B) else Color(0xFF10B981)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                LinearProgressIndicator(
                                    progress = usedPct,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (usedPct > 0.9f) Color(0xFFF43F5E) else if (usedPct > 0.75f) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Free: ${StorageMaintenanceHelper.formatBytes(freeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Used: ${StorageMaintenanceHelper.formatBytes(usedBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        text = "Total: ${StorageMaintenanceHelper.formatBytes(totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // Empty Folder Cleaner Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Empty Directory Cleaner",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Scans storage and purges ghost empty folders",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Recursively searches user directories and safely removes leftover empty folders created by uninstalled apps (system and media root folders are strictly protected).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val sharedDir = FileServerService.sharedRoot ?: Environment.getExternalStorageDirectory()
                                        coroutineScope.launch(Dispatchers.IO) {
                                            isCleaning = true
                                            cleaningStatus = "Scanning and pruning empty directories..."
                                            val res = StorageMaintenanceHelper.cleanEmptyFolders(sharedDir, context)
                                            withContext(Dispatchers.Main) {
                                                isCleaning = false
                                                cleaningStatus = ""
                                                storageRefreshTrigger++
                                                resultTitle = "🧹 Empty Directory Cleanup"
                                                resultSummary = if (res.removedCount > 0)
                                                    "Successfully removed ${res.removedCount} empty folder(s)."
                                                else
                                                    "No empty directories were found in shared storage."
                                                resultItems = res.removedFolders
                                                showResultDialog = true
                                            }
                                        }
                                    },
                                    enabled = !isCleaning,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clean Empty Folders", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // OS Junk Purge Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "OS & Desktop Junk Purge",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Removes cross-platform desktop clutter",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Cleans hidden metadata files left behind from macOS, Windows, and temp transfers: .DS_Store, Thumbs.db, Desktop.ini, *.tmp, *.bak, and *~ backup files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        showJunkConfirmation = true
                                    },
                                    enabled = !isCleaning,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Purge OS Junk Files", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Live In-Card Cleaning Progress
                        if (isCleaning) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cleaningStatus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: NETWORK & SECURITY DIAGNOSTICS
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // WebUI Security PIN Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "WebUI Security PIN",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Active",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Web browser clients accessing File Mate must provide this PIN to authorize file downloads, deletions, and folder creations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = currentPin.chunked(1).joinToString("  "),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                letterSpacing = 2.sp
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(currentPin))
                                                Toast.makeText(context, "Copied PIN $currentPin", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Button(
                                            onClick = {
                                                AuthHelper.generateNewPin()
                                                Toast.makeText(context, "New PIN generated!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "New PIN", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New PIN", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Network Adapters Inspector
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Router,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Network Adapters (${networkAddresses.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            networkAddresses = NetworkUtils.getLocalNetworkAddresses()
                                            if (selectedIpIndex >= networkAddresses.size) selectedIpIndex = 0
                                            Toast.makeText(context, "Refreshed network interfaces", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (networkAddresses.isEmpty()) {
                                    Text(
                                        text = "⚠️ No active network adapter found. Connect to Wi-Fi, Ethernet, or turn on Mobile Hotspot.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFBBF24)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        networkAddresses.forEachIndexed { idx, net ->
                                            val isSelected = idx == selectedIpIndex
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedIpIndex = idx }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        val icon = when (net.type) {
                                                            NetworkType.HOTSPOT -> Icons.Default.WifiTethering
                                                            NetworkType.WIFI -> Icons.Default.Wifi
                                                            NetworkType.ETHERNET -> Icons.Default.Cable
                                                            NetworkType.USB_TETHERING -> Icons.Default.Usb
                                                            else -> Icons.Default.Language
                                                        }
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = null,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = "${net.displayName} (${net.interfaceName})",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                            Text(
                                                                text = net.ip,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                                            )
                                                        }
                                                    }

                                                    if (isSelected) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.primary
                                                        ) {
                                                            Text(
                                                                text = "PRIMARY",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Traffic Monitor & Reset Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Bandwidth Throughput",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    TextButton(
                                        onClick = {
                                            TrafficMonitor.reset()
                                            Toast.makeText(context, "Counters reset to zero", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.3f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "File Server (:8080)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tx: ${StorageMaintenanceHelper.formatBytes(webfsTx)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = "Rx: ${StorageMaintenanceHelper.formatBytes(webfsRx)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.3f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "HTTP Proxy (:8081)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF59E0B)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tx: ${StorageMaintenanceHelper.formatBytes(proxyTx)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = "Rx: ${StorageMaintenanceHelper.formatBytes(proxyRx)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // AirPlay & DLNA Casting Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Cast,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "AirPlay & DLNA Casting",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    if (activeCaster != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFA855F7).copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                text = "Connected",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFA855F7)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Discovered smart TVs and receivers on the current subnet via mDNS/SSDP.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (discoveredDevices.isEmpty()) {
                                    Text(
                                        text = "Searching for cast devices on local network...",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    discoveredDevices.forEach { device ->
                                        val isCur = activeCaster?.deviceId == device.deviceId
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCur) Color(0xFFA855F7).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, if (isCur) Color(0xFFA855F7) else Color.Transparent),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable { CastingState.activeCaster.value = device }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(device.deviceName, color = Color.White, fontWeight = FontWeight.Bold)
                                                    Text(device.deviceId, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                                }
                                                if (isCur) {
                                                    Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFFA855F7))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detailed Cleanup Results Dialog
    if (showJunkConfirmation) {
        AlertDialog(
            onDismissRequest = { showJunkConfirmation = false },
            title = { Text("Purge OS Junk Files?") },
            text = { Text("This permanently deletes .DS_Store, Thumbs.db, Desktop.ini, *.tmp, *.bak, *~ and ._* files from the selected storage folder and its subfolders. Backup and temporary files may contain work you want to keep. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    showJunkConfirmation = false
                    if (!isCleaning) {
                        val sharedDir = FileServerService.sharedRoot ?: Environment.getExternalStorageDirectory()
                        coroutineScope.launch {
                            isCleaning = true
                            cleaningStatus = "Scanning and purging OS junk files..."
                            val res = withContext(Dispatchers.IO) { StorageMaintenanceHelper.cleanJunkFiles(sharedDir, context) }
                            isCleaning = false
                            cleaningStatus = ""
                            storageRefreshTrigger++
                            val freedStr = StorageMaintenanceHelper.formatBytes(res.freedBytes)
                            resultTitle = "🗑️ Desktop Junk Purge"
                            resultSummary = if (res.removedCount > 0)
                                "Purged ${res.removedCount} hidden junk file(s), freeing $freedStr."
                            else "No desktop junk files were found."
                            resultItems = res.removedFiles
                            showResultDialog = true
                        }
                    }
                }) { Text("Delete files") }
            },
            dismissButton = { TextButton(onClick = { showJunkConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(
                    text = resultTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = resultSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (resultItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Items Deleted (${resultItems.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                resultItems.forEach { itemPath ->
                                    Text(
                                        text = "• $itemPath",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showResultDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
