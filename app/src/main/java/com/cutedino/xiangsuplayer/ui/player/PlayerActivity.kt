package com.cutedino.xiangsuplayer.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.databinding.ActivityPlayerBinding
import com.cutedino.xiangsuplayer.databinding.ItemLyricLineBinding
import com.cutedino.xiangsuplayer.databinding.ItemQueueSongCompactBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding

    private val lyricsList = mutableListOf<LyricLine>()
    private lateinit var lyricsAdapter: LyricsAdapter

    private val queueList = mutableListOf<Song>()
    private lateinit var queueAdapter: QueueAdapter

    private var activeIndex = -1
    private var isQueueExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadWallpaper()
        updateWallpaperOverlayOpacity()

        setupFocusScoping()
        setupListeners()
        setupRecyclerViews()
        setupBackHandler()

        observeState()

        binding.playerSeekBar.post {
            binding.playerSeekBar.requestFocus()
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isQueueExpanded) {
                    toggleQueueDrawer()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isQueueExpanded) {
            toggleQueueDrawer()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateWallpaperOverlayOpacity(percent: Int = SessionManager.wallpaperOpacityPercent) {
        val alpha = (percent / 100.0f).coerceIn(0.0f, 0.95f)
        binding.wallpaperOverlay.alpha = alpha
    }

    private fun loadWallpaper() {
        val customUrl = SessionManager.getWallpaperUrl(this)
        if (!customUrl.isNullOrBlank()) {
            binding.ivBingWallpaper.load(customUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_default_wallpaper)
                error(R.drawable.bg_default_wallpaper)
            }
            return
        }

        binding.ivBingWallpaper.setImageResource(R.drawable.bg_default_wallpaper)

        val primaryBingUrl = "https://bing.biturl.top/?resolution=1920&format=image&index=0&mkt=zh-CN"
        binding.ivBingWallpaper.load(primaryBingUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_default_wallpaper)
            error(R.drawable.bg_default_wallpaper)
        }
    }

    private fun toggleQueueDrawer() {
        isQueueExpanded = !isQueueExpanded

        val drawerWidthPx = (380 * resources.displayMetrics.density).toInt()
        val targetMarginEnd = if (isQueueExpanded) drawerWidthPx else 0

        if (isQueueExpanded) {
            binding.btnToggleQueue.visibility = View.GONE
            binding.layoutQueueDrawer.visibility = View.VISIBLE
            binding.layoutQueueDrawer.translationX = drawerWidthPx.toFloat()
            binding.layoutQueueDrawer.alpha = 1f
            binding.layoutQueueDrawer.animate()
                .translationX(0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.layoutQueueDrawer.animate()
                .translationX(drawerWidthPx.toFloat())
                .setDuration(250)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }

        val currentMarginEnd = (binding.layoutMainPlayerContent.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
        val animator = ValueAnimator.ofInt(currentMarginEnd, targetMarginEnd)
        animator.duration = 250
        animator.interpolator = if (isQueueExpanded) DecelerateInterpolator() else AccelerateInterpolator()
        animator.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            val params = binding.layoutMainPlayerContent.layoutParams as ViewGroup.MarginLayoutParams
            params.marginEnd = value
            binding.layoutMainPlayerContent.layoutParams = params
        }
        if (!isQueueExpanded) {
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.layoutQueueDrawer.visibility = View.GONE
                    binding.btnToggleQueue.visibility = View.VISIBLE
                    binding.btnToggleQueue.requestFocus()
                }
            })
        } else {
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.recyclerQueue.post {
                        scrollToCurrentSongCentered()
                        binding.recyclerQueue.requestFocus()
                    }
                }
            })
        }
        animator.start()
    }

    private fun scrollToCurrentSongCentered() {
        val current = PlayerController.currentSong.value ?: return
        val index = queueList.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val layoutManager = binding.recyclerQueue.layoutManager as? LinearLayoutManager ?: return
            val heightPx = binding.recyclerQueue.height.takeIf { it > 0 } ?: (400 * resources.displayMetrics.density).toInt()
            val itemHeightPx = (60 * resources.displayMetrics.density).toInt()
            val offset = (heightPx - itemHeightPx) / 2
            layoutManager.scrollToPositionWithOffset(index, offset.coerceAtLeast(0))
        }
    }

    private fun setupFocusScoping() {
        DPadFocusHelper.setupFocusScale(binding.btnPrev)
        DPadFocusHelper.setupFocusScale(binding.btnPlayPause)
        DPadFocusHelper.setupFocusScale(binding.btnNext)
        DPadFocusHelper.setupFocusScale(binding.btnPlayMode)
        DPadFocusHelper.setupFocusScale(binding.btnToggleQueue)
        DPadFocusHelper.setupFocusScale(binding.btnCloseQueue)

        binding.playerSeekBar.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        PlayerController.seekRelative(-3000)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        PlayerController.seekRelative(3000)
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun setupListeners() {
        binding.btnPrev.setOnClickListener {
            PlayerController.playPrevious()
        }

        binding.btnPlayPause.setOnClickListener {
            PlayerController.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            PlayerController.playNext()
        }

        binding.btnPlayMode.setOnClickListener {
            PlayerController.togglePlayMode()
        }

        binding.btnToggleQueue.setOnClickListener {
            toggleQueueDrawer()
        }

        binding.btnCloseQueue.setOnClickListener {
            if (isQueueExpanded) {
                toggleQueueDrawer()
            }
        }
    }

    private fun setupRecyclerViews() {
        lyricsAdapter = LyricsAdapter(lyricsList)
        binding.rvLyrics.layoutManager = LinearLayoutManager(this)
        binding.rvLyrics.adapter = lyricsAdapter

        queueAdapter = QueueAdapter(queueList, { song ->
            PlayerController.playSong(song)
        }, { song ->
            PlayerController.insertAsNext(song)
            Toast.makeText(this, "已设为下一首播放: ${song.title}", Toast.LENGTH_SHORT).show()
        }, { song ->
            PlayerController.removeFromPlaylist(song)
        }, { hintText ->
            binding.tvQueueActionHint.text = hintText
        })
        binding.recyclerQueue.layoutManager = LinearLayoutManager(this)
        binding.recyclerQueue.adapter = queueAdapter
    }

    private fun observeState() {
        PlayerController.currentSong.observe(this) { song ->
            if (song != null) {
                binding.playerSongTitle.text = song.title
                binding.playerArtistName.text = "${song.artist} • ${song.album}"

                if (song.coverUrl.isNotEmpty()) {
                    binding.playerCover.load(song.coverUrl)
                    loadBlurBackgroundAndPalette(song.coverUrl)
                }

                val currentQ = SessionManager.getAudioQuality(this)
                binding.badgeQuality.text = "${currentQ.displayName} 无损"
                queueAdapter.notifyDataSetChanged()
                if (isQueueExpanded) {
                    scrollToCurrentSongCentered()
                }
            }
        }

        PlayerController.isPlaying.observe(this) { isPlaying ->
            binding.btnPlayPause.text = if (isPlaying) " 暂停" else " 播放"
            val iconRes = if (isPlaying) R.drawable.ic_pause_line else R.drawable.ic_play_compact
            binding.btnPlayPause.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        }

        PlayerController.playMode.observe(this) { mode ->
            binding.btnPlayMode.text = " ${mode.displayName}"
            val modeIconRes = when (mode) {
                PlayMode.SEQUENCE -> R.drawable.ic_mode_seq_line
                PlayMode.SHUFFLE -> R.drawable.ic_mode_shuffle_line
                PlayMode.REPEAT_ONE -> R.drawable.ic_mode_repeat_line
            }
            binding.btnPlayMode.setCompoundDrawablesWithIntrinsicBounds(modeIconRes, 0, 0, 0)
        }

        PlayerController.playlistLiveData.observe(this) { list ->
            queueList.clear()
            queueList.addAll(list)
            queueAdapter.notifyDataSetChanged()
            binding.tvQueueHeader.text = "播放队列 (${list.size}首)"
            binding.btnToggleQueue.text = " 队列 (${list.size}首)"
            if (isQueueExpanded) {
                scrollToCurrentSongCentered()
            }
        }

        PlayerController.lyrics.observe(this) { list ->
            lyricsList.clear()
            lyricsList.addAll(list)
            lyricsAdapter.notifyDataSetChanged()
            activeIndex = -1
        }

        lifecycleScope.launch {
            PlayerController.currentPosition.collect { pos ->
                val duration = PlayerController.duration.value.coerceAtLeast(1L)
                val progress = if (duration > 0) (pos * 1000 / duration).toInt() else 0
                binding.playerSeekBar.progress = progress

                binding.tvProgressTime.text = "${formatTime(pos)} / ${formatTime(duration)}"

                updateLyricsPosition(pos)
            }
        }
    }

    private fun updateLyricsPosition(pos: Long) {
        val list = lyricsList
        if (list.isEmpty()) return
        var active = -1
        for (i in list.indices) {
            if (pos >= list[i].timeMs) {
                active = i
            } else {
                break
            }
        }
        if (active != activeIndex) {
            activeIndex = active
            lyricsAdapter.notifyDataSetChanged()
            if (active >= 0) {
                val layoutManager = binding.rvLyrics.layoutManager as? LinearLayoutManager ?: return
                layoutManager.scrollToPositionWithOffset(active, 120)
            }
        }
    }

    private fun loadBlurBackgroundAndPalette(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(this@PlayerActivity)
                val req = ImageRequest.Builder(this@PlayerActivity)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req).drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap ?: return@launch
                val palette = Palette.from(bitmap).generate()
                val dominantColor = palette.getDominantColor(Color.parseColor("#1E293B"))

                withContext(Dispatchers.Main) {
                    binding.blurBg.load(bitmap)
                    binding.blurBg.setColorFilter(dominantColor)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    inner class LyricsAdapter(
        private val list: List<LyricLine>
    ) : RecyclerView.Adapter<LyricsAdapter.VH>() {

        inner class VH(val binding: ItemLyricLineBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemLyricLineBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val lyric = list[position]
            holder.binding.tvLyricText.text = lyric.text

            if (position == activeIndex) {
                holder.binding.tvLyricText.setTextColor(Color.parseColor("#FFA9F06A"))
                holder.binding.tvLyricText.textSize = 22f
            } else {
                holder.binding.tvLyricText.setTextColor(Color.parseColor("#94A3B8"))
                holder.binding.tvLyricText.textSize = 17f
            }
        }

        override fun getItemCount(): Int = list.size
    }

    inner class QueueAdapter(
        private val list: List<Song>,
        private val onItemClick: (Song) -> Unit,
        private val onItemNextPlay: (Song) -> Unit,
        private val onItemDelete: (Song) -> Unit,
        private val onActionFocused: (String) -> Unit
    ) : RecyclerView.Adapter<QueueAdapter.VH>() {

        inner class VH(val binding: ItemQueueSongCompactBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemQueueSongCompactBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song = list[position]
            holder.binding.tvQueueTitle.text = song.title
            holder.binding.tvQueueArtist.text = "${song.artist} • ${song.album}"

            val current = PlayerController.currentSong.value
            val isPlayingThis = current != null && song.id == current.id

            if (isPlayingThis) {
                holder.binding.tvQueueIndicator.visibility = View.VISIBLE
                holder.binding.tvQueueTitle.setTextColor(Color.parseColor("#FFA9F06A"))
                holder.binding.tvQueueArtist.setTextColor(Color.parseColor("#C0FFA9F0"))
            } else {
                holder.binding.tvQueueIndicator.visibility = View.GONE
                holder.binding.tvQueueTitle.setTextColor(Color.parseColor("#F8FAFC"))
                holder.binding.tvQueueArtist.setTextColor(Color.parseColor("#94A3B8"))
            }

            if (song.coverUrl.isNotEmpty()) {
                holder.binding.imgQueueCover.load(song.coverUrl)
            }

            DPadFocusHelper.setupFocusScale(holder.binding.btnQueueItemPlay) { _, hasFocus ->
                if (hasFocus) onActionFocused("即刻播放：点击重新播放此曲")
            }
            DPadFocusHelper.setupFocusScale(holder.binding.btnQueueItemNext) { _, hasFocus ->
                if (hasFocus) onActionFocused("插队播放：设为下一首优先播放")
            }
            DPadFocusHelper.setupFocusScale(holder.binding.btnQueueItemDelete) { _, hasFocus ->
                if (hasFocus) onActionFocused("移出队列：将此曲从列表移除")
            }

            holder.binding.btnQueueItemPlay.setOnClickListener { onItemClick(song) }
            holder.binding.btnQueueItemNext.setOnClickListener { onItemNextPlay(song) }
            holder.binding.btnQueueItemDelete.setOnClickListener { onItemDelete(song) }
        }

        override fun getItemCount(): Int = list.size
    }
}
