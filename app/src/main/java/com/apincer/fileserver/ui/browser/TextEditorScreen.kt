package com.apincer.fileserver.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    fileItem: FileItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var initialText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showExitWarning by remember { mutableStateOf(false) }

    val isModified = textFieldValue.text != initialText

    fun handleRequestClose() {
        if (isModified) {
            showExitWarning = true
        } else {
            onClose()
        }
    }

    LaunchedEffect(fileItem) {
        withContext(Dispatchers.IO) {
            try {
                val content = fileItem.file.readText()
                withContext(Dispatchers.Main) {
                    initialText = content
                    textFieldValue = TextFieldValue(content)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            }
        }
    }

    if (showExitWarning) {
        AlertDialog(
            onDismissRequest = { showExitWarning = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved modifications in '${fileItem.name}'. Do you want to save before leaving?") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            withContext(Dispatchers.IO) {
                                fileItem.file.writeText(textFieldValue.text)
                            }
                            showExitWarning = false
                            onClose()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    }
                }) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showExitWarning = false
                        onClose()
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showExitWarning = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Dialog(
        onDismissRequest = { handleRequestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = fileItem.name, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { handleRequestClose() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // Copy Button
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(fileItem.name, textFieldValue.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }

                        // Paste Button
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
                                val pasteText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                // Insert at cursor or replace selection
                                val currentSelection = textFieldValue.selection
                                val newText = textFieldValue.text.replaceRange(
                                    currentSelection.start,
                                    currentSelection.end,
                                    pasteText
                                )
                                textFieldValue = textFieldValue.copy(
                                    text = newText,
                                    selection = androidx.compose.ui.text.TextRange(currentSelection.start + pasteText.length)
                                )
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }

                        // Save Button
                        IconButton(
                            onClick = {
                                if (!isModified) return@IconButton
                                coroutineScope.launch {
                                    isSaving = true
                                    try {
                                        withContext(Dispatchers.IO) {
                                            fileItem.file.writeText(textFieldValue.text)
                                        }
                                        initialText = textFieldValue.text
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = isModified && !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Save, 
                                    contentDescription = "Save",
                                    tint = if (isModified) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
            }
        }
    }
}
