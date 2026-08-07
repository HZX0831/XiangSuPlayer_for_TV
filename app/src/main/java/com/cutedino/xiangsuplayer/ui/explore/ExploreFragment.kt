package com.cutedino.xiangsuplayer.ui.explore

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.databinding.FragmentExploreBinding
import com.cutedino.xiangsuplayer.databinding.ItemPlaylistCardBinding
import com.cutedino.xiangsuplayer.databinding.ItemPlaylistSongGridBinding
import com.cutedino.xiangsuplayer.ui.library.PlaylistDetailActivity
import kotlinx.coroutines.launch
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import kotlin.math.ceil

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private val recommendPlaylists = mutableListOf<Playlist>()
    private lateinit var playlistAdapter: PlaylistAdapter

    private val allRecommendSongs = mutableListOf<Song>()
    private val currentPageSongs = mutableListOf<Song>()
    private lateinit var gridAdapter: GridAdapter

    private var currentPage = 1
    private val pageSize = 10
    private var totalPages = 1

    var onReturnToTopNav: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFocus()
        setupListeners()
        setupRecyclerViews()

        loadRecommendations()
    }

    fun focusFirst() {
        val b = _binding ?: return
        if (recommendPlaylists.isNotEmpty()) {
            b.rvRecommendPlaylists.requestFocus()
        } else {
            b.btnPlayDailyAll.requestFocus()
        }
    }

    private fun setupFocus() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.btnPlayDailyAll)
        DPadFocusHelper.setupFocusScale(b.btnRefreshExplore)
        DPadFocusHelper.setupFocusScale(b.btnPrevPage)
        DPadFocusHelper.setupFocusScale(b.btnNextPage)
    }

    private fun setupListeners() {
        val b = _binding ?: return

        b.btnRefreshExplore.setOnClickListener {
            loadRecommendations()
            Toast.makeText(context, "已刷新个性化推荐内容", Toast.LENGTH_SHORT).show()
        }

        b.btnPlayDailyAll.setOnClickListener {
            if (allRecommendSongs.isNotEmpty()) {
                PlayerController.playSong(allRecommendSongs.first(), allRecommendSongs)
                Toast.makeText(context, "开始播放每日推荐全部歌曲 (${allRecommendSongs.size} 首)", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                updateGridPageContent()
            }
        }

        b.btnNextPage.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                updateGridPageContent()
            }
        }

        b.rvRecommendPlaylists.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }
    }

    private fun setupRecyclerViews() {
        val b = _binding ?: return
        playlistAdapter = PlaylistAdapter(recommendPlaylists) { playlist ->
            openPlaylistDetail(playlist)
        }
        b.rvRecommendPlaylists.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvRecommendPlaylists.adapter = playlistAdapter

        gridAdapter = GridAdapter(currentPageSongs) { song ->
            PlayerController.playSong(song, allRecommendSongs)
            Toast.makeText(context, "正在播放: ${song.title}", Toast.LENGTH_SHORT).show()
        }
        b.rvExploreGrid.layoutManager = GridLayoutManager(context, 5)
        b.rvExploreGrid.adapter = gridAdapter
    }

    fun loadRecommendations() {
        loadPersonalizedPlaylists()
        loadDailySongs()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPersonalizedPlaylists() {
        lifecycleScope.launch {
            val res = NcmApi.personalizedPlaylists(limit = 10)
            val list = res.getOrNull()?.ncmList("result") ?: emptyList()

            recommendPlaylists.clear()
            for (item in list) {
                val map = item as? Map<*, *> ?: continue
                val songMap = map as Map<String, Any?>
                val id = songMap.ncmLong("id").toString()
                val name = songMap.ncmString("name")
                val cover = songMap.ncmString("picUrl")
                val count = songMap.ncmLong("trackCount").toInt()
                recommendPlaylists.add(Playlist(id = id, name = name, coverUrl = cover, trackCount = count))
            }

            if (_binding != null) {
                playlistAdapter.notifyDataSetChanged()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadDailySongs() {
        lifecycleScope.launch {
            val songs = if (NcmApi.isLogin) {
                val res = NcmApi.recommendSongs()
                val list = res.getOrNull()?.ncmObj("data")?.ncmList("dailySongs") ?: emptyList()
                val result = mutableListOf<Song>()
                for (item in list) {
                    val map = item as? Map<String, Any?> ?: continue
                    val id = map.ncmLong("id").toString()
                    val name = map.ncmString("name")
                    val arList = map.ncmList("ar")
                    val artist = if (arList.isNotEmpty()) (arList[0] as? Map<String, Any?>)?.ncmString("name") ?: "未知歌手" else "未知歌手"
                    val alObj = map.ncmObj("al")
                    val album = alObj.ncmString("name")
                    val picUrl = alObj.ncmString("picUrl")
                    result.add(Song(id = id, title = name, artist = artist, album = album, coverUrl = picUrl, durationMs = 0L))
                }
                result
            } else {
                val res = SoundSourceRepository.search("热歌榜")
                res.getOrNull() ?: emptyList()
            }

            allRecommendSongs.clear()
            allRecommendSongs.addAll(songs)
            totalPages = ceil(allRecommendSongs.size.toDouble() / pageSize).toInt().coerceAtLeast(1)
            currentPage = 1

            if (_binding != null) {
                updateGridPageContent()
            }
        }
    }

    private fun updateGridPageContent() {
        val b = _binding ?: return
        val start = (currentPage - 1) * pageSize
        val end = (start + pageSize).coerceAtMost(allRecommendSongs.size)

        currentPageSongs.clear()
        if (start < allRecommendSongs.size) {
            currentPageSongs.addAll(allRecommendSongs.subList(start, end))
        }
        gridAdapter.notifyDataSetChanged()

        b.tvGridPageInfo.text = "第 $currentPage / $totalPages 页 (共 ${allRecommendSongs.size} 首)"
        b.btnPrevPage.isEnabled = currentPage > 1
        b.btnNextPage.isEnabled = currentPage < totalPages
    }

    private fun openPlaylistDetail(playlist: Playlist) {
        val intent = Intent(context, PlaylistDetailActivity::class.java).apply {
            putExtra("EXTRA_PLAYLIST_ID", playlist.id)
            putExtra("EXTRA_PLAYLIST_NAME", playlist.name)
            putExtra("EXTRA_PLAYLIST_COVER", playlist.coverUrl)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class PlaylistAdapter(
        private val list: List<Playlist>,
        private val onItemClick: (Playlist) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

        inner class VH(val binding: ItemPlaylistCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemPlaylistCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val playlist = list[position]
            holder.binding.playlistName.text = playlist.name
            if (playlist.coverUrl.isNotEmpty()) {
                holder.binding.playlistCover.load(playlist.coverUrl)
            }

            DPadFocusHelper.setupFocusScale(holder.itemView, 1.03f)
            holder.itemView.setOnClickListener { onItemClick(playlist) }
        }

        override fun getItemCount(): Int = list.size
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
