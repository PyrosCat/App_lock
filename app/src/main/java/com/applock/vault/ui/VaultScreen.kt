package com.applock.vault.ui

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applock.R
import com.applock.core.database.VaultItemEntity
import com.applock.ui.SelfLock
import com.applock.vault.VaultFileTypes
import com.applock.vault.VaultViewModel
import java.text.DateFormat
import java.util.Date

/** Encrypted vault browser (FR-106..119). Reached only from behind the self-gate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val importsInFlight by viewModel.importsInFlight.collectAsState()
    val pendingOriginals by viewModel.pendingOriginals.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var previewItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<VaultItemEntity?>(null) }
    var exportCandidate by remember { mutableStateOf<VaultItemEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> -> viewModel.import(uris) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destination: Uri? ->
        val item = exportCandidate
        exportCandidate = null
        if (destination != null && item != null) viewModel.export(item, destination)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is VaultViewModel.Message.ImportFinished -> buildString {
                    append(context.getString(R.string.vault_import_done, message.imported))
                    if (message.failed > 0) {
                        append(" · ")
                        append(context.getString(R.string.vault_import_failed, message.failed))
                    }
                }
                is VaultViewModel.Message.OriginalsDeleted -> buildString {
                    append(context.getString(R.string.vault_originals_deleted, message.deleted))
                    if (message.failed > 0) {
                        append(" · ")
                        append(
                            context.getString(
                                R.string.vault_originals_delete_failed,
                                message.failed,
                            )
                        )
                    }
                }
                is VaultViewModel.Message.ExportFinished -> context.getString(
                    if (message.success) R.string.vault_export_done
                    else R.string.vault_export_failed
                )
            }
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // SAF picker backgrounds us; don't treat that as leaving the app.
                SelfLock.suppressNextBackground = true
                importLauncher.launch(arrayOf("*/*"))
            }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.vault_add_files),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (importsInFlight > 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.vault_importing, importsInFlight),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (items.isEmpty() && importsInFlight == 0) {
                EmptyVaultHint()
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item ->
                        VaultItemRow(
                            item = item,
                            viewModel = viewModel,
                            onClick = { previewItem = item },
                            onExport = {
                                exportCandidate = item
                                SelfLock.suppressNextBackground = true
                                exportLauncher.launch(item.displayName)
                            },
                            onDelete = { deleteCandidate = item },
                        )
                    }
                }
            }
        }
    }

    if (pendingOriginals.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.resolveOriginals(deleteThem = false) },
            title = { Text(stringResource(R.string.vault_delete_originals_title)) },
            text = { Text(stringResource(R.string.vault_delete_originals_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveOriginals(deleteThem = true) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveOriginals(deleteThem = false) }) {
                    Text(stringResource(R.string.vault_keep_originals))
                }
            },
        )
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            text = {
                Text(stringResource(R.string.vault_delete_item_confirm, item.displayName))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    deleteCandidate = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    previewItem?.let { item ->
        PreviewDialog(item = item, viewModel = viewModel, onClose = { previewItem = null })
    }
}

@Composable
private fun EmptyVaultHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.vault_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.vault_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VaultItemRow(
    item: VaultItemEntity,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = item.id) {
        value = viewModel.thumbnail(item)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    categoryIcon(item.mimeType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Formatter.formatShortFileSize(context, item.sizeBytes) + " · " +
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.importedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onExport) {
            Icon(
                Icons.Filled.SaveAlt,
                contentDescription = stringResource(R.string.vault_export),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
}

@Composable
private fun PreviewDialog(
    item: VaultItemEntity,
    viewModel: VaultViewModel,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                if (VaultFileTypes.categoryOf(item.mimeType) == VaultFileTypes.Category.IMAGE) {
                    // Three states, not a nullable bitmap: a decode that fails
                    // (corrupt/undecodable blob) resolves to Failed rather than
                    // leaving the spinner up forever.
                    val state by produceState<PreviewState>(PreviewState.Loading, item.id) {
                        value = viewModel.preview(item)
                            ?.let { PreviewState.Ready(it) }
                            ?: PreviewState.Failed
                    }
                    when (val s = state) {
                        PreviewState.Loading -> LinearProgressIndicator()
                        is PreviewState.Ready -> Image(
                            bitmap = s.bitmap.asImageBitmap(),
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PreviewState.Failed -> Text(
                            stringResource(R.string.vault_preview_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.vault_preview_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Ready(val bitmap: Bitmap) : PreviewState
    data object Failed : PreviewState
}

private fun categoryIcon(mimeType: String): ImageVector =
    when (VaultFileTypes.categoryOf(mimeType)) {
        VaultFileTypes.Category.IMAGE -> Icons.Filled.Image
        VaultFileTypes.Category.VIDEO -> Icons.Filled.Movie
        VaultFileTypes.Category.AUDIO -> Icons.Filled.MusicNote
        VaultFileTypes.Category.DOCUMENT -> Icons.Filled.Description
        VaultFileTypes.Category.ARCHIVE -> Icons.Filled.FolderZip
        VaultFileTypes.Category.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
