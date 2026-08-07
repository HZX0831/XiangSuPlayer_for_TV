package com.cutedino.xiangsuplayer.ui.search

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.databinding.FragmentSearchBinding
import com.cutedino.xiangsuplayer.databinding.ItemSongCardBinding
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val searchResults = mutableListOf<Song>()
    private lateinit var searchAdapter: SearchAdapter

    private val hotSongs = mutableListOf<Song>()
    private lateinit var hotAdapter: SearchAdapter

    var onReturnToTopNav: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFocus()
        setupListeners()
        setupRecyclerViews()

        loadSearchHistoryTags()
        loadHotSearchSuggestions()
    }

    fun focusSearchInput() {
        _binding?.etSearchQuery?.requestFocus()
    }

    private fun setupFocus() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.etSearchQuery)
        DPadFocusHelper.setupFocusScale(b.btnDoSearch)
        DPadFocusHelper.setupFocusScale(b.btnClearSearchHistory)
    }

    private fun setupListeners() {
        val b = _binding ?: return

        b.btnDoSearch.setOnClickListener {
            val q = b.etSearchQuery.text.toString().trim()
            if (q.isNotEmpty()) {
                performSearch(q)
            } else {
                Toast.makeText(context, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            }
        }

        b.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = b.etSearchQuery.text.toString().trim()
                if (q.isNotEmpty()) performSearch(q)
                true
            } else false
        }

        b.btnClearSearchHistory.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            SessionManager.clearSearchHistory(ctx)
            loadSearchHistoryTags()
            Toast.makeText(ctx, "搜索历史已清空", Toast.LENGTH_SHORT).show()
        }

        b.etSearchQuery.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }

        b.btnDoSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }
    }

    private fun setupRecyclerViews() {
        val b = _binding ?: return
        searchAdapter = SearchAdapter(searchResults) { song ->
            PlayerController.playSong(song, searchResults)
            Toast.makeText(context, "正在播放: ${song.title}", Toast.LENGTH_SHORT).show()
        }
        b.rvSearchResults.layoutManager = LinearLayoutManager(context)
        b.rvSearchResults.adapter = searchAdapter

        hotAdapter = SearchAdapter(hotSongs) { song ->
            PlayerController.playSong(song, hotSongs)
            Toast.makeText(context, "正在播放热搜歌曲: ${song.title}", Toast.LENGTH_SHORT).show()
        }
        b.rvHotSongs.layoutManager = LinearLayoutManager(context)
        b.rvHotSongs.adapter = hotAdapter
    }

    private fun loadSearchHistoryTags() {
        val b = _binding ?: return
        val ctx = context ?: return
        val historyList = SessionManager.getSearchHistory(ctx)
        b.layoutHistoryTags.removeAllViews()

        if (historyList.isEmpty()) {
            b.layoutHistorySection.visibility = View.GONE
            return
        }

        b.layoutHistorySection.visibility = View.VISIBLE
        for (tag in historyList) {
            val btn = Button(ctx).apply {
                text = tag
                textSize = 13f
                setTextColor(Color.parseColor("#E2E8F0"))
                setBackgroundResource(com.cutedino.xiangsuplayer.R.drawable.selector_card_bg)
                setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(8)
                }
            }
            DPadFocusHelper.setupFocusScale(btn, 1.05f)

            btn.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    _binding?.etSearchQuery?.requestFocus()
                    true
                } else false
            }

            btn.setOnClickListener {
                b.etSearchQuery.setText(tag)
                performSearch(tag)
            }
            b.layoutHistoryTags.addView(btn)
        }
    }

    private fun performSearch(query: String) {
        val b = _binding ?: return
        val ctx = context ?: return
        SessionManager.addSearchHistory(ctx, query)
        loadSearchHistoryTags()

        b.layoutHotSection.visibility = View.GONE
        b.rvSearchResults.visibility = View.VISIBLE

        lifecycleScope.launch {
            val res = SoundSourceRepository.search(query)
            val songs = res.getOrNull() ?: emptyList()

            searchResults.clear()
            searchResults.addAll(songs)
            if (_binding != null) {
                searchAdapter.notifyDataSetChanged()
                if (searchResults.isEmpty()) {
                    Toast.makeText(context, "未搜到相关歌曲，请尝试其它关键词", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadHotSearchSuggestions() {
        lifecycleScope.launch {
            val res = SoundSourceRepository.search("热门")
            val songs = res.getOrNull() ?: emptyList()
            hotSongs.clear()
            hotSongs.addAll(songs.take(8))

            if (_binding != null) {
                hotAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SearchAdapter(
        private val list: List<Song>,
        private val onItemClick: (Song) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.VH>() {

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
            holder.binding.cardBadge.text = "NET"
            if (song.coverUrl.isNotEmpty()) {
                holder.binding.cardCover.load(song.coverUrl)
            }

            DPadFocusHelper.setupFocusScale(holder.itemView, 1.03f)
            holder.itemView.setOnClickListener { onItemClick(song) }

            holder.itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0) {
                    val b = _binding ?: return@setOnKeyListener false
                    if (b.layoutHistorySection.visibility == View.VISIBLE && b.layoutHistoryTags.childCount > 0) {
                        b.layoutHistoryTags.getChildAt(0).requestFocus()
                    } else {
                        b.etSearchQuery.requestFocus()
                    }
                    true
                } else false
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
