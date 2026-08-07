package com.cutedino.xiangsuplayer.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.databinding.ItemSongCardBinding

class HistoryDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val historyList = PlayerController.getHistory()
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(com.cutedino.xiangsuplayer.R.layout.dialog_history, null)

        val rv = view.findViewById<RecyclerView>(com.cutedino.xiangsuplayer.R.id.rvHistoryList)
        val tvEmpty = view.findViewById<android.widget.TextView>(com.cutedino.xiangsuplayer.R.id.tvHistoryEmpty)

        if (historyList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rv.visibility = View.VISIBLE
            rv.layoutManager = LinearLayoutManager(context)
            rv.adapter = HistoryAdapter(historyList) { song ->
                PlayerController.playSong(song, historyList)
                Toast.makeText(context, "开始播放: ${song.title}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("📜 全局播放历史")
            .setView(view)
            .setNegativeButton("关闭", null)
            .create()
    }

    class HistoryAdapter(
        private val list: List<Song>,
        private val onItemClick: (Song) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        class VH(val binding: ItemSongCardBinding) : RecyclerView.ViewHolder(binding.root)

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

            DPadFocusHelper.setupFocusScale(holder.itemView, 1.03f)
            holder.itemView.setOnClickListener { onItemClick(song) }
        }

        override fun getItemCount(): Int = list.size
    }
}
