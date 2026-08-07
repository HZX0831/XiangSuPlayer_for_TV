package com.cutedino.xiangsuplayer.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.focus.DPadFocusHelper
import com.cutedino.xiangsuplayer.databinding.LayoutMiniPlayerBinding
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity

object MiniPlayerHelper {

    fun setup(activity: AppCompatActivity, binding: LayoutMiniPlayerBinding) {
        DPadFocusHelper.setupFocusScale(binding.miniPlayer, 1.02f)
        DPadFocusHelper.setupFocusScale(binding.btnMiniPlayMode)
        DPadFocusHelper.setupFocusScale(binding.btnMiniPlayPause)
        DPadFocusHelper.setupFocusScale(binding.btnMiniNext)

        binding.btnMiniPlayPause.setOnClickListener {
            PlayerController.togglePlayPause()
        }

        binding.btnMiniNext.setOnClickListener {
            PlayerController.playNext()
        }

        binding.btnMiniPlayMode.setOnClickListener {
            PlayerController.togglePlayMode()
        }

        binding.miniPlayer.setOnClickListener {
            val intent = Intent(activity, PlayerActivity::class.java)
            activity.startActivity(intent)
        }

        PlayerController.currentSong.observe(activity) { song ->
            if (song != null) {
                binding.miniTitle.text = song.title
                binding.miniArtist.text = "${song.artist} • ${song.album}"
                if (song.coverUrl.isNotEmpty()) {
                    binding.miniCover.load(song.coverUrl)
                }
            }
        }

        PlayerController.isPlaying.observe(activity) { isPlaying ->
            binding.btnMiniPlayPause.text = if (isPlaying) " 暂停" else " 播放"
            val iconRes = if (isPlaying) R.drawable.ic_pause_line else R.drawable.ic_play_compact
            binding.btnMiniPlayPause.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        }

        PlayerController.playMode.observe(activity) { mode ->
            binding.btnMiniPlayMode.text = " ${mode.displayName.take(2)}"
            val modeIconRes = when (mode) {
                PlayMode.SEQUENCE -> R.drawable.ic_mode_seq_line
                PlayMode.SHUFFLE -> R.drawable.ic_mode_shuffle_line
                PlayMode.REPEAT_ONE -> R.drawable.ic_mode_repeat_line
            }
            binding.btnMiniPlayMode.setCompoundDrawablesWithIntrinsicBounds(modeIconRes, 0, 0, 0)
        }
    }
}
