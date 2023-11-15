package com.example.exoplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.exoplayer.databinding.ActivityPlayerBinding
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityPlayerBinding
    private val PROGRESS_UPDATE_INTERNAL: Long = 1000
    private val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 100
    private val mHandler = Handler(Looper.getMainLooper())
    private var mScheduleFuture: ScheduledFuture<*>? = null
    private val mUpdateProgressTask = Runnable { updateProgress() }
    private val mExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var mediaBrowser: MediaBrowserCompat? = null
    private var mLastPlaybackState: PlaybackStateCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private val mCallback: MediaControllerCompat.Callback =
        object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
                //  Log.i("message-----------:","PlaybackStateCompat发生改变");
                updatePlaybackState(state)
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat) {
                //  Log.i("message-----------:","MediaMetadataCompat发生改变");
                updateMediaDescription(metadata.description)
                updateDuration(metadata)
            }
        }

    private val mConnectionCallback: MediaBrowserCompat.ConnectionCallback =
        object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                println("mConnectionCallback:" + "开始连接")
                if (mediaBrowser!!.isConnected) {
                    val mediaId = mediaBrowser!!.root
                    println("-----------------------------mediaId:$mediaId")
                    try {
                        connectToSession(mediaBrowser!!.sessionToken)
                    } catch (e: RemoteException) {
                        println("mConnectionCallback:" + "连接失败")
                    }
                }
            }

            override fun onConnectionFailed() {
                Log.i("mConnectionCallback", "连接失败")
                println("mConnectionCallback:" + "连接失败")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaService::class.java), mConnectionCallback, null
        )
    }

    override fun onStart() {
        super.onStart()
        if (mediaBrowser != null) {
            if (!mediaBrowser!!.isConnected) {
                mediaBrowser!!.connect()
            }
        }
    }

    private fun initView() {
        binding.pauseBtn.setOnClickListener {
            binding.pauseBtn.visibility = View.GONE
            binding.playBtn.visibility = View.VISIBLE
            mediaController!!.transportControls.pause()
        }
        binding.previousBtn.setOnClickListener{
            mediaController!!.transportControls.skipToPrevious()
        }
        binding.playBtn.setOnClickListener{
            binding.pauseBtn.visibility = View.VISIBLE
            binding.playBtn.visibility = View.GONE
            mediaController!!.transportControls.play()
        }
        binding.nextBtn.setOnClickListener{
            mediaController!!.transportControls.skipToNext()
        }
        binding.seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mediaController!!.transportControls.seekTo(seekBar.progress.toLong())
            }
        })
    }


    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        mediaController = MediaControllerCompat(
            this, token
        )
        MediaControllerCompat.setMediaController(this, mediaController)
        mediaController!!.registerCallback(mCallback)
        val state = mediaController!!.playbackState
        updatePlaybackState(state)
        val metadata = mediaController!!.metadata
        if (metadata != null) {
            updateMediaDescription(metadata.description)
            updateDuration(metadata)
        }
        updateProgress()
        if (state != null && (state.state == PlaybackStateCompat.STATE_PLAYING ||
                    state.state == PlaybackStateCompat.STATE_BUFFERING)
        ) {
            scheduleSeekbarUpdate()
        }
    }

    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        if (state == null) {
            return
        }
        mLastPlaybackState = state
        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                binding.pauseBtn.visibility = View.VISIBLE
                binding.playBtn.visibility = View.INVISIBLE
                scheduleSeekbarUpdate()
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                binding.pauseBtn.visibility = View.INVISIBLE
                binding.playBtn.visibility = View.VISIBLE
                stopSeekbarUpdate()
            }

            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {
                binding.pauseBtn.visibility = View.INVISIBLE
                binding.playBtn.visibility = View.VISIBLE
                stopSeekbarUpdate()
            }

            PlaybackStateCompat.STATE_BUFFERING -> {
                binding.pauseBtn.visibility = View.INVISIBLE
                binding.playBtn.visibility = View.VISIBLE
                stopSeekbarUpdate()
            }
        }
    }

    private fun updateDuration(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            return
        }
        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
        binding.seekBar.max = duration
        binding.endTime.text = DateUtils.formatElapsedTime((duration / 1000).toLong())
        Glide.with(this)
            .load(Uri.parse(metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)))
            .into(binding.imageViewMusicActivity)
    }

    private fun updateProgress() {
        if (mLastPlaybackState == null) {
            return
        }
        var currentPosition = mLastPlaybackState!!.position
        if (mLastPlaybackState!!.state == PlaybackStateCompat.STATE_PLAYING) {
            val timeDelta = SystemClock.elapsedRealtime() -
                    mLastPlaybackState!!.lastPositionUpdateTime
            currentPosition += (timeDelta.toInt() * mLastPlaybackState!!.playbackSpeed).toLong()
        }
        binding.seekBar.progress = currentPosition.toInt()
        binding.startTime.text = "" + DateUtils.formatElapsedTime(currentPosition / 1000)
    }

    private fun updateMediaDescription(description: MediaDescriptionCompat?) {
        if (description == null) {
            return
        }
        binding.musicName.text = description.title
    }

    private fun scheduleSeekbarUpdate() {
        stopSeekbarUpdate()
        if (!mExecutorService.isShutdown) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                { mHandler.post(mUpdateProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS
            )
        }
    }

    private fun stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture!!.cancel(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaBrowser != null) {
            mediaBrowser!!.disconnect()
        }
        val controllerCompat = MediaControllerCompat.getMediaController(this)
        controllerCompat?.unregisterCallback(mCallback)
        val intent = Intent(this, MediaService::class.java)
        startService(intent)
    }
}