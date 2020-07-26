package com.dev.abhinav.smartmusicplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import java.util.*

class SongAdapter(context: Context, private val songs: ArrayList<Song>) : BaseAdapter() {
    private val songInf: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return songs.size
    }

    override fun getItem(arg0: Int): Any? {
        return null
    }

    override fun getItemId(arg0: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val songLay = songInf.inflate(R.layout.custom_list, parent, false)
        val imageView = songLay.findViewById<View>(R.id.logo) as ImageView
        val songView =  songLay.findViewById<View>(R.id.song) as TextView
        val artistView = songLay.findViewById<View>(R.id.artist) as TextView
        val durationView = songLay.findViewById<View>(R.id.duration) as TextView
        val currSong = songs[position]
        imageView.setImageResource(currSong.cover)
        songView.text = currSong.title
        artistView.text = currSong.artist
        durationView.text = currSong.duration
        songLay.tag = position
        return songLay
    }
}