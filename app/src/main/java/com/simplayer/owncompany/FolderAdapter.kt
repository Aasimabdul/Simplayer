package com.simplayer.owncompany

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplayer.owncompany.databinding.ListItemFolderBinding

class FolderAdapter(
    private val folders: List<VideoFolder>,
    private val onFolderClick: (VideoFolder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    class ViewHolder(val binding: ListItemFolderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.binding.tvFolderName.text = folder.name
        holder.binding.tvVideoCount.text = String.format("%d videos", folder.videoCount)
        
        if (folder.isNew > 0) {
            holder.binding.tvNewTag.visibility = View.VISIBLE
            holder.binding.tvNewTag.text = folder.isNew.toString()
        } else {
            holder.binding.tvNewTag.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onFolderClick(folder) }
    }

    override fun getItemCount(): Int = folders.size
}
