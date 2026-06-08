package com.example.ai

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.databinding.ActivityInpaintingBinding
import java.io.File
import java.io.FileOutputStream

class InpaintingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInpaintingBinding
    private var sourceImageUri: Uri? = null
    private var sourceBitmap: Bitmap? = null
    private var resultImagePath: String? = null

    // Register image picker contract
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                loadSelectedImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInpaintingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        }

        binding.btnClearMask.setOnClickListener {
            binding.inpaintingCanvas.clear()
        }

        binding.btnInpaint.setOnClickListener {
            if (sourceBitmap == null) {
                Toast.makeText(this, "Please choose a source image first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.inpaintingCanvas.isMaskEmpty()) {
                Toast.makeText(this, "Please paint a mask over the area you want to replace!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            triggerInpainting()
        }
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            sourceImageUri = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // Decode bitmap
            val inputStream = contentResolver.openInputStream(uri)
            val decoded = BitmapFactory.decodeStream(inputStream)
            
            if (decoded != null) {
                // Scale down slightly if too large to avoid OOM
                val scaled = scaleBitmapIfNeeded(decoded)
                sourceBitmap = scaled
                
                binding.ivInpaintingBg.setImageBitmap(scaled)
                binding.tvCanvasHint.visibility = View.GONE
                binding.inpaintingCanvas.clear()
                Toast.makeText(this, "Image loaded! Paint the retouch mask.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Could not open selected image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
            return bitmap
        }
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val argW: Int
        val argH: Int
        if (bitmap.width > bitmap.height) {
            argW = maxDimension
            argH = (maxDimension / ratio).toInt()
        } else {
            argH = maxDimension
            argW = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, argW, argH, true)
    }

    private fun triggerInpainting() {
        binding.btnInpaint.isEnabled = false
        binding.btnClearMask.isEnabled = false
        binding.pbInpainting.visibility = View.VISIBLE
        binding.tvInpaintingStatus.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            val src = sourceBitmap
            if (src != null) {
                val maskPaths = binding.inpaintingCanvas.getMaskCoordinates()
                val resultBitmap = applySimulatedInpainting(src, maskPaths)

                // Save result to file
                val savedPath = saveGeneratedResult(resultBitmap)
                resultImagePath = savedPath

                // Render result inside image preview
                binding.ivInpaintingBg.setImageBitmap(resultBitmap)
                binding.inpaintingCanvas.clear() // Clear mask overlay after render

                Toast.makeText(this, "Inpainting process complete!", Toast.LENGTH_LONG).show()
            }

            binding.btnInpaint.isEnabled = true
            binding.btnClearMask.isEnabled = true
            binding.pbInpainting.visibility = View.GONE
            binding.tvInpaintingStatus.visibility = View.GONE
        }, 2000)
    }

    private fun applySimulatedInpainting(src: Bitmap, paths: List<Path>): Bitmap {
        val result = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Draw blurred fill across the original mask paths
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 42f
            isAntiAlias = true
            color = Color.parseColor("#9020202A") // Dreamy blurred slate overlay filled back in coordinate
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        }

        // Apply blur on the path segments
        for (path in paths) {
            canvas.drawPath(path, paint)
        }

        // Add a modern digital neon line details over the masked center to simulate high tech reconstruction
        val neonPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 6f
            color = Color.parseColor("#00E5FF")
            isAntiAlias = true
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.OUTER)
        }
        for (path in paths) {
            canvas.drawPath(path, neonPaint)
        }

        return result
    }

    private fun saveGeneratedResult(bitmap: Bitmap): String {
        val dir = File(filesDir, "app_images")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "AI_INPAINT_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }
}
