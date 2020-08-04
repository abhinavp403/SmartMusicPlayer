package com.dev.abhinav.smartmusicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.MediaController
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

open class MainActivity : AppCompatActivity(), MediaController.MediaPlayerControl {
    private lateinit var songList: ArrayList<Song>
    private lateinit var songView: ListView
    private var musicSrv: MusicService? = null
    private var playIntent: Intent? = null
    private var musicBound = false
    private lateinit var controller: MediaController
    private var paused = false
    private var playbackPaused = false
    private lateinit var globalVariable: GlobalClass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        globalVariable = this.applicationContext as GlobalClass
        songList = ArrayList()
        songView = findViewById(R.id.songList)

        appExternalStoragePermission()
        controller = MusicController(this)
        setController()
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
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    override fun onResume() {
        super.onResume()
        if (paused) {
            setController()
            paused = false
        }
    }

    override fun onStop() {
        controller.hide()
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.switchId)
        item.title = "Voice Enabled = " + globalVariable.mode
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.switchId) {
            if (globalVariable.mode == "ON") {
                globalVariable.mode = "OFF"
                item.title = "Voice Enabled = " + globalVariable.mode
            } else {
                globalVariable.mode = "ON"
                item.title = "Voice Enabled = " + globalVariable.mode
            }
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
    }

    fun songPicked(view: View) {
        musicSrv!!.setSong(view.tag.toString().toInt())
        musicSrv!!.playSong()
        val intent = Intent(this@MainActivity, SmartPlayerActivity::class.java)
        intent.putExtra("title", songList[view.tag.toString().toInt()].title)
        intent.putExtra("artist", songList[view.tag.toString().toInt()].artist)
        intent.putExtra("duration", songList[view.tag.toString().toInt()].seconds)
        startActivity(intent)
        if(playbackPaused) {
            setController()
            playbackPaused = false
        }
        controller.show(0)
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
        val musicCursor: Cursor = musicResolver.query(musicUri, null, null, null, null)!!
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

    private fun setController() {
        controller.setPrevNextListeners({ playNext() }) { playPrev() }
        controller.setMediaPlayer(this)
        controller.setAnchorView(findViewById(R.id.songList))
        controller.isEnabled = true
    }

    private fun playNext() {
        musicSrv!!.playNext()
        if(playbackPaused) {
            setController()
            playbackPaused = false
        }
        controller.show(0)
    }

    private fun playPrev() {
        musicSrv!!.playPrev()
        if(playbackPaused) {
            setController()
            playbackPaused = false
        }
        controller.show(0)
    }

    override fun isPlaying(): Boolean {
        return if (musicSrv != null && musicBound)
            musicSrv!!.isPng()
        else
            false
    }

    override fun canSeekForward(): Boolean {
        return true
    }

    override fun getDuration(): Int {
        return if (musicSrv != null && musicBound && musicSrv!!.isPng())
            musicSrv!!.getDuration()
        else
            0
    }

    override fun pause() {
        playbackPaused = true
        musicSrv!!.pausePlayer()
    }

    override fun seekTo(pos: Int) {
        musicSrv!!.seek(pos)
    }

    override fun start() {
        musicSrv!!.go()
    }

    override fun getBufferPercentage(): Int {
        return (musicSrv!!.getSeek()*100) / musicSrv!!.getDuration()
    }

    override fun getCurrentPosition(): Int {
        return if (musicSrv != null && musicBound && !musicSrv!!.isPng())
            musicSrv!!.getPos()
        else
            0
    }

    override fun canSeekBackward(): Boolean {
        return true
    }

    override fun getAudioSessionId(): Int {
        return 0
    }

    override fun canPause(): Boolean {
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        stopService(playIntent)
        musicSrv = null
    }
}