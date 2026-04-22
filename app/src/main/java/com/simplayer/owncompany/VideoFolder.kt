package com.simplayer.owncompany

import java.io.File

data class VideoFolder(
    val name: String,
    val path: String,
    val videos: MutableList<VideoModel> = mutableListOf(),
    var isNew: Int = 0
) {
    val videoCount: Int get() = videos.size
}
