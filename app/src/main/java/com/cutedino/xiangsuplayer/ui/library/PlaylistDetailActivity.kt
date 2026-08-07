package com.cutedino.xiangsuplayer.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.databinding.ActivityPlaylistDetailBinding
import com.cutedino.xiangsuplayer.databinding.ItemPlaylistSongGridBinding
import com.cutedino.xiangsuplayer.ui.main.MiniPlayerHelper
import kotlinx.coroutines.launch
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import kotlin.math.ceil

class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistDetailBinding
    private val allSongs = mutableListOf<Song>()
    private val currentPageSongs = mutableListOf<Song>()
    private lateinit var gridAdapter: GridAdapter

    private var currentPage = 1
    private val pageSize = 10
    private var totalPages = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadWallpaper()
        updateWallpaperOverlayOpacity()

        val playlistId = intent.getStringExtra("EXTRA_PLAYLIST_ID") ?: ""
        val playlistName = intent.getStringExtra("EXTRA_PLAYLIST_NAME") ?: "歌单"
        val playlistCover = intent.getStringExtra("EXTRA_PLAYLIST_COVER") ?: ""

        binding.tvDetailName.text = playlistName
        if (playlistCover.isNotEmpty()) {
            binding.ivDetailCover.load(playlistCover)
        }

        setupFocus()
        setupListeners()
        setupRecyclerView()
        MiniPlayerHelper.setup(this, binding.layoutMiniPlayer)

        if (playlistId.isNotEmpty()) {
            loadPlaylistSongs(playlistId)
        }
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

    private fun setupFocus() {
        DPadFocusHelper.setupFocusScale(binding.btnPlayAll)
        DPadFocusHelper.setupFocusScale(binding.btnBack)
        DPadFocusHelper.setupFocusScale(binding.btnPrevPage)
        DPadFocusHelper.setupFocusScale(binding.btnNextPage)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPlayAll.setOnClickListener {
            if (allSongs.isNotEmpty()) {
                PlayerController.playSong(allSongs.first(), allSongs)
                Toast.makeText(this, "已开始播放歌单全部歌曲 (${allSongs.size} 首)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                updatePageContent()
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                updatePageContent()
            }
        }
    }

    private fun setupRecyclerView() {
        gridAdapter = GridAdapter(currentPageSongs) { song ->
            PlayerController.playSong(song, allSongs)
            Toast.makeText(this, "正在播放: ${song.title}", Toast.LENGTH_SHORT).show()
        }
        binding.rvGridSongs.layoutManager = GridLayoutManager(this, 5)
        binding.rvGridSongs.adapter = gridAdapter
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPlaylistSongs(playlistId: String) {
        lifecycleScope.launch {
            val res = NcmApi.playlistDetail(playlistId)
            val trackList = res.getOrNull()?.ncmObj("playlist")?.ncmList("tracks") ?: emptyList()

            allSongs.clear()
            for (item in trackList) {
                val map = item as? Map<String, Any?> ?: continue
                val id = map.ncmLong("id").toString()
                val name = map.ncmString("name")
                val arList = map.ncmList("ar")
                val artist = if (arList.isNotEmpty()) (arList[0] as? Map<String, Any?>)?.ncmString("name") ?: "未知歌手" else "未知歌手"
                val alObj = map.ncmObj("al")
                val album = alObj.ncmString("name")
                val picUrl = alObj.ncmString("picUrl")
                allSongs.add(Song(id = id, title = name, artist = artist, album = album, coverUrl = picUrl, durationMs = 0L))
            }

            binding.tvDetailMeta.text = "共 ${allSongs.size} 首歌曲"
            totalPages = ceil(allSongs.size.toDouble() / pageSize).toInt().coerceAtLeast(1)
            currentPage = 1

            updatePageContent()
        }
    }

    private fun updatePageContent() {
        val start = (currentPage - 1) * pageSize
        val end = (start + pageSize).coerceAtMost(allSongs.size)

        currentPageSongs.clear()
        if (start < allSongs.size) {
            currentPageSongs.addAll(allSongs.subList(start, end))
        }
        gridAdapter.notifyDataSetChanged()

        binding.tvGridPageInfo.text = "第 $currentPage / $totalPages 页"
        binding.btnPrevPage.isEnabled = currentPage > 1
        binding.btnNextPage.isEnabled = currentPage < totalPages
    }

    inner class GridAdapter(
        private val list: List<Song>,
        private val onItemClick: (Song) -> Unit
    ) : RecyclerView.Adapter<GridAdapter.VH>() {

        inner class VH(val binding: ItemPlaylistSongGridBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemPlaylistSongGridBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song = list[position]
            holder.binding.songTitle.text = song.title
            holder.binding.songArtist.text = "${song.artist} • ${song.album}"
            if (song.coverUrl.isNotEmpty()) {
                holder.binding.songCover.load(song.coverUrl)
            }

            DPadFocusHelper.setupFocusScale(holder.itemView, 1.03f)
            holder.itemView.setOnClickListener { onItemClick(song) }
        }

        override fun getItemCount(): Int = list.size
    }
}
