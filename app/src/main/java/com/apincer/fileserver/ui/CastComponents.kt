package com.apincer.fileserver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.apincer.fileserver.cast.CastingState
import com.apincer.fileserver.cast.TvCaster

@Composable
fun GoogleCastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.size(48.dp),
        factory = { context ->
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(
                context, 
                androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
            )
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
            }
        }
    )
}

@Composable
fun UnifiedCastButton(modifier: Modifier = Modifier, iconTint: Color = MaterialTheme.colorScheme.primary) {
    val activeCaster by CastingState.activeCaster.collectAsState()
    val isTransferring by CastingState.isTransferring.collectAsState()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = { CastingState.showCastSheet.value = true }) {
            Icon(
                imageVector = if (activeCaster != null) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = "Cast",
                tint = if (activeCaster != null) MaterialTheme.colorScheme.primary else iconTint
            )
        }
        if (isTransferring) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedCastSheet(
    discoveredDevices: List<TvCaster>,
    onDismiss: () -> Unit
) {
    val activeCaster by CastingState.activeCaster.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Cast to Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Chromecast Section
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Google Cast Devices")
                }
                GoogleCastButton()
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // AirPlay / DLNA Section
            Text("AirPlay & DLNA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            if (discoveredDevices.isEmpty()) {
                Text("Searching for devices on local network...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(discoveredDevices) { device ->
                        val isSelected = activeCaster?.deviceId == device.deviceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) CastingState.activeCaster.value = null
                                    else CastingState.activeCaster.value = device
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = device.deviceName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
