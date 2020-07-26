package com.dev.abhinav.smartmusicplayer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import java.util.*

class MusicService : Service(), OnPreparedListener, MediaPlayer.OnErrorListener, OnCompletionListener {
    private var player: MediaPlayer? = null
    private var songs: ArrayList<Song>? = null
    private var pos = 0
    private val musicBind: IBinder = MusicBinder()
    private var songTitle = ""
    private var artistTitle = ""
    private var duration = 0
    private val NOTIFY_ID = 1
    private var shuffle = false
    private var rand: Random? = null
    private var serviceCallbacks: ServiceCallbacks? = null

    override fun onCreate() {
        super.onCreate()
        pos = 0
        player = MediaPlayer()
        initMusicPlayer()
        rand = Random()
    }

    override fun onDestroy() {
        stopForeground(true)
    }

    private fun initMusicPlayer() {
        player!!.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        player!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        player!!.setOnPreparedListener(this)
        player!!.setOnCompletionListener(this)
        player!!.setOnErrorListener(this)
    }

    fun setList(theSongs: ArrayList<Song>?) {
        songs = theSongs
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        return musicBind
    }

    override fun onUnbind(intent: Intent): Boolean {
        player!!.stop()
        player!!.release()
        return false
    }

    fun setCallbacks(callbacks: ServiceCallbacks) {
        serviceCallbacks = callbacks
    }

    fun playSong() {
        player!!.reset()
        val playSong = songs!![pos]
        val currSong: Long = playSong.id
        songTitle = playSong.title
        artistTitle = playSong.artist
        duration = playSong.seconds
        val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currSong)
        try {
            player!!.setDataSource(applicationContext, trackUri)
        } catch (e: Exception) {
            Log.e("MUSIC SERVICE", "Error setting data source", e)
        }
        player!!.prepareAsync()
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        if (mediaPlayer.currentPosition > 0) {
            mediaPlayer.reset()
            playNext()
            if (serviceCallbacks != null) {
                serviceCallbacks!!.setNextPrevScreen()
            }
        }
    }

    override fun onError(mediaPlayer: MediaPlayer, i: Int, i1: Int): Boolean {
        mediaPlayer.reset()
        return false
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        mediaPlayer.start()
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendInt = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this)

//        builder.setContentIntent(pendInt)
//            .setSmallIcon(R.drawable.ic_play_arrow_black_24dp)
//            .setTicker(songTitle)
//            .setOngoing(true)
//            .setContentTitle("Playing")
//        .setContentText(songTitle)
//        val notification = builder.build()
//        startForeground(NOTIFY_ID, notification)
    }

    fun getSeek(): Int {
        return player!!.currentPosition
    }

    fun getTitle(): String {
        return songTitle
    }

    fun getArtist(): String {
        return artistTitle
    }

    fun getDuration(): Int {
        return duration
    }

    fun setSong(songIndex: Int) {
        pos = songIndex
    }

    fun getPos(): Int {
        return pos
    }

    fun isPng(): Boolean {
        return player!!.isPlaying
    }

    fun pausePlayer() {
        player!!.pause()
    }

    fun seek(pos: Int) {
        player!!.seekTo(pos)
    }

    fun go() {
        player!!.start()
    }

    fun playPrev() {
        pos--
        if(pos < 0)
            pos = songs!!.size-1
        playSong()
    }

    fun playNext() {
        pos++
        if(pos >= songs!!.size)
            pos = 0
        playSong()

        //For Shuffle
//        if(shuffle) {
//            var newSong = pos
//            while(newSong == pos){
//                newSong = rand!!.nextInt(songs!!.size);
//            }
//            pos = newSong
//        }
//        else{
//            pos++
//            (pos >= songs!!.size)
//            pos = 0
//        }
//        playSong()
    }

    fun setShuffle() {
        shuffle = if (shuffle) false else true
    }
}