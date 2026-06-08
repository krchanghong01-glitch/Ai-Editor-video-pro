package com.example.editor

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemClipBinding
import java.io.File

class ClipsHorizontalAdapter(
    private val clips: List<Clip>,
    private val selectedClipProvider: () -> Clip?,
    private val onClipClick: (Clip) -> Unit
) : RecyclerView.Adapter<ClipsHorizontalAdapter.ClipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val binding = ItemClipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ClipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.bind(clips[position])
    }

    override fun getItemCount(): Int = clips.size

    inner class ClipViewHolder(private val binding: ItemClipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(clip: Clip) {
            val ctx = binding.root.context
            
            // Set text label
            binding.tvClipTitle.text = if (clip.type == "subtitle" || clip.type == "text") {
                if (clip.textAnnotation.isNotEmpty()) clip.textAnnotation else "Caption"
            } else {
                clip.fileName
            }

            // Secondary meta text representation
            binding.tvClipInfo.text = "${clip.type.uppercase()} | ${clip.speed}x | ${clip.durationMs / 1000f}s"

            // Preview local image rendering for AI Generated pics
            if (clip.type == "image" && clip.fileName.startsWith("/")) {
                val f = File(clip.fileName)
                if (f.exists()) {
                    val op = BitmapFactory.Options().apply { inSampleSize = 8 }
                    val b = BitmapFactory.decodeFile(clip.fileName, op)
                    if (b != null) {
                        binding.ivThumbnail.setImageBitmap(b)
                        binding.ivThumbnail.visibility = View.VISIBLE
                    } else {
                        binding.ivThumbnail.visibility = View.GONE
                    }
                } else {
                    binding.ivThumbnail.visibility = View.GONE
                }
            } else {
                binding.ivThumbnail.visibility = View.GONE
            }

            // Selection state border visualization
            val selected = selectedClipProvider()
            if (selected?.id == clip.id) {
                binding.selectionBorder.visibility = View.VISIBLE
                binding.leftTrimHandle.visibility = View.VISIBLE
                binding.rightTrimHandle.visibility = View.VISIBLE
            } else {
                binding.selectionBorder.visibility = View.GONE
                binding.leftTrimHandle.visibility = View.GONE
                binding.rightTrimHandle.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onClipClick(clip)
            }
        }
    }
}
