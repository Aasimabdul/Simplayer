package com.simplayer.owncompany

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.simplayer.owncompany.databinding.ActivityMainBinding
import com.simplayer.owncompany.dlna.DlnaBrowser
import com.simplayer.owncompany.dlna.DlnaEntry
import com.simplayer.owncompany.dlna.DlnaServer
import kotlinx.coroutines.*

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null

    private val videoUris = mutableListOf<Uri>()
    private val videoNames = mutableListOf<String>()
    private var listAdapter: ArrayAdapter<String>? = null

    private val prefs by lazy {
        getSharedPreferences("simplayer_prefs", Context.MODE_PRIVATE)
    }

    private val speedList = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 0

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // DLNA
    private var dlnaBrowser: DlnaBrowser? = null
    private var dlnaServers = listOf<DlnaServer>()
    private var dlnaPathStack = mutableListOf<String>()
    private var dlnaCurrentServer: DlnaServer? = null

    // Folder picker
    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
            if (treeUri != null) loadVideosFromFolder(treeUri)
        }

    // Subtitle picker
    private val pickSubtitle =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { subUri: Uri? ->
            if (subUri != null) attachSubtitle(subUri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPlayer()
        setupList()
        setupControls()

        binding.btnPickFolder.setOnClickListener { pickFolder.launch(null) }
        binding.btnWeb.setOnClickListener { askWebUrl() }
        binding.btnDlna.setOnClickListener { openDlnaServers() }
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player
    }

    private fun setupControls() {
        binding.btnBack10.setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
        }

        binding.btnForward10.setOnClickListener {
            val p = player ?: return@setOnClickListener
            val dur = p.duration
            val newPos = if (dur > 0) (p.currentPosition + 10_000).coerceAtMost(dur) else p.currentPosition + 10_000
            p.seekTo(newPos)
        }

        binding.btnSpeed.setOnClickListener {
            val p = player ?: return@setOnClickListener
            speedIndex = (speedIndex + 1) % speedList.size
            val sp = speedList[speedIndex]
            p.playbackParameters = PlaybackParameters(sp)
            binding.btnSpeed.text = "${sp}x"
        }

        binding.btnSub.setOnClickListener {
            pickSubtitle.launch(arrayOf("application/x-subrip", "text/plain", "application/octet-stream"))
        }

        binding.btnAudio.setOnClickListener {
            selectAudioTrack()
        }
    }

    private fun setupList() {
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, videoNames)
        binding.videoList.adapter = listAdapter

        binding.videoList.setOnItemClickListener { _, _, position, _ ->
            playVideo(videoUris[position], resumeIfSame = false)
        }
    }

    private fun loadVideosFromFolder(treeUri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        videoUris.clear()
        videoNames.clear()

        val folder = DocumentFile.fromTreeUri(this, treeUri)
        if (folder != null && folder.isDirectory) {
            val files = folder.listFiles()
                .filter { it.isFile && isVideoFile(it.name ?: "", it.type ?: "") }
                .sortedBy { it.name?.lowercase() }

            for (file in files) {
                videoUris.add(file.uri)
                videoNames.add(file.name ?: "Unknown video")
            }
        }

        listAdapter?.notifyDataSetChanged()
        resumeLastIfPossible()
    }

    private fun isVideoFile(name: String, mime: String): Boolean {
        if (mime.startsWith("video/")) return true
        val lower = name.lowercase()
        return lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".avi") ||
                lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".m4v")
    }

    private fun resumeLastIfPossible() {
        val lastUriStr = prefs.getString("last_uri", null)
        if (lastUriStr == null) {
            if (videoUris.isNotEmpty()) playVideo(videoUris[0], resumeIfSame = false)
            return
        }

        val lastPos = prefs.getLong("last_pos", 0L)
        val lastUri = Uri.parse(lastUriStr)

        val index = videoUris.indexOfFirst { it.toString() == lastUri.toString() }
        if (index != -1) {
            binding.videoList.setSelection(index)
            playVideo(lastUri, resumeIfSame = true, resumePosition = lastPos)
        } else if (videoUris.isNotEmpty()) {
            playVideo(videoUris[0], resumeIfSame = false)
        }
    }

    private fun playVideo(uri: Uri, resumeIfSame: Boolean, resumePosition: Long = 0L) {
        val p = player ?: return

        val currentUri = prefs.getString("last_uri", null)
        val same = (currentUri == uri.toString())

        val mediaItem = MediaItem.fromUri(uri)
        p.setMediaItem(mediaItem)
        p.prepare()

        if (resumeIfSame && same && resumePosition > 0) {
            p.seekTo(resumePosition)
        }

        p.playWhenReady = true
        prefs.edit().putString("last_uri", uri.toString()).apply()
    }

    private fun playWebOrDlna(url: String) {
        playVideo(Uri.parse(url), resumeIfSame = false)
    }

    private fun attachSubtitle(subUri: Uri) {
        val p = player ?: return

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
            .setMimeType("application/x-subrip")
            .setLanguage("en")
            .setSelectionFlags(0)
            .build()

        val current = p.currentMediaItem ?: return
        val newItem = current.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        val pos = p.currentPosition
        p.setMediaItem(newItem)
        p.prepare()
        p.seekTo(pos)
        p.playWhenReady = true

        Toast.makeText(this, "Subtitle added!", Toast.LENGTH_SHORT).show()
    }

    private fun selectAudioTrack() {
        val p = player ?: return

        val params = TrackSelectionParameters.Builder(this)
            .setPreferredAudioLanguage(null)
            .build()

        p.trackSelectionParameters = params
        Toast.makeText(this, "Audio set to default", Toast.LENGTH_SHORT).show()
    }

    private fun askWebUrl() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.hint = "Paste MP4/M3U8 direct link"

        AlertDialog.Builder(this)
            .setTitle("Play Web Video")
            .setView(input)
            .setPositiveButton("Play") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http")) {
                    playWebOrDlna(url)
                } else {
                    Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==========================
    // DLNA FULL UI
    // ==========================
    private fun openDlnaServers() {
        if (dlnaBrowser == null) dlnaBrowser = DlnaBrowser(this)

        Toast.makeText(this, "Scanning DLNA devices...", Toast.LENGTH_SHORT).show()

        dlnaBrowser?.start { servers ->
            dlnaServers = servers
        }

        scope.launch {
            delay(1200)
            if (dlnaServers.isEmpty()) {
                Toast.makeText(this@MainActivity, "No DLNA servers found", Toast.LENGTH_SHORT).show()
            } else {
                showServerPicker()
            }
        }
    }

    private fun showServerPicker() {
        val names = dlnaServers.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("DLNA Servers")
            .setItems(names) { _, which ->
                dlnaCurrentServer = dlnaServers[which]
                dlnaPathStack.clear()
                dlnaPathStack.add("0")
                browseDlna("0")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun browseDlna(objectId: String) {
        val server = dlnaCurrentServer ?: return
        Toast.makeText(this, "Loading DLNA folder...", Toast.LENGTH_SHORT).show()

        scope.launch {
            dlnaBrowser?.browse(server, objectId) { entries ->
                runOnUiThread {
                    showDlnaEntries(entries)
                }
            }
        }
    }

    private fun showDlnaEntries(entries: List<DlnaEntry>) {
        val titles = entries.map {
            if (it.isFolder) "📁 ${it.title}" else "🎬 ${it.title}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("DLNA Browser")
            .setItems(titles) { _, which ->
                val entry = entries[which]
                if (entry.isFolder) {
                    dlnaPathStack.add(entry.objectId)
                    browseDlna(entry.objectId)
                } else {
                    val url = entry.url
                    if (url != null) {
                        playWebOrDlna(url)
                    } else {
                        Toast.makeText(this, "No playable URL", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton("Back") { _, _ ->
                if (dlnaPathStack.size > 1) {
                    dlnaPathStack.removeLast()
                    browseDlna(dlnaPathStack.last())
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        savePlaybackState()
    }

    override fun onStop() {
        super.onStop()
        savePlaybackState()
    }

    private fun savePlaybackState() {
        val p = player ?: return
        prefs.edit().putLong("last_pos", p.currentPosition).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        savePlaybackState()
        player?.release()
        player = null
        dlnaBrowser?.stop()
        scope.cancel()
    }
}
