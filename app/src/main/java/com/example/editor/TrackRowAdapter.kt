package com.example.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemTrackRowBinding

class TrackRowAdapter(
    private var tracks: List<Track>,
    private val selectedClipProvider: () -> Clip?,
    private val onClipClick: (Track, Clip) -> Unit
) : RecyclerView.Adapter<TrackRowAdapter.TrackViewHolder>() {

    fun updateTracks(newTracks: List<Track>) {
        this.tracks = newTracks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(tracks[position])
    }

    override fun getItemCount(): Int = tracks.size

    inner class TrackViewHolder(private val binding: ItemTrackRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track) {
            binding.tvTrackLabel.text = track.trackLabel

            // Nested clips recycler setup
            binding.rvTrackClips.layoutManager = LinearLayoutManager(
                binding.root.context, LinearLayoutManager.HORIZONTAL, false
            )
            val clipsAdapter = ClipsHorizontalAdapter(
                track.clips,
                selectedClipProvider,
                onClipClick = { clip -> onClipClick(track, clip) }
            )
            binding.rvTrackClips.adapter = clipsAdapter
        }
    }
}
