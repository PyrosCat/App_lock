package com.applock.privacy.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applock.core.database.IntruderEventDao
import com.applock.core.database.IntruderEventEntity
import com.applock.privacy.IntruderCaptureManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntruderLogViewModel @Inject constructor(
    private val dao: IntruderEventDao,
    private val captureManager: IntruderCaptureManager,
) : ViewModel() {

    private val photoCache = LruCache<Long, Bitmap>(16)

    val events: StateFlow<List<IntruderEventEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun photo(event: IntruderEventEntity): Bitmap? {
        val fileName = event.photoFileName ?: return null
        return photoCache.get(event.id)
            ?: captureManager.decodePhoto(fileName, maxDimension = 512)
                ?.also { photoCache.put(event.id, it) }
    }

    fun delete(event: IntruderEventEntity) {
        photoCache.remove(event.id)
        viewModelScope.launch {
            dao.delete(event.id)
            event.photoFileName?.let(captureManager::deletePhoto)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            events.value.forEach { it.photoFileName?.let(captureManager::deletePhoto) }
            dao.deleteAll()
            photoCache.evictAll()
        }
    }
}
