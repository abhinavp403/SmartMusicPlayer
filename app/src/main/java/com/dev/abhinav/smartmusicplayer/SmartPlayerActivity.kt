package com.dev.abhinav.smartmusicplayer

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import kotlinx.android.synthetic.main.activity_smartplayer.*
import java.util.*

class SmartPlayerActivity : AppCompatActivity(), MediaController.MediaPlayerControl, ServiceCallbacks {

    private lateinit var lowerRelativeLayout: RelativeLayout
    private lateinit var parentRelativeLayout: RelativeLayout
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private var keeper: String = ""

    private lateinit var playpauseBtn: ImageView
    private lateinit var nextBtn: ImageView
    private lateinit var previousBtn: ImageView
    private lateinit var loopBtn: ImageView
    private lateinit var shuffleBtn: ImageView
    private lateinit var songNameText: TextView
    private lateinit var artistNameText: TextView
    private lateinit var elapsedTimeText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var imageView: ImageView

    private var musicSrv: MusicService? = null
    private var playIntent: Intent? = null
    private var totalTime: Int = 0
    private var musicBound = false
    private var paused = false
    private var playbackPaused = false
    private var isLoop = false
    private lateinit var globalVariable: GlobalClass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smartplayer)

        checkVoiceCommandPermission()
        globalVariable = this.applicationContext as GlobalClass

        playpauseBtn = findViewById(R.id.playpausebtn)
        nextBtn = findViewById(R.id.nextbtn)
        previousBtn = findViewById(R.id.previousbtn)
        shuffleBtn = findViewById(R.id.shufflebtn)
        loopBtn = findViewById(R.id.loopbtn)
        songNameText = findViewById(R.id.songname)
        artistNameText = findViewById(R.id.artistname)
        elapsedTimeText = findViewById(R.id.elapsedTimeLabel)
        remainingTimeText = findViewById(R.id.remainingTimeLabel)
        imageView = findViewById(R.id.logo)
        setInitialResources()

        lowerRelativeLayout = findViewById(R.id.lower)
        parentRelativeLayout = findViewById(R.id.parentRelativeLayout)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        setController()
        validateReceivedValues()

        if (globalVariable.mode == "OFF") {
            lowerRelativeLayout.visibility = View.VISIBLE
            parentRelativeLayout.setOnClickListener(null)
        } else {
            lowerRelativeLayout.visibility = View.INVISIBLE
            parentRelativeLayout.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        speechRecognizer.startListening(speechRecognizerIntent)
                        keeper = ""
                    }
                    MotionEvent.ACTION_UP -> {
                        speechRecognizer.stopListening()
                    }
                }
                return@OnTouchListener false
            })
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {}

            override fun onResults(results: Bundle) {
                val matchesFound: ArrayList<String>? = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matchesFound != null) {
                    if (globalVariable.mode == "ON") {
                        keeper = matchesFound[0]
                        Toast.makeText(this@SmartPlayerActivity, "Command = $keeper", Toast.LENGTH_LONG).show()
                        if (keeper.equals("pause", ignoreCase = true) || keeper.equals("pause song", ignoreCase = true)) {
                            pause()
                        } else if (keeper.equals("play", ignoreCase = true) || keeper.equals("play song", ignoreCase = true)) {
                            start()
                        } else if (keeper.equals("next", ignoreCase = true) || keeper.equals("play next song", ignoreCase = true)) {
                            playNext()
                        } else if (keeper.equals("previous", ignoreCase = true) || keeper.equals("play previous song", ignoreCase = true)) {
                            playPrev()
                        } else if (keeper.equals("loop", ignoreCase = true) || keeper.equals("play in loop", ignoreCase = true)) {
                            isLoop = !isLoop
                            loop()
                        } else if (keeper.equals("shuffle", ignoreCase = true) || keeper.equals("shuffle playlist", ignoreCase = true)) {
                            globalVariable.isShuffle = !globalVariable.isShuffle
                            shuffle()
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle) {}

            override fun onEvent(eventType: Int, params: Bundle) {}
        })

        positionBar.max = totalTime
        positionBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicSrv!!.seek(progress)
                    positionBar.progress = progress
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        }
        )
    }

    private fun setInitialResources() {
        playpauseBtn.setImageResource(R.drawable.ic_pause_black_24dp)
        if(globalVariable.isShuffle) {
            shuffleBtn.setBackgroundResource(R.drawable.ic_baseline_shuffle_24_on)
        }
    }

    override fun onStart() {
        super.onStart()
        if (playIntent == null) {
            playIntent = Intent(this, MusicService::class.java)
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE)
            startService(playIntent)
        }
    }

    private val musicConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicSrv = binder.service
            musicBound = true
            musicSrv!!.setCallbacks(this@SmartPlayerActivity)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            musicBound = false
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    override fun onResume() {
        super.onResume()
        if(paused) {
            setController()
            paused = false
        }
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
                lowerRelativeLayout.visibility = View.VISIBLE
                parentRelativeLayout.setOnClickListener(null)
            } else {
                globalVariable.mode = "ON"
                item.title = "Voice Enabled = " + globalVariable.mode
                lowerRelativeLayout.visibility = View.INVISIBLE
                parentRelativeLayout.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            speechRecognizer.startListening(speechRecognizerIntent)
                            keeper = ""
                        }
                        MotionEvent.ACTION_UP -> {
                            speechRecognizer.stopListening()
                        }
                    }
                    return@OnTouchListener false
                })
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

    private fun createTimeLabel(time: Int): String {
        var timeLabel: String
        val min = time / 1000 / 60
        val sec = time / 1000 % 60
        timeLabel = "$min:"
        if(sec < 10) timeLabel += "0"
        timeLabel += sec
        return timeLabel
    }

    private fun validateReceivedValues() {
        val title = intent.getStringExtra("title")
        val artist = intent.getStringExtra("artist")
        val duration = intent.getIntExtra("duration", 0)

        songNameText.text = title
        songNameText.setHorizontallyScrolling(true)
        songNameText.isSelected = true
        artistNameText.text = artist
        totalTime = duration
        val remainingTime = createTimeLabel(totalTime)
        remainingTimeLabel.text = "-$remainingTime"

        thread()
    }

    private fun thread() {
        val musicMethodsHandler = Handler()
        val musicRun = object : Runnable {
            override fun run() {
                if (musicBound) {
                    positionBar.max = totalTime
                    val musicCurTime = musicSrv!!.getSeek()
                    positionBar.progress = musicCurTime
                    val elapsedTime = createTimeLabel(musicCurTime)
                    elapsedTimeLabel.text = elapsedTime
                    val remainingTime = createTimeLabel(totalTime - musicCurTime)
                    remainingTimeLabel.text = "-$remainingTime"
                } else if (!musicBound) {
                    Log.d("nnn", java.lang.Boolean.toString(musicBound))
                }
                musicMethodsHandler.postDelayed(this, 1000)
            }
        }
        musicMethodsHandler.postDelayed(musicRun, 1000)
    }

    private fun checkVoiceCommandPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:  $packageName"))
                startActivity(intent)
                finish()
            }
        }
    }

    private fun setController() {
        playpauseBtn.setOnClickListener {
            if(musicSrv!!.isPng()) pause()
            else start()
        }
        previousBtn.setOnClickListener {
                playPrev()
        }
        nextBtn.setOnClickListener {
                playNext()
        }
        shuffleBtn.setOnClickListener {
            globalVariable.isShuffle = !globalVariable.isShuffle
            shuffle()
        }
        loopBtn.setOnClickListener {
            isLoop = !isLoop
            loop()
        }
    }

    private fun playNext() {
        musicSrv!!.playNext()
        setNextPrevScreen()
    }

    private fun playPrev() {
        musicSrv!!.playPrev()
        setNextPrevScreen()
    }

    private fun shuffle() {
        if(globalVariable.isShuffle) {
            shuffleBtn.setBackgroundResource(R.drawable.ic_baseline_shuffle_24_on)
            musicSrv!!.setShuffle(globalVariable.isShuffle)
        }
        else {
            shuffleBtn.setBackgroundResource(R.drawable.ic_baseline_shuffle_24)
            musicSrv!!.setShuffle(globalVariable.isShuffle)
        }
        setNextPrevScreen()
    }

    private fun loop() {
        if(isLoop) {
            loopBtn.setBackgroundResource(R.drawable.ic_baseline_loop_24_on)
            musicSrv!!.setLoop(isLoop)
        }
        else {
            loopBtn.setBackgroundResource(R.drawable.ic_baseline_loop_24)
            musicSrv!!.setLoop(isLoop)
        }
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
        playpauseBtn.setImageResource(R.drawable.ic_play_arrow_black_24dp)
        musicSrv!!.pausePlayer()
    }

    override fun seekTo(pos: Int) {
        musicSrv!!.seek(pos)
    }

    override fun start() {
        musicSrv!!.go()
        playpauseBtn.setImageResource(R.drawable.ic_pause_black_24dp)
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

    override fun setNextPrevScreen() {
        playpauseBtn.setImageResource(R.drawable.ic_pause_black_24dp)
        songNameText.text = musicSrv!!.getTitle()
        artistNameText.text = musicSrv!!.getArtist()
        totalTime = musicSrv!!.getDuration()
        if(playbackPaused) {
            setController()
            playbackPaused = false
        }
        thread()
    }
}