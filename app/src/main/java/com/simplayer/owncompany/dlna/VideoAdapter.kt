package com.simplayer.owncompany.dlna

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplayer.owncompany.databinding.ListItemVideoBinding

class VideoAdapter(
    private val list: List<String>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    class VH(val binding: ListItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ListItemVideoBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return VH(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val path = list[position]
        val name = path.substringAfterLast("/")
        holder.binding.videoTitle.text = name
        
        val extension = name.substringAfterLast(".", "").uppercase()
        holder.binding.videoInfo.text = if (extension.isNotEmpty()) "$extension Video" else "Media File"
        
        holder.itemView.setOnClickListener { onClick(position) }
    }
}
