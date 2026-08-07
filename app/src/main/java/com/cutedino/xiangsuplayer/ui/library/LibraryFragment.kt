package com.cutedino.xiangsuplayer.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.databinding.FragmentLibraryBinding
import com.cutedino.xiangsuplayer.databinding.ItemPlaylistCardBinding
import com.cutedino.xiangsuplayer.databinding.ItemSongCardBinding
import kotlinx.coroutines.launch
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmString

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val userPlaylists = mutableListOf<Playlist>()
    private lateinit var playlistAdapter: PlaylistAdapter

    private val favoriteSongs = mutableListOf<Song>()
    private lateinit var favAdapter: LibraryAdapter

    var onReturnToTopNav: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupDirectionalLocks()
        refreshNeteasePlaylists()
    }

    fun focusFirst() {
        val b = _binding ?: return
        if (userPlaylists.isNotEmpty()) {
            b.rvNeteasePlaylists.requestFocus()
        } else {
            b.rvFavorites.requestFocus()
        }
    }

    private fun setupDirectionalLocks() {
        val b = _binding ?: return
        b.rvNeteasePlaylists.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }
    }

    private fun setupRecyclerViews() {
        val b = _binding ?: return
        playlistAdapter = PlaylistAdapter(userPlaylists) { playlist ->
            openPlaylistDetail(playlist)
        }
        b.rvNeteasePlaylists.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvNeteasePlaylists.adapter = playlistAdapter

        favAdapter = LibraryAdapter(favoriteSongs) { song ->
            PlayerController.playSong(song, favoriteSongs)
            Toast.makeText(context, "正在播放: ${song.title}", Toast.LENGTH_SHORT).show()
        }
        b.rvFavorites.layoutManager = LinearLayoutManager(context)
        b.rvFavorites.adapter = favAdapter
    }

    fun refreshNeteasePlaylists() {
        val b = _binding ?: return
        if (!NcmApi.isLogin) {
            userPlaylists.clear()
            playlistAdapter.notifyDataSetChanged()
            b.tvLoginTip.visibility = View.VISIBLE
            return
        }

        b.tvLoginTip.visibility = View.GONE
        lifecycleScope.launch {
            val _b = _binding ?: return@launch
            val uid = NcmApi.userId
            if (uid <= 0) return@launch

            val res = NcmApi.userPlaylist(uid.toString())
            val list = res.getOrNull()?.ncmList("playlist") ?: emptyList()

            userPlaylists.clear()
            for (item in list) {
                val map = item as? Map<*, *> ?: continue
                @Suppress("UNCHECKED_CAST")
                val songMap = map as Map<String, Any?>
                val id = songMap.ncmLong("id").toString()
                val name = songMap.ncmString("name")
                val cover = songMap.ncmString("coverImgUrl")
                val count = songMap.ncmLong("trackCount").toInt()
                userPlaylists.add(Playlist(id = id, name = name, coverUrl = cover, trackCount = count))
            }

            if (_binding != null) {
                playlistAdapter.notifyDataSetChanged()
            }
        }
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

    inner class LibraryAdapter(
        private val list: List<Song>,
        private val onItemClick: (Song) -> Unit
    ) : RecyclerView.Adapter<LibraryAdapter.VH>() {

        inner class VH(val binding: ItemSongCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSongCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song = list[position]
            holder.binding.cardTitle.text = song.title
            holder.binding.cardSubtitle.text = "${song.artist} • ${song.album}"
            holder.binding.cardBadge.text = "FAV"
            if (song.coverUrl.isNotEmpty()) {
                holder.binding.cardCover.load(song.coverUrl)
            }

            DPadFocusHelper.setupFocusScale(holder.itemView, 1.03f)
            holder.itemView.setOnClickListener { onItemClick(song) }
        }

        override fun getItemCount(): Int = list.size
    }
}
