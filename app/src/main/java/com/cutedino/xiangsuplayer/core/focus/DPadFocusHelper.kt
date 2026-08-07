package com.cutedino.xiangsuplayer.core.focus

import android.view.View

object DPadFocusHelper {

    fun setupFocusScale(view: View, scale: Float = 1.03f, onFocusChange: ((View, Boolean) -> Unit)? = null) {
        view.isFocusable = true
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(150)
                    .start()
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            onFocusChange?.invoke(v, hasFocus)
        }
    }
}
