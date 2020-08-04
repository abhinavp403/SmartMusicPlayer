package com.dev.abhinav.smartmusicplayer

import android.content.Context
import android.view.KeyEvent
import android.widget.MediaController

class MusicController(context: Context?, boolean: Boolean = false) : MediaController(context, boolean) {

    override fun hide() {}

    override fun dispatchKeyEvent(event: KeyEvent) : Boolean {
        val keyCode = event.keyCode
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            (context as MainActivity).onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
