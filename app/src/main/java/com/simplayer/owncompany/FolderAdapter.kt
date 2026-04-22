class FolderAdapter(
    private val list: List<VideoFolder>,
    private val onClick: (VideoFolder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(android.R.id.text1)
        val count: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val folder = list[position]
        holder.name.text = folder.name
        holder.count.text = "${folder.videos.size} videos"

        holder.itemView.setOnClickListener {
            onClick(folder)
        }
    }
}