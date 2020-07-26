package com.dev.abhinav.smartmusicplayer

import android.Manifest
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.dev.abhinav.smartmusicplayer.MusicService.MusicBinder
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.util.*
import kotlin.collections.ArrayList

open class MainActivity : AppCompatActivity() {
    private lateinit var songList: ArrayList<Song>
    private lateinit var songView: ListView
    private var mode:String = "OFF"
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var musicSrv: MusicService? = null
    private var playIntent: Intent? = null
    private var musicBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        editor = preferences.edit()
        songList = ArrayList()
        songView = findViewById(R.id.songList)

        appExternalStoragePermission()
    }

    override fun onStart() {
        super.onStart()
        if (playIntent == null) {
            playIntent = Intent(this, MusicService::class.java)
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE)
            startService(playIntent)
        }
    }

    override fun onDestroy() {
        stopService(playIntent)
        musicSrv = null
        editor.clear().commit()
        super.onDestroy()
    }

    /*override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.switchId) {
            item.title = preferences.getString("val", "Voice Enabled: OFF")
            val itemOne = preferences.getString("val", "Voice Enabled: OFF")
            val itemMode = preferences.getString("mode", "OFF")
            Log.d("kkk1", itemMode)
            if (itemMode == "ON") {
                mode = "OFF"
                item.title = itemOne
                editor.putString("val", item.title as String)
                editor.putString("mode", mode)
            } else {
                mode = "ON"
                item.title = "Voice Enabled = ON"
                editor.putString("val", item.title as String)
                editor.putString("mode", mode)
            }
            editor.apply()
            return true
        }
        if(item.itemId == R.id.commands) {
            val dialog = MaterialDialog(this).noAutoDismiss().customView(R.layout.custom_dialog)
            dialog.findViewById<TextView>(R.id.ok_button).setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }*/

    fun songPicked(view: View) {
        musicSrv!!.setSong(view.tag.toString().toInt())
        musicSrv!!.playSong()
        val intent = Intent(this@MainActivity, SmartPlayerActivity::class.java)
        intent.putExtra("title", songList[view.tag.toString().toInt()].title)
        intent.putExtra("artist", songList[view.tag.toString().toInt()].artist)
        intent.putExtra("duration", songList[view.tag.toString().toInt()].seconds)
        startActivity(intent)
    }

    private val musicConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicBinder
            musicSrv = binder.service
            musicSrv!!.setList(songList)
            musicBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            musicBound = false
        }
    }

    private fun appExternalStoragePermission() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    displaySongNames()
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                }

                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {
                    token!!.continuePermissionRequest()
                }
            }).check()
    }

    private fun displaySongNames() {
        val musicResolver = contentResolver
        val musicUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val musicCursor: Cursor = musicResolver.query(musicUri, null, null, null, null)
        if (musicCursor.moveToFirst()) {
            val idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val timeColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            do {
                val thisId = musicCursor.getLong(idColumn)
                val thisTitle = musicCursor.getString(titleColumn)
                val thisArtist = musicCursor.getString(artistColumn)
                val thisCover = R.drawable.cover
                val thisDuration = musicCursor.getInt(timeColumn)
                songList.add(Song(thisId, thisTitle, thisArtist, thisCover, thisDuration, createTimeLabel(thisDuration)))
            } while (musicCursor.moveToNext())
        }

        Collections.sort(songList, object : Comparator<Song?> {
            override fun compare(a: Song?, b: Song?): Int {
                return a!!.title.compareTo(b!!.title)
            }
        })

        val arrayAdapter = SongAdapter(this, songList)
        songView.adapter = arrayAdapter
    }

    private fun createTimeLabel(time: Int): String {
        var timeLabel: String
        val min = time / 1000 / 60
        val sec = time / 1000 % 60
        timeLabel = "$min:"
        if(sec < 10) timeLabel += "0"
        timeLabel += sec
        return timeLabel
    }
}