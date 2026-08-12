package com.applock.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.applock.domain.VaultFileTypes
import com.applock.security.EncryptedFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Encrypted media vault (FR-106..FR-119). Payloads are AES-256-GCM
 * [EncryptedFileStore] blobs named by random UUID; the human-readable index
 * (names, types, dates) lives only in the SQLCipher database. Import and
 * export stream through memory — plaintext never lands on disk on our side.
 */
class VaultRepository(
    private val context: Context,
    private val vaultItemDao: VaultItemDao,
    private val fileStore: EncryptedFileStore,
) {

    val items: Flow<List<VaultItemEntity>> = vaultItemDao.observeAll()

    /**
     * Copies one SAF document into the vault (FR-109..112). The blob is
     * fully written before the index row exists, so a crash mid-import
     * leaves an orphaned blob at worst, never a dangling index entry.
     */
    suspend fun import(uri: Uri): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        val blobName = UUID.randomUUID().toString()
        runCatching {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(uri) ?: DEFAULT_NAME
            val mimeType = resolver.getType(uri) ?: VaultFileTypes.mimeForName(displayName)

            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("source not readable: $uri")
            val copied = input.use { source ->
                fileStore.openOutput(VAULT_DIR, blobName).use { sink ->
                    source.copyTo(sink)
                }
            }

            val item = VaultItemEntity(
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = copied,
                fileName = blobName,
            )
            item.copy(id = vaultItemDao.insert(item))
        }.onFailure {
            Log.w(TAG, "Import failed for $uri", it)
            fileStore.delete(VAULT_DIR, blobName)
        }
    }

    /**
     * FR-114: removes the original document after import. Works only for
     * providers that support deletion; failure is reported, never fatal.
     */
    suspend fun deleteOriginal(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete original $uri", e)
            false
        }
    }

    /** FR-115: drops the index row and the ciphertext blob. */
    suspend fun delete(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        vaultItemDao.delete(item.id)
        fileStore.delete(VAULT_DIR, item.fileName)
    }

    /** FR-119: decrypts the item into a user-chosen SAF destination. */
    suspend fun exportTo(item: VaultItemEntity, destination: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = context.contentResolver.openOutputStream(destination)
                    ?: throw IllegalStateException("destination not writable")
                output.use { sink ->
                    fileStore.openInput(VAULT_DIR, item.fileName).use { source ->
                        source.copyTo(sink)
                    }
                }
                Unit
            }.onFailure {
                // The SAF picker already created the destination document; a
                // failed decrypt (e.g. corrupt blob) would otherwise leave a
                // half-written/empty file masquerading as a real export.
                Log.w(TAG, "Export failed for ${item.displayName} — removing partial file", it)
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            }
        }

    /** FR-118: in-memory preview decode for images; null for other types. */
    suspend fun decodeImage(item: VaultItemEntity, maxDimension: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            if (VaultFileTypes.categoryOf(item.mimeType) != VaultFileTypes.Category.IMAGE) {
                return@withContext null
            }
            fileStore.decodeBitmap(VAULT_DIR, item.fileName, maxDimension)
        }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private companion object {
        const val TAG = "VaultRepository"
        const val VAULT_DIR = "vault"
        const val DEFAULT_NAME = "imported-file"
    }
}
