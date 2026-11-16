package com.playora.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playora.app.data.MediaFile
import com.playora.app.data.MediaRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the scanned library so it survives navigation and rotation; scanning happens off the main thread. */
class LibVM(app: Application) : AndroidViewModel(app) {
    var videos by mutableStateOf<List<MediaFile>>(emptyList()); private set
    var audio by mutableStateOf<List<MediaFile>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val (v, a) = withContext(Dispatchers.IO) { MediaRepo.loadVideos(ctx) to MediaRepo.loadAudio(ctx) }
            videos = v; audio = a; loading = false; loaded = true
        }
    }
}
