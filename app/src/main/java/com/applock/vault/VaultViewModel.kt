package com.applock.vault

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applock.core.Graph
import com.applock.core.database.VaultItemEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel : ViewModel() {

    private val repository = Graph.vaultRepository

    val items: StateFlow<List<VaultItemEntity>> = repository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Files currently being encrypted; >0 shows the import progress bar. */
    val importsInFlight = MutableStateFlow(0)

    /** Imported source URIs awaiting the delete-originals decision (FR-114). */
    val pendingOriginals = MutableStateFlow<List<Uri>>(emptyList())

    private val messageChannel = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = messageChannel.receiveAsFlow()

    private val thumbnailCache = LruCache<Long, Bitmap>(48)

    sealed interface Message {
        data class ImportFinished(val imported: Int, val failed: Int) : Message
        data class OriginalsDeleted(val deleted: Int, val failed: Int) : Message
        data class ExportFinished(val success: Boolean) : Message
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importsInFlight.value += uris.size
            val imported = mutableListOf<Uri>()
            var failed = 0
            try {
                uris.forEach { uri ->
                    repository.import(uri)
                        .onSuccess { imported += uri }
                        .onFailure { failed++ }
                    importsInFlight.value -= 1
                }
            } finally {
                // A cancelled/crashed loop must not leave the bar stuck.
                importsInFlight.value = 0
            }
            messageChannel.send(Message.ImportFinished(imported.size, failed))
            if (imported.isNotEmpty()) pendingOriginals.value = imported
        }
    }

    /** FR-114 — user chose whether the plaintext originals should go. */
    fun resolveOriginals(deleteThem: Boolean) {
        val uris = pendingOriginals.value
        pendingOriginals.value = emptyList()
        if (!deleteThem || uris.isEmpty()) return
        viewModelScope.launch {
            var deleted = 0
            uris.forEach { if (repository.deleteOriginal(it)) deleted++ }
            messageChannel.send(Message.OriginalsDeleted(deleted, uris.size - deleted))
        }
    }

    fun delete(item: VaultItemEntity) {
        thumbnailCache.remove(item.id)
        viewModelScope.launch { repository.delete(item) }
    }

    fun export(item: VaultItemEntity, destination: Uri) {
        viewModelScope.launch {
            val result = repository.exportTo(item, destination)
            messageChannel.send(Message.ExportFinished(result.isSuccess))
        }
    }

    suspend fun thumbnail(item: VaultItemEntity): Bitmap? =
        thumbnailCache.get(item.id)
            ?: repository.decodeImage(item, maxDimension = 256)
                ?.also { thumbnailCache.put(item.id, it) }

    suspend fun preview(item: VaultItemEntity): Bitmap? = repository.decodeImage(item)
}
