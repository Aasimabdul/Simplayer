package com.simplayer.owncompany

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplayer.owncompany.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private var videoList = mutableListOf<VideoModel>()
    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true

    private lateinit var audioManager: AudioManager
    private var currentBrightness = 0.5f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) loadVideos()
        else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        currentBrightness = window.attributes.screenBrightness
        if (currentBrightness < 0) currentBrightness = 0.5f

        setupRecyclerView()
        checkPermissions()
        setupPlayerUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.playerContainer.visibility == View.VISIBLE) {
                    closePlayer()
                } else {
                    finish()
                }
            }
        })
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadVideos() {
        videoList.clear()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                videoList.add(
                    VideoModel(
                        id,
                        it.getString(nameCol),
                        uri.toString(),
                        it.getLong(durationCol),
                        it.getLong(sizeCol)
                    )
                )
            }
        }
        binding.videoRecyclerView.adapter?.notifyDataSetChanged()
    }

    private fun setupRecyclerView() {
        binding.videoRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.videoRecyclerView.adapter = VideoAdapter(videoList) { video ->
            openPlayer(video)
        }
    }

    private fun setupPlayerUI() {
        binding.btnBack.setOnClickListener { closePlayer() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnFullscreen.setOnClickListener { toggleOrientation() }
        binding.btnSpeed.setOnClickListener { showSpeedDialog() }

        binding.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setupGestures()
    }

    private fun openPlayer(video: VideoModel) {
        binding.playerContainer.visibility = View.VISIBLE
        binding.tvVideoTitle.text = video.title
        hideSystemUI()

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(video.path)))
            prepare()
            play()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        binding.playerSeekBar.max = duration.toInt()
                        updateProgress()
                    }
                }
            })
        }
        binding.playerView.player = player
        showControls()
    }

    private fun closePlayer() {
        player?.release()
        player = null
        binding.playerContainer.visibility = View.GONE
        showSystemUI()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                it.play()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            showControls()
        }
    }

    private fun updateProgress() {
        player?.let {
            binding.playerSeekBar.progress = it.currentPosition.toInt()
            binding.tvTime.text = String.format(
                Locale.getDefault(),
                "%s / %s",
                formatTime(it.currentPosition),
                formatTime(it.duration)
            )
            if (it.isPlaying) {
                handler.postDelayed({ updateProgress() }, 1000)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    private fun showControls() {
        binding.controlsLayout.visibility = View.VISIBLE
        isControlsVisible = true
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
    }

    private val hideControlsRunnable = Runnable {
        binding.controlsLayout.visibility = View.GONE
        isControlsVisible = false
    }

    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isControlsVisible) hideControlsRunnable.run() else showControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = binding.gestureView.width
                if (e.x < viewWidth / 2) {
                    player?.seekTo((player?.currentPosition ?: 0) - 10000)
                    showIndicator(android.R.drawable.ic_media_rew, "-10s")
                } else {
                    player?.seekTo((player?.currentPosition ?: 0) + 10000)
                    showIndicator(android.R.drawable.ic_media_ff, "+10s")
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                if (e1 == null) return false
                val viewWidth = binding.gestureView.width
                val viewHeight = binding.gestureView.height
                
                if (abs(dY) > abs(dX)) {
                    if (e1.x < viewWidth / 2) {
                        // Brightness
                        val delta = dY / viewHeight
                        currentBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                        val layoutParams = window.attributes
                        layoutParams.screenBrightness = currentBrightness
                        window.attributes = layoutParams
                        showIndicator(android.R.drawable.ic_menu_compass, "Brightness ${(currentBrightness * 100).toInt()}%")
                    } else {
                        // Volume
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val delta = (dY / viewHeight * maxVol).toInt()
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (currentVol + delta).coerceIn(0, maxVol), 0)
                        val percent = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol * 100).toInt()
                        showIndicator(android.R.drawable.ic_lock_silent_mode_off, "Volume $percent%")
                    }
                }
                return true
            }
        })

        binding.gestureView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showIndicator(icon: Int, text: String) {
        binding.indicatorLayout.visibility = View.VISIBLE
        binding.indicatorIcon.setImageResource(icon)
        binding.indicatorText.text = text
        handler.removeCallbacks(hideIndicatorRunnable)
        handler.postDelayed(hideIndicatorRunnable, 1000)
    }

    private val hideIndicatorRunnable = Runnable {
        binding.indicatorLayout.visibility = View.GONE
    }

    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "1.0x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                player?.setPlaybackSpeed(speedValues[which])
                Toast.makeText(this, "Speed: ${speeds[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
