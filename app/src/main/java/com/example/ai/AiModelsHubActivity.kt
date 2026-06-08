package com.example.ai

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ActivityAiModelsBinding
import com.example.databinding.ItemModelCardBinding

class AiModelsHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiModelsBinding
    private lateinit var modelManager: ModelManager
    private lateinit var adapter: ModelsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiModelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager = ModelManager(this)
        
        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.rvModelsList.layoutManager = LinearLayoutManager(this)
        adapter = ModelsAdapter(modelManager.getModels().toMutableList()) { model, isUseAction ->
            if (isUseAction) {
                // Open appropriate activity
                if (model.id == "model_inpainting") {
                    val intent = Intent(this, InpaintingActivity::class.java)
                    startActivity(intent)
                } else {
                    val intent = Intent(this, TextToImageActivity::class.java)
                    intent.putExtra("EXTRA_SELECTED_MODEL_ID", model.id)
                    startActivity(intent)
                }
            } else {
                // Simulate install progress for 2 seconds
                simulateInstallation(model)
            }
        }
        binding.rvModelsList.adapter = adapter
    }

    private fun simulateInstallation(model: AiModel) {
        // Find index of model card
        val index = adapter.models.indexOfFirst { it.id == model.id }
        if (index == -1) return

        Toast.makeText(this, "Downloading & Installing neural checkpoints...", Toast.LENGTH_SHORT).show()
        
        // Simulating background download / unzip / load
        Handler(Looper.getMainLooper()).postDelayed({
            modelManager.setInstalled(model.id, true)
            // Reload local list
            adapter.models[index] = model.copy(isInstalled = true)
            adapter.notifyItemChanged(index)
            Toast.makeText(this, "${model.name} deployed successfully!", Toast.LENGTH_SHORT).show()
        }, 2000)
    }

    // Inner Adapter Class for AI Models
    private class ModelsAdapter(
        val models: MutableList<AiModel>,
        val onActionClick: (AiModel, Boolean) -> Unit
    ) : RecyclerView.Adapter<ModelsAdapter.ModelViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
            val itemBinding = ItemModelCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ModelViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
            val model = models[position]
            holder.bind(model)
        }

        override fun getItemCount(): Int = models.size

        inner class ModelViewHolder(private val itemBinding: ItemModelCardBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(model: AiModel) {
                itemBinding.tvModelName.text = model.name
                itemBinding.tvModelDeveloper.text = model.developer
                itemBinding.tvModelDescription.text = model.description

                if (model.isInstalled) {
                    itemBinding.tvModelStatus.text = "Status: READY (DEPLOYED)"
                    itemBinding.tvModelStatus.setTextColor(
                        itemBinding.root.context.getColor(android.R.color.holo_green_light)
                    )
                    itemBinding.btnAction.text = "Launch Engine"
                    itemBinding.btnAction.setBackgroundColor(
                        itemBinding.root.context.getColor(com.example.R.color.accent_cyan)
                    )
                    itemBinding.btnAction.setTextColor(
                        itemBinding.root.context.getColor(com.example.R.color.black)
                    )
                } else {
                    itemBinding.tvModelStatus.text = "Status: NOT INSTALLED"
                    itemBinding.tvModelStatus.setTextColor(
                        itemBinding.root.context.getColor(com.example.R.color.text_secondary)
                    )
                    itemBinding.btnAction.text = "Compile Pack"
                    itemBinding.btnAction.setBackgroundColor(
                        itemBinding.root.context.getColor(com.example.R.color.surface_dark_glass)
                    )
                    itemBinding.btnAction.setTextColor(
                        itemBinding.root.context.getColor(com.example.R.color.white)
                    )
                }

                itemBinding.btnAction.setOnClickListener {
                    onActionClick(model, model.isInstalled)
                }
            }
        }
    }
}
