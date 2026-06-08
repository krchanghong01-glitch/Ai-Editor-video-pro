package com.example.editor

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class EditorViewModel : ViewModel() {

    private val _canvasRatio = MutableLiveData<String>("16:9")
    val canvasRatio: LiveData<String> = _canvasRatio

    private val _tracks = MutableLiveData<List<Track>>()
    val tracks: LiveData<List<Track>> = _tracks

    private val _currentTimeMs = MutableLiveData<Long>(0L)
    val currentTimeMs: LiveData<Long> = _currentTimeMs

    private val _totalDurationMs = MutableLiveData<Long>(30000L) // Default 30s timeline
    val totalDurationMs: LiveData<Long> = _totalDurationMs

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _selectedClip = MutableLiveData<Clip?>()
    val selectedClip: LiveData<Clip?> = _selectedClip

    val undoManager = UndoManager()

    init {
        initializeDefaultTracks()
    }

    fun initializeDefaultTracks() {
        val defaultTracks = listOf(
            Track(trackLabel = "V1"), // Main Video Track
            Track(trackLabel = "V2"), // Overlay Track (Stickers, AI Generated images)
            Track(trackLabel = "A1"), // Background Music Track
            Track(trackLabel = "A2")  // Custom Voiceover Recording Track
        )
        _tracks.value = defaultTracks
    }

    fun setCanvasRatio(ratio: String) {
        _canvasRatio.value = ratio
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun updatePlayhead(timeMs: Long) {
        val total = _totalDurationMs.value ?: 30000L
        if (timeMs in 0..total) {
            _currentTimeMs.value = timeMs
        }
    }

    fun setSelectedClip(clip: Clip?) {
        _selectedClip.value = clip
    }

    // JSON Project Serialization and Saving
    fun saveProject(context: Context, isAutoSave: Boolean = false): Boolean {
        return try {
            val projectState = ProjectState(
                ratio = _canvasRatio.value ?: "16:9",
                tracks = _tracks.value ?: emptyList(),
                durationMs = _totalDurationMs.value ?: 30000L
            )
            val json = Gson().toJson(projectState)
            val fileName = if (isAutoSave) "autosave_project.json" else "user_project.json"
            val file = File(context.filesDir, fileName)
            file.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadProject(context: Context, isAutoSave: Boolean = false): Boolean {
        return try {
            val fileName = if (isAutoSave) "autosave_project.json" else "user_project.json"
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return false

            val json = file.readText()
            val stateType = object : TypeToken<ProjectState>() {}.type
            val loadedState: ProjectState = Gson().fromJson(json, stateType)

            _canvasRatio.value = loadedState.ratio
            _tracks.value = loadedState.tracks
            _totalDurationMs.value = loadedState.durationMs
            _selectedClip.value = null
            undoManager.clear()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Timeline Helper Action Blocks
    fun addClipToTrack(label: String, clip: Clip) {
        val currentList = _tracks.value ?: return
        val targetTrack = currentList.find { it.trackLabel == label } ?: return

        undoManager.executeCommand(AddClipCommand(targetTrack, clip) {
            _tracks.value = currentList // trigger observers update
        })
    }

    fun removeClipFromTrack(label: String, clip: Clip) {
        val currentList = _tracks.value ?: return
        val targetTrack = currentList.find { it.trackLabel == label } ?: return

        undoManager.executeCommand(DeleteClipCommand(targetTrack, clip) {
            _tracks.value = currentList
            if (_selectedClip.value?.id == clip.id) {
                _selectedClip.value = null
            }
        })
    }

    fun applyClipChange(clip: Clip, updatedClip: Clip) {
        val currentList = _tracks.value ?: return
        undoManager.executeCommand(UpdateClipCommand(clip, clip.copy(), updatedClip) {
            _tracks.value = currentList
            _selectedClip.value = clip // update binding selected clip state
        })
    }

    fun performUndo() {
        if (undoManager.undo()) {
            _tracks.value = _tracks.value // trigger observers
        }
    }

    fun performRedo() {
        if (undoManager.redo()) {
            _tracks.value = _tracks.value // trigger observers
        }
    }

    // Helper data representation block
    private data class ProjectState(
        val ratio: String,
        val tracks: List<Track>,
        val durationMs: Long
    )
}
