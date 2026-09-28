package com.apincer.fileserver.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
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

    // Find bar state
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchIndex by remember { mutableStateOf(0) }
    val findFocusRequester = remember { FocusRequester() }

    val isModified = textFieldValue.text != initialText

    // Compute match positions whenever text or query changes
    val matchRanges: List<IntRange> = remember(textFieldValue.text, findQuery) {
        if (findQuery.isBlank()) emptyList()
        else {
            val ranges = mutableListOf<IntRange>()
            var idx = 0
            val lower = textFieldValue.text.lowercase()
            val qLower = findQuery.lowercase()
            while (idx <= lower.length - qLower.length) {
                val found = lower.indexOf(qLower, idx)
                if (found < 0) break
                ranges.add(found until found + qLower.length)
                idx = found + 1
            }
            ranges
        }
    }

    // Clamp match index when matches change
    LaunchedEffect(matchRanges.size) {
        if (matchRanges.isNotEmpty()) {
            findMatchIndex = findMatchIndex.coerceIn(0, matchRanges.size - 1)
        }
    }

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
                Column {
                    TopAppBar(
                        title = { Text(text = fileItem.name, maxLines = 1) },
                        navigationIcon = {
                            IconButton(onClick = { handleRequestClose() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            // Find Toggle
                            IconButton(onClick = {
                                showFindBar = !showFindBar
                                if (!showFindBar) findQuery = ""
                            }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Find",
                                    tint = if (showFindBar) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
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

                    // ── Find Bar ───────────────────────────────────────────────
                    AnimatedVisibility(
                        visible = showFindBar,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        LaunchedEffect(showFindBar) {
                            if (showFindBar) findFocusRequester.requestFocus()
                        }
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (findQuery.isEmpty()) {
                                        Text(
                                            "Find in file…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    BasicTextField(
                                        value = findQuery,
                                        onValueChange = {
                                            findQuery = it
                                            findMatchIndex = 0
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(findFocusRequester)
                                    )
                                }
                                // Match count indicator
                                if (findQuery.isNotBlank()) {
                                    Text(
                                        text = if (matchRanges.isEmpty()) "0/0"
                                               else "${findMatchIndex + 1}/${matchRanges.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                                // Prev
                                IconButton(
                                    onClick = {
                                        if (matchRanges.isNotEmpty()) {
                                            findMatchIndex = (findMatchIndex - 1 + matchRanges.size) % matchRanges.size
                                        }
                                    },
                                    enabled = matchRanges.isNotEmpty(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(18.dp))
                                }
                                // Next
                                IconButton(
                                    onClick = {
                                        if (matchRanges.isNotEmpty()) {
                                            findMatchIndex = (findMatchIndex + 1) % matchRanges.size
                                        }
                                    },
                                    enabled = matchRanges.isNotEmpty(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(18.dp))
                                }
                                // Close find bar
                                IconButton(
                                    onClick = { showFindBar = false; findQuery = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close find", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Build annotated text with find highlights
                val annotated = remember(textFieldValue.text, matchRanges, findMatchIndex) {
                    buildAnnotatedString {
                        val text = textFieldValue.text
                        var last = 0
                        matchRanges.forEachIndexed { idx, range ->
                            append(text.substring(last, range.first))
                            withStyle(
                                SpanStyle(
                                    background = if (idx == findMatchIndex)
                                        Color(0xFFFFD54F) // active match — amber
                                    else
                                        Color(0x66FFD54F)  // other matches — dim amber
                                )
                            ) {
                                append(text.substring(range.first, range.last + 1))
                            }
                            last = range.last + 1
                        }
                        if (last < text.length) append(text.substring(last))
                    }
                }

                TextField(
                    value = if (findQuery.isBlank()) textFieldValue
                            else textFieldValue.copy(annotatedString = annotated),
                    onValueChange = { newVal ->
                        // Only allow text edits when find is hidden or query is cleared
                        textFieldValue = newVal.copy(annotatedString = buildAnnotatedString { append(newVal.text) })
                        findMatchIndex = 0
                    },
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
