package com.cutedino.xiangsuplayer.ui.main

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.databinding.FragmentHistoryBinding
import com.cutedino.xiangsuplayer.databinding.ItemSongCardBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyList = mutableListOf<Song>()
    private lateinit var historyAdapter: HistoryAdapter

    var onReturnToTopNav: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFocus()
        setupListeners()
        setupRecyclerView()

        loadHistory()
    }

    fun focusFirst() {
        val b = _binding ?: return
        b.btnPlayAllHistory.requestFocus()
    }

    private fun setupFocus() {
        val b = _binding ?: return
        DPadFocusHelper.setupFocusScale(b.btnPlayAllHistory)
        DPadFocusHelper.setupFocusScale(b.btnClearHistory)
    }

    private fun setupListeners() {
        val b = _binding ?: return

        b.btnPlayAllHistory.setOnClickListener {
            if (historyList.isNotEmpty()) {
                PlayerController.playSong(historyList.first(), historyList)
                Toast.makeText(context, "开始播放全部历史记录", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "暂无可播放的历史记录", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnClearHistory.setOnClickListener {
            PlayerController.clearHistory()
            loadHistory()
            Toast.makeText(context, "播放历史已成功清空", Toast.LENGTH_SHORT).show()
        }

        b.btnPlayAllHistory.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }

        b.btnClearHistory.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onReturnToTopNav?.invoke()
                true
            } else false
        }
    }

    private fun setupRecyclerView() {
        val b = _binding ?: return
        historyAdapter = HistoryAdapter(
            historyList,
            onItemClick = { song ->
                PlayerController.playSong(song)
                Toast.makeText(context, "正在播放: ${song.title}", Toast.LENGTH_SHORT).show()
            },
            onItemDelete = { song ->
                PlayerController.removeFromHistory(song)
                loadHistory()
                Toast.makeText(context, "已删除历史记录: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        b.rvHistoryList.layoutManager = LinearLayoutManager(context)
        b.rvHistoryList.adapter = historyAdapter
    }

    fun loadHistory() {
        val b = _binding ?: return
        historyList.clear()
        historyList.addAll(PlayerController.getHistory())

        if (historyList.isEmpty()) {
            b.tvHistoryEmpty.visibility = View.VISIBLE
            b.rvHistoryList.visibility = View.GONE
            b.tvHistorySubtitle.text = "共 0 首历史记录"
        } else {
            b.tvHistoryEmpty.visibility = View.GONE
            b.rvHistoryList.visibility = View.VISIBLE
            b.tvHistorySubtitle.text = "共 ${historyList.size} 首历史记录 • 按时间倒序"
        }
        historyAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class HistoryAdapter(
        private val list: List<Song>,
        private val onItemClick: (Song) -> Unit,
        private val onItemDelete: (Song) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

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
            holder.binding.cardBadge.text = "HIS"
            if (song.coverUrl.isNotEmpty()) {
                holder.binding.cardCover.load(song.coverUrl)
            }

            holder.binding.btnCardDelete.visibility = View.VISIBLE
            DPadFocusHelper.setupFocusScale(holder.binding.btnCardDelete, 1.15f)

            DPadFocusHelper.setupFocusScale(holder.itemView, 1.03f)
            holder.itemView.setOnClickListener { onItemClick(song) }
            holder.binding.btnCardDelete.setOnClickListener { onItemDelete(song) }

            holder.itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0) {
                    val b = _binding ?: return@setOnKeyListener false
                    b.btnPlayAllHistory.requestFocus()
                    true
                } else false
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
