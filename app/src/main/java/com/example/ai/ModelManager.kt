package com.example.ai

import android.content.Context
import android.content.SharedPreferences

data class AiModel(
    val id: String,
    val name: String,
    val developer: String,
    val description: String,
    var isInstalled: Boolean,
    val type: String // "text_to_image" or "inpainting"
)

class ModelManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)

    fun getModels(): List<AiModel> {
        return listOf(
            AiModel(
                "model_dalle3",
                "OpenAI DALL·E 3",
                "OpenAI",
                "High-fidelity visual renderings from detailed prompts with deep context reasoning.",
                isInstalled("model_dalle3"),
                "text_to_image"
            ),
            AiModel(
                "model_sdxl",
                "Stability AI SDXL",
                "Stability AI",
                "Ultra photorealistic cinematic prompt expansion and composition generator.",
                isInstalled("model_sdxl"),
                "text_to_image"
            ),
            AiModel(
                "model_fal_ai",
                "Fal AI Fast Diffusion",
                "Fal AI",
                "Extremely responsive, low-latency latent consistency renderer for interactive editing.",
                isInstalled("model_fal_ai"),
                "text_to_image"
            ),
            AiModel(
                "model_inpainting",
                "Stability AI Inpainting v2",
                "Stability AI",
                "Generative filling that replaces selected masked image regions seamlessly.",
                isInstalled("model_inpainting"),
                "inpainting"
            )
        )
    }

    fun getInstalledModelsOf(type: String): List<AiModel> {
        return getModels().filter { it.isInstalled && it.type == type }
    }

    fun isInstalled(modelId: String): Boolean {
        // By default, let's keep dalle3 installed initially to make the first-use UX super friendly!
        if (modelId == "model_dalle3") {
            return prefs.getBoolean("install_$modelId", true)
        }
        return prefs.getBoolean("install_$modelId", false)
    }

    fun setInstalled(modelId: String, installed: Boolean) {
        prefs.edit().putBoolean("install_$modelId", installed).apply()
    }
}
