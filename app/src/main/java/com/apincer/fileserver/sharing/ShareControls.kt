package com.apincer.fileserver.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apincer.fileserver.FileServerService
import com.apincer.fileserver.generateQrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ShareControls(currentFolder: File, selectedPaths: Set<String>, root: File, primaryIp: String, onStartServer: () -> Unit, onPresent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by FileServerService.sharesRevision.collectAsState()
    var sessions by remember { mutableStateOf(emptyList<ShareSession>()) }
    var configure by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ShareMode.DOWNLOAD) }
    var minutes by remember { mutableStateOf(60) }
    var creating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var createdLink by remember { mutableStateOf<String?>(null) }
    var activeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(revision, activeDialog) { sessions = withContext(Dispatchers.IO) { FileServerService.shares.list() } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !creating, onClick = { selectedOnly = selectedPaths.isNotEmpty(); mode = ShareMode.DOWNLOAD; configure = true }, modifier = Modifier.weight(1f)) {
                Text(if (selectedPaths.isEmpty()) "Share folder" else "Share selected (${selectedPaths.size})")
            }
            OutlinedButton(onClick = onPresent) { Text("Present photos") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { activeDialog = true }) { Text("Active shares (${sessions.size})") }
            if (creating) Text("Starting sharing…", modifier = Modifier.padding(12.dp))
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
    if (configure) AlertDialog(
        onDismissRequest = { configure = false },
        title = { Text(if (selectedOnly) "Share selected files" else "Share ${currentFolder.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Anyone with this link can use the permissions you choose. Shared photos can be saved.")
                Spacer(Modifier.height(12.dp))
                val choices = if (selectedOnly) listOf(ShareMode.DOWNLOAD) else ShareMode.entries
                choices.forEach { choice ->
                    Row { RadioButton(selected = mode == choice, onClick = { mode = choice }); TextButton(onClick = { mode = choice }) { Text(when (choice) {
                        ShareMode.DOWNLOAD -> "Download files"
                        ShareMode.UPLOAD -> "Receive files only"
                        ShareMode.DOWNLOAD_AND_UPLOAD -> "Download and receive files"
                    }) } }
                }
                Text("Expires after")
                Row { listOf(15 to "15 min", 60 to "1 hour", 1440 to "24 hours").forEach { (value, label) ->
                    FilterChip(selected = minutes == value, onClick = { minutes = value }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                } }
            }
        },
        confirmButton = { TextButton(onClick = {
            configure = false; creating = true; status = null
            val folder = currentFolder
            val files = selectedPaths.map(::File)
            val requestedSelectedOnly = selectedOnly
            val requestedMode = mode
            val requestedMinutes = minutes
            scope.launch {
                try {
                    if (!FileServerService.isRunning) {
                        onStartServer()
                        withTimeout(45_000) { FileServerService.isRunningFlow.first { it } }
                    }
                    val share = withContext(Dispatchers.IO) {
                        check(FileServerService.isRunning) { "Start the server to share files" }
                        FileServerService.shares.create(root, if (requestedSelectedOnly) null else folder, if (requestedSelectedOnly) files else emptyList(), requestedMode, requestedMinutes)
                    }
                    FileServerService.sharesChanged()
                    createdLink = "http://$primaryIp:8080${share.urlPath}"
                } catch (e: Exception) { status = e.message ?: "Could not start sharing. Check storage and server access." }
                finally { creating = false }
            }
        }) { Text("Create share link") } },
        dismissButton = { TextButton(onClick = { configure = false }) { Text("Cancel") } }
    )
    createdLink?.let { link ->
        val qr = remember(link) { generateQrCode(link) }
        AlertDialog(onDismissRequest = { createdLink = null }, title = { Text("Share link") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                qr?.let { Image(it.asImageBitmap(), contentDescription = "Share link QR code", modifier = Modifier.fillMaxWidth().height(230.dp)) }
                Text(link, modifier = Modifier.fillMaxWidth())
                Text("Keep this link private. Stopping the server ends every share.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }, "Share link"))
        }) { Text("Send link") } }, dismissButton = { TextButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ShareMate link", link))
            status = "Link copied"; createdLink = null
        }) { Text("Copy") } })
    }
    if (activeDialog) AlertDialog(onDismissRequest = { activeDialog = false }, title = { Text("Active shares") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (sessions.isEmpty()) Text("No active shares. Expired links are removed automatically.")
            sessions.forEach { session ->
                Text(session.label)
                Text("${session.mode.name.replace('_', ' ')} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.expiresAtMillis))}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { FileServerService.shares.revoke(session.id); FileServerService.sharesChanged() }) { Text("Revoke") }
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = { activeDialog = false }) { Text("Close") } })
}
