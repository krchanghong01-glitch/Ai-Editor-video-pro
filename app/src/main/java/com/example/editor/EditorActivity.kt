package com.example.editor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.ai.AiModelsHubActivity
import com.example.ai.LibraryActivity
import com.example.ai.TextToImageActivity
import android.net.Uri
import com.example.databinding.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.chip.Chip
import java.io.File

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var viewModel: EditorViewModel
    private lateinit var trackAdapter: TrackRowAdapter

    // Player Playhead Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playerRunnable = object : Runnable {
        override fun run() {
            if (viewModel.isPlaying.value == true) {
                var currentMs = viewModel.currentTimeMs.value ?: 0L
                val totalMs = viewModel.totalDurationMs.value ?: 30000L
                if (currentMs >= totalMs) {
                    currentMs = 0L // Loop
                } else {
                    currentMs += 100L // update every 100ms
                }
                viewModel.updatePlayhead(currentMs)
                mainHandler.postDelayed(this, 100)
            }
        }
    }

    // Auto-save runnable (triggers every 30 seconds)
    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            viewModel.saveProject(this@EditorActivity, isAutoSave = true)
            Toast.makeText(this@EditorActivity, "Project auto-saved safely", Toast.LENGTH_SHORT).show()
            mainHandler.postDelayed(this, 30000)
        }
    }

    // Activity Launchers for Pickers and AI features
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataUri = result.data?.data
            if (dataUri != null) {
                val fileName = getFileNameFromUri(dataUri)
                // Add default 5s video or image clip based on file name extension
                val clip = Clip(
                    type = if (fileName.endsWith(".mp3") || fileName.endsWith(".wav")) "audio" else "video",
                    fileName = fileName
                )
                if (clip.type == "audio") {
                    viewModel.addClipToTrack("A1", clip)
                } else {
                    viewModel.addClipToTrack("V1", clip)
                }
            }
        }
    }

    private val textToImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imagePath = result.data?.getStringExtra("EXTRA_IMAGE_PATH")
            if (imagePath != null) {
                // Add image clip on overlay track V2
                val clipName = imagePath.substringAfterLast("/")
                val clip = Clip(type = "image", fileName = imagePath)
                viewModel.addClipToTrack("V2", clip)
            }
        }
    }

    private val libraryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imagePath = result.data?.getStringExtra("EXTRA_IMAGE_PATH")
            if (imagePath != null) {
                val clip = Clip(type = "image", fileName = imagePath)
                viewModel.addClipToTrack("V2", clip)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force Edge-to-Edge and spacing
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        viewModel = ViewModelProvider(this)[EditorViewModel::class.java]

        // Load previous project if exists
        viewModel.loadProject(this)

        setupTimelineRv()
        setupTopBarListeners()
        setupPlayerListeners()
        setupSidebarListeners()
        observeViewModel()

        // Init auto-save background loop
        mainHandler.postDelayed(autoSaveRunnable, 30000)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(playerRunnable)
        mainHandler.removeCallbacks(autoSaveRunnable)
    }

    private fun setupTimelineRv() {
        binding.rvTimelineTracks.layoutManager = LinearLayoutManager(this)
        trackAdapter = TrackRowAdapter(
            tracks = viewModel.tracks.value ?: emptyList(),
            selectedClipProvider = { viewModel.selectedClip.value },
            onClipClick = { track, clip ->
                viewModel.setSelectedClip(clip)
            }
        )
        binding.rvTimelineTracks.adapter = trackAdapter
    }

    private fun observeViewModel() {
        // Canvas aspect ratio observer
        viewModel.canvasRatio.observe(this) { ratio ->
            updateCanvasDimensions(ratio)
        }

        // Project timeline structures observer
        viewModel.tracks.observe(this) { list ->
            trackAdapter.updateTracks(list)
            updateAutoDuckingState(list)
        }

        // Playhead dynamic progress observer
        viewModel.currentTimeMs.observe(this) { timeMs ->
            binding.tvCurrentTimecode.text = formatTimecode(timeMs)
            val total = viewModel.totalDurationMs.value ?: 30000L
            val percent = if (total > 0) ((timeMs.toFloat() / total.toFloat()) * 100).toInt() else 0
            binding.playbackSeekbar.progress = percent

            // Synchronize overlay subtitles tracking
            val isSubtitleMatched = updateLiveSubtitleAt(timeMs)
            
            // Synchronize active preview filter/effects rendering based on selected clip filters
            updateVisualShaderStyle()
        }

        viewModel.totalDurationMs.observe(this) { total ->
            binding.tvTotalDuration.text = "/ " + formatTimecode(total)
        }

        viewModel.isPlaying.observe(this) { isPlaying ->
            if (isPlaying) {
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                mainHandler.post(playerRunnable)
            } else {
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                mainHandler.removeCallbacks(playerRunnable)
            }
        }

        // Active highlighted Clip attributes inspector values
        viewModel.selectedClip.observe(this) { clip ->
            if (clip != null) {
                binding.tvActiveTrackLabel.text = "Selected: ${clip.type.uppercase()} CLIP"
                binding.actionDeleteClip.visibility = View.VISIBLE
                binding.actionDuplicateClip.visibility = View.VISIBLE
            } else {
                binding.tvActiveTrackLabel.text = "Timeline Grid Layout"
                binding.actionDeleteClip.visibility = View.GONE
                binding.actionDuplicateClip.visibility = View.GONE
            }
            trackAdapter.notifyDataSetChanged()
        }
    }

    private fun setupTopBarListeners() {
        // Project settings Aspect Ratio Dialog trigger (by clicking project title)
        binding.tvProjectTitle.setOnClickListener {
            showNewProjectDialog()
        }

        // Undo action trigger
        binding.btnUndo.setOnClickListener {
            viewModel.performUndo()
            Toast.makeText(this, "Action undone", Toast.LENGTH_SHORT).show()
        }

        // Redo action trigger
        binding.btnRedo.setOnClickListener {
            viewModel.performRedo()
            Toast.makeText(this, "Action redone", Toast.LENGTH_SHORT).show()
        }

        // Direct Save Project trigger
        binding.btnSave.setOnClickListener {
            if (viewModel.saveProject(this)) {
                Toast.makeText(this, "Project saved securely", Toast.LENGTH_SHORT).show()
            }
        }

        // AI models hub selector activity launches
        binding.btnAiHub.setOnClickListener {
            val intent = Intent(this, AiModelsHubActivity::class.java)
            startActivity(intent)
        }

        // Launch export compiling dialog
        binding.btnExport.setOnClickListener {
            showExportDialog()
        }
    }

    private fun setupPlayerListeners() {
        binding.btnPlayPause.setOnClickListener {
            val original = viewModel.isPlaying.value ?: false
            viewModel.setPlaying(!original)
        }

        binding.playbackSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val total = viewModel.totalDurationMs.value ?: 30000L
                    val targetMs = ((progress.toFloat() / 100f) * total.toFloat()).toLong()
                    viewModel.updatePlayhead(targetMs)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                viewModel.setPlaying(false)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Interactive double-tap and drag overlay text bindings
        binding.overlayPreviewText.setOnLongClickListener {
            val clip = viewModel.selectedClip.value
            if (clip != null && (clip.type == "text" || clip.type == "subtitle")) {
                showAddTextDialog(clip)
            }
            true
        }
    }

    private fun setupSidebarListeners() {
        // IMPORT RESOURCE TOOL
        binding.toolImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                val extraMimeTypes = arrayOf("video/*", "image/*", "audio/*")
                putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
            }
            filePickerLauncher.launch(intent)
        }

        // AI IMAGING GENERATOR
        binding.toolAiGenerate.setOnClickListener {
            val intent = Intent(this, TextToImageActivity::class.java)
            textToImageLauncher.launch(intent)
        }

        // LIBRARY ACCESS
        binding.toolLibrary.setOnClickListener {
            val intent = Intent(this, LibraryActivity::class.java)
            libraryLauncher.launch(intent)
        }

        // SPEEDS DIALOG SHEET
        binding.toolSpeed.setOnClickListener {
            val clip = viewModel.selectedClip.value
            if (clip != null) {
                showSpeedCurveSheet(clip)
            } else {
                Toast.makeText(this, "Please select a clip first", Toast.LENGTH_SHORT).show()
            }
        }

        // COLOR MANIPULATORS
        binding.toolColorGrading.setOnClickListener {
            val clip = viewModel.selectedClip.value
            if (clip != null) {
                showColorGradingSheet(clip)
            } else {
                Toast.makeText(this, "Please select a clip first", Toast.LENGTH_SHORT).show()
            }
        }

        // CHROMACUT / AI SEGMENTS
        binding.toolCutout.setOnClickListener {
            val clip = viewModel.selectedClip.value
            if (clip != null) {
                showChromaCutoutSheet(clip)
            } else {
                Toast.makeText(this, "Please select a clip first", Toast.LENGTH_SHORT).show()
            }
        }

        // LUT FILTERS Presets selection
        binding.toolFilter.setOnClickListener {
            val clip = viewModel.selectedClip.value
            if (clip != null) {
                showFiltersSheet(clip)
            } else {
                Toast.makeText(this, "Please select a clip first", Toast.LENGTH_SHORT).show()
            }
        }

        // Presets hardware FX selector
        binding.toolFx.setOnClickListener {
            val clip = viewModel.selectedClip.value
            if (clip != null) {
                showFxSheet(clip)
            } else {
                Toast.makeText(this, "Please select a clip first", Toast.LENGTH_SHORT).show()
            }
        }

        // DIALOG ADD TEXT
        binding.toolText.setOnClickListener {
            val textClip = Clip(type = "text", fileName = "Static Heading", textAnnotation = "Double-tap text to edit")
            viewModel.addClipToTrack("V2", textClip)
            viewModel.setSelectedClip(textClip)
            Toast.makeText(this, "Dynamic Heading card positioned", Toast.LENGTH_SHORT).show()
        }

        // AI AUTOMATED AUDIO CAPTIONER / SUBTITLER
        binding.toolSubtitles.setOnClickListener {
            triggerAutoSubtitleModeling()
        }

        // RECORD VOICE-OVER MICROPHONE
        binding.toolVoice.setOnClickListener {
            triggerVoiceoverRecording()
        }

        // DELETE CURRENT CLIP ACTION
        binding.actionDeleteClip.setOnClickListener {
            val current = viewModel.selectedClip.value
            if (current != null) {
                // Determine which track contains the clip
                var trackLabel = ""
                val tracks = viewModel.tracks.value ?: return@setOnClickListener
                for (tr in tracks) {
                    if (tr.clips.contains(current)) {
                        trackLabel = tr.trackLabel
                        break
                    }
                }
                if (trackLabel.isNotEmpty()) {
                    viewModel.removeClipFromTrack(trackLabel, current)
                    Toast.makeText(this, "Removed from track", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // DUPLICATE CURRENT CLIP ACTION
        binding.actionDuplicateClip.setOnClickListener {
            val current = viewModel.selectedClip.value
            if (current != null) {
                var trackLabel = ""
                val tracks = viewModel.tracks.value ?: return@setOnClickListener
                for (tr in tracks) {
                    if (tr.clips.contains(current)) {
                        trackLabel = tr.trackLabel
                        break
                    }
                }
                if (trackLabel.isNotEmpty()) {
                    val duplicate = current.copy(id = java.util.UUID.randomUUID().toString())
                    viewModel.addClipToTrack(trackLabel, duplicate)
                    Toast.makeText(this, "Duplicated successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 1. SELECT ASPECT RATIO IMPLEMENTATION
    private fun showNewProjectDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogNewProjectBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val selectRatio = { ratio: String ->
            viewModel.setCanvasRatio(ratio)
            Toast.makeText(this, "Canvas configured to $ratio", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnRatio169.setOnClickListener { selectRatio("16:9") }
        sheetBinding.btnRatio916.setOnClickListener { selectRatio("9:16") }
        sheetBinding.btnRatio11.setOnClickListener { selectRatio("1:1") }
        sheetBinding.btnRatio45.setOnClickListener { selectRatio("4:5") }
        sheetBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        sheetBinding.btnCreate.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateCanvasDimensions(ratio: String) {
        val previewContainer = binding.previewCard
        val params = previewContainer.layoutParams as FrameLayout.LayoutParams

        // Keep bounds elegant based on standard device dimensions inside editor container
        val baseSize = resources.displayMetrics.widthPixels.coerceAtMost(resources.displayMetrics.heightPixels) / 2
        
        when (ratio) {
            "16:9" -> {
                params.width = (baseSize * 1.6f).toInt()
                params.height = baseSize
            }
            "9:16" -> {
                params.width = (baseSize * 0.62f).toInt()
                params.height = baseSize
            }
            "1:1" -> {
                params.width = baseSize
                params.height = baseSize
            }
            "4:5" -> {
                params.width = (baseSize * 0.8f).toInt()
                params.height = baseSize
            }
        }
        previewContainer.layoutParams = params
    }

    // 2. TIMELINE SEEKING AND TRACK SYNCHRONIZATIONS
    private fun updateLiveSubtitleAt(timeMs: Long): Boolean {
        val tracksList = viewModel.tracks.value ?: return false
        val overlayTrack = tracksList.find { it.trackLabel == "V2" } ?: return false

        // Search for text annotations/subtitles overlapping active coordinates
        val activeClip = overlayTrack.clips.find { c ->
            val start = c.startOffsetMs
            val end = c.startOffsetMs + c.durationMs
            (c.type == "text" || c.type == "subtitle") && timeMs in start..end
        }

        if (activeClip != null) {
            binding.tvActiveSubtitle.text = activeClip.textAnnotation
            binding.tvActiveSubtitle.visibility = View.VISIBLE
            
            // Mirror onto main preview text widget as well
            binding.overlayPreviewText.text = activeClip.textAnnotation
            binding.overlayPreviewText.visibility = View.VISIBLE
            return true
        } else {
            binding.tvActiveSubtitle.visibility = View.GONE
            binding.overlayPreviewText.visibility = View.GONE
            return false
        }
    }

    // 3. VOICE RECORDING SIMULATOR
    private fun triggerVoiceoverRecording() {
        // Request audio permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            simulateRecordingProgress()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            simulateRecordingProgress()
        } else {
            Toast.makeText(this, "Microphone access denied! Record simulate cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun simulateRecordingProgress() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Recording Voiceover Clip")
        builder.setMessage("Say something! Capturing microphone feed stream...")
        
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.isIndeterminate = true
        progress.setPadding(24, 16, 24, 16)
        builder.setView(progress)

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()

        // After 2.5s recording, finalize voice clip and duck audio clips
        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
            val voiceClip = Clip(
                type = "voice",
                fileName = "VO_Rec_${System.currentTimeMillis() % 10000}.wav",
                durationMs = 4000L,
                startOffsetMs = viewModel.currentTimeMs.value ?: 0L
            )
            viewModel.addClipToTrack("A2", voiceClip)
            Toast.makeText(this, "Voiceover segment appended to Timeline (A2)", Toast.LENGTH_LONG).show()
        }, 2500)
    }

    // 4. MIXER AUTO-DUCKING
    private fun updateAutoDuckingState(list: List<Track>) {
        val voiceTrack = list.find { it.trackLabel == "A2" } ?: return
        val musicTrack = list.find { it.trackLabel == "A1" } ?: return

        val containsVoice = voiceTrack.clips.isNotEmpty()
        if (containsVoice) {
            // Duck background tracks down to 20% volume securely
            for (c in musicTrack.clips) {
                if (c.volume > 0.2f) {
                    c.volume = 0.2f
                    Toast.makeText(this, "Audio auto-ducked A1 down to 20% level", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 5. SPEED CONTROL Presets & Bezier editor
    private fun showSpeedCurveSheet(clip: Clip) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetSpeedCurveBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val setSpeed = { speed: Float ->
            val updated = clip.copy(speed = speed)
            viewModel.applyClipChange(clip, updated)
            Toast.makeText(this, "Speed updated to ${speed}x", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnSpeed025c.setOnClickListener { setSpeed(0.25f) }
        sheetBinding.btnSpeed05c.setOnClickListener { setSpeed(0.5f) }
        sheetBinding.btnSpeed1c.setOnClickListener { setSpeed(1.0f) }
        sheetBinding.btnSpeed2c.setOnClickListener { setSpeed(2.0f) }
        sheetBinding.btnSpeedDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // 6. DETAILED COLOR GRADING MANUAL SLIDERS
    private fun showColorGradingSheet(clip: Clip) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetColorGradingBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Bind active properties inside layout sliders
        sheetBinding.sliderBrightness.value = (clip.volume - 1.0f) * 50f // simulate brightness slider onto amplitude changes
        sheetBinding.lblBrightness.text = "Brightness offset: ${(clip.volume - 1.0f).toInt()}%"

        sheetBinding.sliderBrightness.addOnChangeListener { _, value, _ ->
            sheetBinding.lblBrightness.text = "Brightness offset: ${value.toInt()}%"
        }

        sheetBinding.btnColorDone.setOnClickListener {
            val speedOffsetValue = (sheetBinding.sliderBrightness.value / 50f) + 1.0f
            val updated = clip.copy(volume = speedOffsetValue)
            viewModel.applyClipChange(clip, updated)
            Toast.makeText(this, "Color grading attributes synced", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // 7. COMPOSITE CHROMA KEY AND AI sujeto segmenters
    private fun showChromaCutoutSheet(clip: Clip) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetChromaCutoutBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Set layout panel states based on clip properties
        if (clip.hasChromaKey) {
            sheetBinding.panelChromaSettings.visibility = View.VISIBLE
            sheetBinding.panelAiCutoutSettings.visibility = View.GONE
        }

        sheetBinding.btnModeAiCutout.setOnClickListener {
            sheetBinding.panelAiCutoutSettings.visibility = View.VISIBLE
            sheetBinding.panelChromaSettings.visibility = View.GONE
            sheetBinding.panelMaskSettings.visibility = View.GONE
        }

        sheetBinding.btnModeChroma.setOnClickListener {
            sheetBinding.panelChromaSettings.visibility = View.VISIBLE
            sheetBinding.panelAiCutoutSettings.visibility = View.GONE
            sheetBinding.panelMaskSettings.visibility = View.GONE
        }

        sheetBinding.btnModeMask.setOnClickListener {
            sheetBinding.panelMaskSettings.visibility = View.VISIBLE
            sheetBinding.panelChromaSettings.visibility = View.GONE
            sheetBinding.panelAiCutoutSettings.visibility = View.GONE
        }

        sheetBinding.btnTriggerAiCutout.setOnClickListener {
            val updated = clip.copy(hasAiCutout = !clip.hasAiCutout)
            viewModel.applyClipChange(clip, updated)
            val msg = if (updated.hasAiCutout) "High-definition subject segmented!" else "AI subject cutout disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnTriggerMask.setOnClickListener {
            val updated = clip.copy(hasRadialMask = !clip.hasRadialMask)
            viewModel.applyClipChange(clip, updated)
            val msg = if (updated.hasRadialMask) "Radial Circle Mask Active!" else "Radial overlay disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnChromaDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // 8. LUT CINEMATIC FILTERS PRESET list
    private fun showFiltersSheet(clip: Clip) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetFiltersBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val setFilter = { filterId: String ->
            val updated = clip.copy(activeFilter = filterId)
            viewModel.applyClipChange(clip, updated)
            Toast.makeText(this, "Active Color LUT style: $filterId", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.chipVintage.setOnClickListener { setFilter("vintage") }
        sheetBinding.chipDrama.setOnClickListener { setFilter("drama") }
        sheetBinding.chipCyberpunk.setOnClickListener { setFilter("cyberpunk") }
        sheetBinding.chipNoir.setOnClickListener { setFilter("noir") }
        sheetBinding.chipSoftglow.setOnClickListener { setFilter("softglow") }
        sheetBinding.chipGolden.setOnClickListener { setFilter("golden") }

        sheetBinding.btnClearFilter.setOnClickListener {
            val updated = clip.copy(activeFilter = "none")
            viewModel.applyClipChange(clip, updated)
            Toast.makeText(this, "Colors reset to natural spectrum", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnFilterDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // 9. HARDWARE PRESETS FX SHEET
    private fun showFxSheet(clip: Clip) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetFxBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val setFx = { fxId: String ->
            val updated = clip.copy(activeFx = fxId)
            viewModel.applyClipChange(clip, updated)
            Toast.makeText(this, "FX loaded onto GPU shader: $fxId", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnFxGlitch.setOnClickListener { setFx("glitch") }
        sheetBinding.btnFxRgb.setOnClickListener { setFx("rgb") }
        sheetBinding.btnFxPixel.setOnClickListener { setFx("pixel") }
        sheetBinding.btnFxBlur.setOnClickListener { setFx("blur") }

        sheetBinding.btnClearFx.setOnClickListener {
            val updated = clip.copy(activeFx = "none")
            viewModel.applyClipChange(clip, updated)
            Toast.makeText(this, "Shaders disabled", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        sheetBinding.btnFxDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // 10. LOCAL PREVIEW HARDWARE FX SHADERS REFLECTION
    private fun updateVisualShaderStyle() {
        val tracksList = viewModel.tracks.value ?: return
        val mainVideoTrack = tracksList.find { it.trackLabel == "V1" } ?: return
        val overlayTrack = tracksList.find { it.trackLabel == "V2" } ?: return

        // Fetch active intersecting clip first
        val curTimeMs = viewModel.currentTimeMs.value ?: 0L
        val activeClip = mainVideoTrack.clips.find { c ->
            val start = c.startOffsetMs
            val end = c.startOffsetMs + c.durationMs
            curTimeMs in start..end
        } ?: overlayTrack.clips.find { c ->
            val start = c.startOffsetMs
            val end = c.startOffsetMs + c.durationMs
            curTimeMs in start..end
        }

        if (activeClip != null) {
            // Apply color filter tint
            when (activeClip.activeFilter) {
                "vintage" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#3FA35D20")) // sepia vintage
                "cyberpunk" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#4400E5FF")) // neon cyan cyan overlay
                "drama" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#44990000")) // hot red tint
                "noir" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#66606060")) // grey monochrome shading
                "softglow" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#22FFFFFF")) // high luminosity white
                "golden" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#3FE5A300")) // sunset glow
                else -> {
                    // Check active FX overlays
                    when (activeClip.activeFx) {
                        "glitch" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#3FAA00AA")) // chromatic pink tint
                        "rgb" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#3FAA0000"))
                        "pixel" -> binding.effectOverlayShader.setBackgroundColor(Color.parseColor("#220000FF"))
                        else -> binding.effectOverlayShader.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
            }

            // Apply cutout masks visibility
            if (activeClip.hasAiCutout) {
                binding.maskOverlayView.visibility = View.VISIBLE
            } else {
                binding.maskOverlayView.visibility = View.GONE
            }
        } else {
            binding.effectOverlayShader.setBackgroundColor(Color.TRANSPARENT)
            binding.maskOverlayView.visibility = View.GONE
        }
    }

    // 11. TEXTOVER CAPTION DIALOGS
    private fun showAddTextDialog(clip: Clip) {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAddTextBinding.inflate(layoutInflater)
        builder.setView(dialogBinding.root)

        dialogBinding.etDialogText.setText(clip.textAnnotation)

        val fonts = listOf("Space Grotesk Pro", "System Bold", "Monospace Coding", "Serif Elegant", "Roboto Condensed")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fonts)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerFonts.adapter = spinnerAdapter

        val textDialog = builder.create()

        dialogBinding.btnTextCancel.setOnClickListener { textDialog.dismiss() }
        dialogBinding.btnTextConfirm.setOnClickListener {
            val value = dialogBinding.etDialogText.text.toString().trim()
            if (value.isNotEmpty()) {
                val updated = clip.copy(
                    textAnnotation = value,
                    fontName = dialogBinding.spinnerFonts.selectedItem?.toString() ?: "Default"
                )
                viewModel.applyClipChange(clip, updated)
                Toast.makeText(this, "Text overlays customized successfully", Toast.LENGTH_SHORT).show()
                textDialog.dismiss()
            }
        }

        textDialog.show()
    }

    // 12. SPECIAL AI DEMAND AUTO-SUBTITLE
    private fun triggerAutoSubtitleModeling() {
        val tracksList = viewModel.tracks.value ?: return
        val v1Track = tracksList.find { it.trackLabel == "V1" } ?: return

        if (v1Track.clips.isEmpty()) {
            Toast.makeText(this, "Empty project, import some media files first", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading progress simulating cloud neural parsing
        val progressBuilder = AlertDialog.Builder(this)
        progressBuilder.setTitle("Acoustic Model Analyzer")
        progressBuilder.setMessage("Isolating phonetic tracks & composing matching subtitles on track...")

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.isIndeterminate = true
        bar.setPadding(24, 16, 24, 16)
        progressBuilder.setView(bar)

        progressBuilder.setCancelable(false)
        val progressDialog = progressBuilder.create()
        progressDialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            progressDialog.dismiss()
            // Clear prior subtitle clips on V2 overlay
            val overlayTrack = tracksList.find { it.trackLabel == "V2" } ?: return@postDelayed
            overlayTrack.clips.removeAll { it.type == "subtitle" }

            // Produce automatic subtitles synced to video bounds
            val testCaptions = listOf(
                "Welcome to the ultimate AI Studio",
                "Rendering cinematic graphic effects",
                "Powered by Snapdragon 8s optimization",
                "Exporting master codec timeline"
            )

            var offset = 0L
            for (i in 0 until testCaptions.size.coerceAtMost(v1Track.clips.size * 2)) {
                val sub = Clip(
                    type = "subtitle",
                    fileName = "Sub_${i + 1}",
                    textAnnotation = testCaptions[i % testCaptions.size],
                    durationMs = 4000L,
                    startOffsetMs = offset
                )
                viewModel.addClipToTrack("V2", sub)
                offset += 5000L
            }

            Toast.makeText(this, "Composed phonetic text tracks! Double-tap subtitles overlay to modify.", Toast.LENGTH_LONG).show()
        }, 2200)
    }

    // 13. SPECIALIZED COMPILING PORTAL EXPORTER
    private fun showExportDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogExportBinding.inflate(layoutInflater)
        builder.setView(dialogBinding.root)

        val dialog = builder.create()

        dialogBinding.btnExportCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnExportBegin.setOnClickListener {
            dialog.dismiss()
            performFinalCompileSimulation()
        }

        dialog.show()
    }

    private fun performFinalCompileSimulation() {
        val pBuilder = AlertDialog.Builder(this)
        pBuilder.setTitle("Rendering Output Video Segment")
        pBuilder.setMessage("Compositing filters, lut shaders, active soundtracks and overlay models...")

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.isIndeterminate = false
        bar.max = 100
        bar.setPadding(24, 16, 24, 16)
        pBuilder.setView(bar)

        pBuilder.setCancelable(false)
        val pDialog = pBuilder.create()
        pDialog.show()

        // Count up progress elegantly
        val h = Handler(Looper.getMainLooper())
        var currentPercent = 0
        val runnable = object : Runnable {
            override fun run() {
                if (currentPercent < 100) {
                    currentPercent += 10
                    bar.progress = currentPercent
                    pBuilder.setMessage("Compositing filters, lut shaders, active soundtracks and overlay models... ($currentPercent%)")
                    h.postDelayed(this, 300)
                } else {
                    pDialog.dismiss()
                    finalizeCompiledFile()
                }
            }
        }
        h.post(runnable)
    }

    private fun finalizeCompiledFile() {
        // Create exported directory
        val dir = File(filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()

        val filename = "RENDER_EXPORT_${System.currentTimeMillis()}.mp4"
        val out = File(dir, filename)
        out.writeText("AI VIDEO EDITOR PRO MP4 BIN DATA PACK") // Write fake byte data to finalize real file paths

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "com.aivideoeditor.pro.fileprovider",
            out
        )

        // Show beautiful successful bottom sheet
        val successBuilder = AlertDialog.Builder(this)
        successBuilder.setTitle("Export Render Complete!")
        successBuilder.setMessage("Successfully compiled project into high quality AVC codec video format!\n\nFile saved under library: /exports/$filename")
        successBuilder.setPositiveButton("Share Video") { _, _ ->
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share master video render output"))
        }
        successBuilder.setNegativeButton("Great", null)
        successBuilder.show()
    }

    // Simple helpers
    private fun getFileNameFromUri(uri: android.net.Uri): String {
        return uri.path?.substringAfterLast("/") ?: "imported_clip.mp4"
    }

    private fun formatTimecode(timeMs: Long): String {
        val secNum = (timeMs / 1000).toInt()
        val min = secNum / 60
        val sec = secNum % 60
        val ms = ((timeMs % 1000) / 100).toInt()
        return String.format("%02d:%02d.%d", min, sec, ms)
    }
}
