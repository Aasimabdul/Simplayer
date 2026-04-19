package com.simplayer.owncompany.dlna

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simplayer.owncompany.R

class VideoAdapter(
    private val list: List<String>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.videoTitle)
        val info: TextView = view.findViewById(R.id.videoInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_video, parent, false)
        return VH(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val path = list[position]
        val name = path.substringAfterLast("/")
        holder.title.text = name
        
        val extension = name.substringAfterLast(".", "").uppercase()
        holder.info.text = if (extension.isNotEmpty()) "$extension Video" else "Media File"
        
        holder.itemView.setOnClickListener { onClick(position) }
    }
}
