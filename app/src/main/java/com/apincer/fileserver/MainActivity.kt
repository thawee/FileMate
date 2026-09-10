package com.apincer.fileserver

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.apincer.fileserver.theme.FileMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebFSScreen()
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
fun WebFSScreen() {
    var showHostTools by remember { mutableStateOf(false) }
    val viewModel: FileBrowserViewModel = viewModel()
    val files by viewModel.files.collectAsState()
    
    val currentPath by viewModel.currentPath.collectAsState()
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
    LaunchedEffect(slideshowActive, slideshowIndex, activeCaster) {
        if (slideshowActive && slideshowSlides.isNotEmpty()) {
            activeCaster?.let { caster ->
                val slide = slideshowSlides[slideshowIndex]
                val bytes = slide.fetchImageBytes?.invoke()
                caster.showImage(slide.imageUrl, bytes)
            }
        }
    }


    if (showHostTools) {
        Dialog(
            onDismissRequest = { showHostTools = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            HostAndToolsContent(onClose = { showHostTools = false })
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = { Text("File Mate", fontWeight = FontWeight.Bold) },
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
                        IconButton(onClick = { showHostTools = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Host & Tools")
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
            FileBrowserScreen(
                viewModel = viewModel,
                bottomPadding = if (hasFloatingMiniPlayer) 88.dp else 8.dp,
                onFileClick = { fileItem -> 
                    val ext = fileItem.name.substringAfterLast('.', "").lowercase()

                    val textExtensions = listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "csv", "log")
                    if (fileItem.mimeType.startsWith("text/") || ext in textExtensions) {
                        editorFileItem = fileItem
                    } else {
                        val mediaFiles = files.filter { !it.isDirectory && !(it.mimeType.startsWith("text/") || it.name.substringAfterLast('.', "").lowercase() in textExtensions) }
                        val targetIndex = mediaFiles.indexOf(fileItem).takeIf { it >= 0 } ?: 0
                        CastingState.currentIndex.value = targetIndex
                        CastingState.isPlaying.value = false
                        previewIndex = targetIndex
                    }
                }
            )
        }
    }

    previewIndex?.let { index ->
        val textExts = listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "csv", "log")
        val mediaFiles = files.filter { !it.isDirectory && !(it.mimeType.startsWith("text/") || it.name.substringAfterLast('.', "").lowercase() in textExts) }
        LaunchedEffect(mediaFiles) {
            val primaryIp = com.apincer.fileserver.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
            val slides = mediaFiles.map { file ->
                com.apincer.fileserver.ui.SlideItem(
                    id = file.path,
                    imageUrl = "http://$primaryIp:8080/files/${file.path}",
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
            onFileDeleted = { 
                viewModel.loadDirectory(viewModel.currentPath.value) 
                previewIndex = null
                CastingState.isPlaying.value = false
            },
            onFileResized = {
                viewModel.loadDirectory(viewModel.currentPath.value) 
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
                    .clickable { previewIndex = CastingState.currentIndex.value }
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

@Composable
fun HostAndToolsContent(onClose: () -> Unit) {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf(FileServerService.isRunning) }

    val webfsRx by TrafficMonitor.webfsRxBytes.collectAsState()
    val webfsTx by TrafficMonitor.webfsTxBytes.collectAsState()
    val proxyRx by TrafficMonitor.proxyRxBytes.collectAsState()
    val proxyTx by TrafficMonitor.proxyTxBytes.collectAsState()

    var networkAddresses by remember { mutableStateOf(NetworkUtils.getLocalNetworkAddresses()) }
    var selectedIpIndex by remember { mutableStateOf(0) }
    val primaryIp = networkAddresses.getOrNull(selectedIpIndex)?.ip
        ?: networkAddresses.firstOrNull()?.ip
        ?: "127.0.0.1"
    val qrCodeBitmap = remember(primaryIp) { generateQrCode("http://$primaryIp:8080") }

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
                isServerRunning = FileServerService.isRunning
                refreshAddresses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                refreshAddresses()
            }
            override fun onLost(network: android.net.Network) {
                refreshAddresses()
            }
            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                refreshAddresses()
            }
            override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: android.net.LinkProperties) {
                refreshAddresses()
            }
        }
        val request = android.net.NetworkRequest.Builder().build()
        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to register network callback", e)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
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
        isServerRunning = true
    }

    val manageStorageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
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
        isServerRunning = false
        networkAddresses = NetworkUtils.getLocalNetworkAddresses()
        if (selectedIpIndex >= networkAddresses.size) selectedIpIndex = 0
    }



    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    var selectedTab by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var isCleaning by remember { mutableStateOf(false) }
    var cleaningStatus by remember { mutableStateOf("") }

    var showResultDialog by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultSummary by remember { mutableStateOf("") }
    var resultItems by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val currentPin by AuthHelper.currentPin.collectAsState()
    val castingManager = remember { CastingManager(context) }
    val discoveredDevices by castingManager.devices.collectAsState()

    DisposableEffect(Unit) {
        castingManager.startDiscovery()
        onDispose { castingManager.stopDiscovery() }
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
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Professional Header
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        if (isServerRunning) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
                    .clip(CircleShape)
                    .background(if (isServerRunning) Color(0xFF10B981) else Color(0xFFEF4444))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "File Mate",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        val activeType = networkAddresses.getOrNull(selectedIpIndex)?.type
        val connectionSubtext = when (activeType) {
            NetworkType.HOTSPOT -> "Access via Mobile Hotspot"
            NetworkType.WIFI -> "Access via local Wi-Fi"
            NetworkType.ETHERNET -> "Access via Ethernet"
            NetworkType.USB_TETHERING -> "Access via USB Tethering"
            else -> if (primaryIp == "127.0.0.1") "Offline (No Wi-Fi / Hotspot active)" else "Access via local network"
        }
        Text(
            text = if (isServerRunning) "Server active • $connectionSubtext" else "Secured file and network sharing.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isServerRunning) Color(0xFF34D399) else Color.LightGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Large Action Button
        Button(
            onClick = { if (isServerRunning) stopServer() else requestAllFilesAccessAndStart() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(64.dp)
                .shadow(if (isServerRunning) 12.dp else 0.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServerRunning) Color(0xFF1E293B) else MaterialTheme.colorScheme.primary,
                contentColor = if (isServerRunning) Color(0xFFF43F5E) else Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isServerRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isServerRunning) "Stop Sharing" else "Start Sharing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Premium Segmented Control Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tab0Bg = if (selectedTab == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
            val tab1Bg = if (selectedTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
            val tab2Bg = if (selectedTab == 2) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tab0Bg)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📡 Server",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tab1Bg)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛠️ Tools",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tab2Bg)
                    .clickable { selectedTab = 2 }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📺 Cast",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 0) {
            AnimatedVisibility(
                visible = isServerRunning,
                enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan QR Code on client device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Beautiful Card for QR Code
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            qrCodeBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.size(160.dp)
                                )
                            } ?: Text("Generating QR...", color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Connection Details Card
                    val serverUrl = "http://$primaryIp:8080"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            // Header: Label & Action Icons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SERVER ADDRESS",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            networkAddresses = NetworkUtils.getLocalNetworkAddresses()
                                            if (selectedIpIndex >= networkAddresses.size) selectedIpIndex = 0
                                            android.widget.Toast.makeText(context, "Network IP refreshed", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh IP",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("File Mate URL", serverUrl)
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Copied $serverUrl", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Copy",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Dedicated Full-Width URL Display - Never Truncated!
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("File Mate URL", serverUrl)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Copied $serverUrl", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Text(
                                    text = serverUrl,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    softWrap = true
                                )
                            }

                            if (networkAddresses.size > 1) {
                                androidx.compose.foundation.lazy.LazyRow(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(networkAddresses.size) { idx ->
                                        val info = networkAddresses[idx]
                                        val isSelected = idx == selectedIpIndex
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedIpIndex = idx },
                                            label = {
                                                Text(
                                                    "${info.displayName}: ${info.ip}",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }

                            if (networkAddresses.isEmpty()) {
                                Text(
                                    text = "⚠️ No active Wi-Fi or Hotspot. Please connect to Wi-Fi or turn on Hotspot.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            
                            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            
                            // Credentials Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Username",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "admin",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Password (PIN)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentPin,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Network Stats section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard("File Server", formatBytes(webfsTx), formatBytes(webfsRx))
                        StatCard("Proxy Server", formatBytes(proxyTx), formatBytes(proxyRx))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Proxy Guide
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "HTTP Proxy Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "To route traffic through this device, configure your client's proxy to:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Host IP",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = primaryIp,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Port",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "8081",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isServerRunning) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tap 'Start Sharing' above to generate QR code and active server address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else if (selectedTab == 1) {
            // Storage Maintenance Tools Section (Tab 1)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🛠️",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage Utilities & Cleaner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Clean empty folders & purge hidden OS junk files from Android UI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Clean Empty Folders
                        Button(
                            onClick = {
                                val sharedDir = FileServerService.sharedRoot ?: Environment.getExternalStorageDirectory()
                                coroutineScope.launch(Dispatchers.IO) {
                                    isCleaning = true
                                    cleaningStatus = "Scanning & removing empty folders..."
                                    val res = StorageMaintenanceHelper.cleanEmptyFolders(sharedDir, context)
                                    withContext(Dispatchers.Main) {
                                        isCleaning = false
                                        cleaningStatus = ""
                                        resultTitle = "🧹 Empty Folder Cleanup Results"
                                        resultSummary = if (res.removedCount > 0)
                                            "Successfully removed ${res.removedCount} empty directory(ies)."
                                        else
                                            "No empty folders were found in shared storage."
                                        resultItems = res.removedFolders
                                        showResultDialog = true
                                    }
                                }
                            },
                            enabled = !isCleaning,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text("🧹 Empty Folders", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        // Purge OS Junk Files
                        Button(
                            onClick = {
                                val sharedDir = FileServerService.sharedRoot ?: Environment.getExternalStorageDirectory()
                                coroutineScope.launch(Dispatchers.IO) {
                                    isCleaning = true
                                    cleaningStatus = "Scanning & purging OS junk files..."
                                    val res = StorageMaintenanceHelper.cleanJunkFiles(sharedDir, context)
                                    withContext(Dispatchers.Main) {
                                        isCleaning = false
                                        cleaningStatus = ""
                                        val freedStr = StorageMaintenanceHelper.formatBytes(res.freedBytes)
                                        resultTitle = "🗑️ OS Junk Purge Results"
                                        resultSummary = if (res.removedCount > 0)
                                            "Purged ${res.removedCount} junk file(s), freeing $freedStr."
                                        else
                                            "No OS junk files (.DS_Store, Thumbs.db, .tmp) were found."
                                        resultItems = res.removedFiles
                                        showResultDialog = true
                                    }
                                }
                            },
                            enabled = !isCleaning,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text("🗑️ Purge Junk", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    // In-card Live Progress Feedback
                    if (isCleaning) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
        } else if (selectedTab == 2) {
            // Casting (Tab 2)
            val activeCaster by CastingState.activeCaster.collectAsState()
            var castStatus by remember { mutableStateOf("") }
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📺", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Media Casting",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cast media to AirPlay or DLNA devices on your network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (discoveredDevices.isEmpty()) {
                        Text("Scanning for devices on network (mDNS)...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Discovered Devices:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        discoveredDevices.forEach { device ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (activeCaster?.deviceId == device.deviceId) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { CastingState.activeCaster.value = device }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(device.deviceName, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(device.deviceId, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (activeCaster?.deviceId == device.deviceId) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    if (activeCaster != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            castStatus = "Sending test image..."
                                            // Demo: Generate a byte array of the QR code to test AirPlay
                                            val stream = java.io.ByteArrayOutputStream()
                                            qrCodeBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                            val bytes = stream.toByteArray()
                                            
                                            // Send it
                                            activeCaster?.showImage("http://$primaryIp:8080/", bytes)
                                            castStatus = "Test image sent!"
                                        } catch (e: Exception) {
                                            castStatus = "Error: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test Cast")
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        activeCaster?.stop()
                                        CastingState.activeCaster.value = null
                                        castStatus = ""
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop")
                            }
                        }
                        if (castStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(castStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Detailed Cleanup Results Dialog
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

    // Storage Permission Onboarding Rationale Dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Storage Access Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "File Mate turns your device into a high-speed local file server. To host, share, and manage files over Wi-Fi and perform storage cleanups, File Mate requires All Files Access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔒 Privacy Guarantee: All file sharing and maintenance occur 100% locally on your Wi-Fi network. No external servers or cloud uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        manageStorageLauncher.launch(intent)
                    }
                ) {
                    Text("Continue to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Not Now")
                }
            }
        )
    }
    }
}

@Composable
fun StatCard(title: String, txLine: String, rxLine: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.width(155.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "↑ ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                Text(text = txLine, style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "↓ ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                Text(text = rxLine, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
        }
    }
}
