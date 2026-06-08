package com.example.ai

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.databinding.ActivityTextToImageBinding
import java.io.File

class TextToImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextToImageBinding
    private lateinit var modelManager: ModelManager
    private var generatedImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextToImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager = ModelManager(this)

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupModelsSpinner()

        binding.btnGenerate.setOnClickListener {
            val prompt = binding.etPrompt.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Please enter a descriptive prompt!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            triggerGeneration(prompt)
        }

        binding.btnAddToProject.setOnClickListener {
            generatedImagePath?.let { path ->
                // Send path back to Editor
                val returnIntent = Intent()
                returnIntent.putExtra("EXTRA_IMAGE_PATH", path)
                setResult(Activity.RESULT_OK, returnIntent)
                Toast.makeText(this, "Appended generated asset to Timeline V2", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupModelsSpinner() {
        val installed = modelManager.getInstalledModelsOf("text_to_image")
        if (installed.isEmpty()) {
            Toast.makeText(this, "No text models installed! Launch DALL-E 3 config.", Toast.LENGTH_LONG).show()
            val names = listOf("OpenAI DALL·E 3 (Default Offline)")
            val spinAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerModels.adapter = spinAdapter
        } else {
            val names = installed.map { it.name }
            val spinAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerModels.adapter = spinAdapter

            // If an extra model was passed, try selecting it
            val defaultModelId = intent.getStringExtra("EXTRA_SELECTED_MODEL_ID")
            defaultModelId?.let { id ->
                val selectedIndex = installed.indexOfFirst { it.id == id }
                if (selectedIndex != -1) {
                    binding.spinnerModels.setSelection(selectedIndex)
                }
            }
        }
    }

    private fun triggerGeneration(prompt: String) {
        val selectedModel = binding.spinnerModels.selectedItem?.toString() ?: "DALL-E 3"

        binding.btnGenerate.isEnabled = false
        binding.pbRendering.visibility = View.VISIBLE
        binding.tvRenderStatus.text = "AI Generating with $selectedModel..."
        binding.tvRenderStatus.visibility = View.VISIBLE
        binding.btnAddToProject.visibility = View.GONE
        binding.tvPromptOverlay.text = ""

        Handler(Looper.getMainLooper()).postDelayed({
            val imagePath = generateSimulatedImage(prompt, selectedModel)
            generatedImagePath = imagePath

            // Display generated image
            val bitmap = BitmapFactory.decodeFile(imagePath)
            binding.ivGeneratedPreview.setImageBitmap(bitmap)
            binding.tvPromptOverlay.text = "PROMPT: $prompt\nMODEL: $selectedModel"

            // Reset States
            binding.btnGenerate.isEnabled = true
            binding.pbRendering.visibility = View.GONE
            binding.tvRenderStatus.visibility = View.GONE
            binding.btnAddToProject.visibility = View.VISIBLE

            Toast.makeText(this, "Creative generation complete!", Toast.LENGTH_SHORT).show()
        }, 3000)
    }

    private fun generateSimulatedImage(prompt: String, modelName: String): String {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        // Background dark gradient matching our color scheme
        val lg = LinearGradient(
            0f, 0f, 512f, 512f,
            Color.parseColor("#0A0A0C"), Color.parseColor("#1C1C28"), Shader.TileMode.CLAMP
        )
        paint.shader = lg
        canvas.drawRect(0f, 0f, 512f, 512f, paint)
        paint.shader = null

        // Neon design frame / portal circle
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawCircle(256f, 256f, 190f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2200E5FF")
        canvas.drawCircle(256f, 256f, 190f, paint)

        // Shimmering neural connections
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#55FFFFFF")
        canvas.drawLine(100f, 100f, 220f, 180f, paint)
        canvas.drawLine(220f, 180f, 320f, 140f, paint)
        canvas.drawLine(320f, 140f, 412f, 240f, paint)
        canvas.drawLine(412f, 240f, 256f, 380f, paint)
        canvas.drawLine(256f, 380f, 100f, 100f, paint)

        // Node dots
        paint.color = Color.WHITE
        canvas.drawCircle(100f, 100f, 8f, paint)
        canvas.drawCircle(220f, 180f, 10f, paint)
        canvas.drawCircle(320f, 140f, 6f, paint)
        canvas.drawCircle(412f, 240f, 10f, paint)
        canvas.drawCircle(256f, 380f, 12f, paint)

        // Draw Play/Triangle element inside node
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawCircle(220f, 180f, 4f, paint)
        canvas.drawCircle(256f, 380f, 5f, paint)

        // Main overlay captions
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        paint.textScaleX = 0.9f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("AI PRO GRAPHIC ENGINE", 256f, 220f, paint)

        // Prompt text truncation
        var label = if (prompt.length > 28) prompt.substring(0, 25) + "..." else prompt
        paint.color = Color.parseColor("#00E5FF")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText("\"$label\"", 256f, 270f, paint)

        // Technology meta footer
        paint.color = Color.parseColor("#A0A0A0")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("COMPOSED VIA $modelName", 256f, 320f, paint)

        // Save PNG to private images folder
        val dir = File(filesDir, "app_images")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "AI_GEN_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }
}
