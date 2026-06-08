package com.example.ai

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ActivityLibraryBinding
import com.example.databinding.ItemLibraryImageBinding
import java.io.File

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var adapter: LibraryGridAdapter
    private val imagesList = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
        loadLocalLibrary()
    }

    private fun setupRecyclerView() {
        binding.rvLibraryGrid.layoutManager = GridLayoutManager(this, 3)
        adapter = LibraryGridAdapter(imagesList,
            onItemClicked = { file ->
                // Return selected item paths back to editor
                val returnIntent = Intent().apply {
                    putExtra("EXTRA_IMAGE_PATH", file.absolutePath)
                }
                setResult(Activity.RESULT_OK, returnIntent)
                Toast.makeText(this, "Selected file: ${file.name}", Toast.LENGTH_SHORT).show()
                finish()
            },
            onDeleteClicked = { file ->
                deleteLibraryFile(file)
            }
        )
        binding.rvLibraryGrid.adapter = adapter
    }

    private fun loadLocalLibrary() {
        val dir = File(filesDir, "app_images")
        if (dir.exists() && dir.isDirectory) {
            val list = dir.listFiles { pathname ->
                pathname.isFile && (pathname.name.startsWith("AI_GEN_") || pathname.name.startsWith("AI_INPAINT_") || pathname.name.endsWith(".png"))
            }
            imagesList.clear()
            if (list != null) {
                imagesList.addAll(list.sortedByDescending { it.lastModified() })
            }
        }

        adapter.notifyDataSetChanged()
        toggleEmptyState()
    }

    private fun toggleEmptyState() {
        if (imagesList.isEmpty()) {
            binding.emptyStateView.visibility = View.VISIBLE
            binding.rvLibraryGrid.visibility = View.GONE
        } else {
            binding.emptyStateView.visibility = View.GONE
            binding.rvLibraryGrid.visibility = View.VISIBLE
        }
    }

    private fun deleteLibraryFile(file: File) {
        if (file.exists()) {
            if (file.delete()) {
                val idx = imagesList.indexOf(file)
                if (idx != -1) {
                    imagesList.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                    toggleEmptyState()
                    Toast.makeText(this, "Asset removed successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Could not delete asset from storage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Recycler Adapter implementation for library list
    private class LibraryGridAdapter(
        private val list: List<File>,
        private val onItemClicked: (File) -> Unit,
        private val onDeleteClicked: (File) -> Unit
    ) : RecyclerView.Adapter<LibraryGridAdapter.LibraryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
            val itemBinding = ItemLibraryImageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return LibraryViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class LibraryViewHolder(private val itemBinding: ItemLibraryImageBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(file: File) {
                // Read bitmap with scale sample sizing
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4 // Load low res thumbnail
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                if (bitmap != null) {
                    itemBinding.ivLibraryThumbnail.setImageBitmap(bitmap)
                }

                itemBinding.tvLibraryImageTitle.text = file.name

                itemBinding.root.setOnClickListener {
                    onItemClicked(file)
                }

                itemBinding.btnDeleteImage.setOnClickListener {
                    onDeleteClicked(file)
                }
            }
        }
    }
}
