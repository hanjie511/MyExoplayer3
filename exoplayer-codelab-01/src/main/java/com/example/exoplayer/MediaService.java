package com.example.exoplayer;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.List;

public class MediaService extends MediaBrowserServiceCompat {

    private List<MediaMetadataCompat> misic_list = Samples.getPlayList();
    Player simpleExoPlayer;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat playbackState;
    private Context context;
    private MediaNotificationManager mediaNotificationManager;
    private QueueManager queueManager;
    private final MyEventListener myEventListener= new MyEventListener();
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("123", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        mediaSession=new MediaSessionCompat(this,"MediaService");
        queueManager=new QueueManager();
        playbackState = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE,0,1.0f)
                .build();
        mediaSession.setCallback(mediaSessionCallback);
        mediaSession.setPlaybackState(playbackState);
        //设置token后会触发MediaBrowserCompat.ConnectionCallback的回调方法
        //表示MediaBrowser与MediaBrowserService连接成功
        setSessionToken(mediaSession.getSessionToken());
        try {
            mediaNotificationManager=new MediaNotificationManager(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //设置token后会触发MediaBrowserCompat.ConnectionCallback的回调方法
        //表示MediaBrowser与MediaBrowserService连接成功

    }
    private void playMusic(MediaMetadataCompat mediaMetadataCompat, final Context context){
        if(simpleExoPlayer!=null){
            simpleExoPlayer.release();
            simpleExoPlayer = null;
        }
        simpleExoPlayer = new ExoPlayer.Builder(this).build();
        simpleExoPlayer.addMediaItem(MediaItem.fromUri(mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)));
        simpleExoPlayer.addListener(myEventListener);
        simpleExoPlayer.setPlayWhenReady(true);
        simpleExoPlayer.prepare();
        mediaNotificationManager.startNotification();
    }
    private MediaSessionCompat.Callback mediaSessionCallback=new MediaSessionCompat.Callback() {
        @Override
        public void onPrepare() {
            super.onPrepare();
        }

        @Override
        public void onPlay() {
            if (simpleExoPlayer != null) {
                simpleExoPlayer.setPlayWhenReady(true);
            }else{
                playMusic(queueManager.getCurrentMediaMetadata(),context);
            }
        }

        @Override
        public void onPause() {
            if (simpleExoPlayer != null) {
                simpleExoPlayer.pause();
            }
        }

        @Override
        public void onSkipToNext() {

            playMusic(queueManager.getNextMediaMetadata(),context);
        }

        @Override
        public void onSkipToPrevious() {
            playMusic(queueManager.getPreviousMediaMetadata(),context);
        }

        @Override
        public void onStop() {
            super.onStop();
        }

        @Override
        public void onSeekTo(long pos) {
            if (simpleExoPlayer != null) {
                simpleExoPlayer.seekTo(pos);
            }
        }
    };
    private class MyEventListener implements Player.Listener{
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            Player.Listener.super.onIsPlayingChanged(isPlaying);
            Log.i("isPlaying",""+isPlaying);
            try {
                updatePlaybackState(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Player.Listener.super.onPlaybackStateChanged(playbackState);
            switch (playbackState) {
                case Player.STATE_IDLE:
                case Player.STATE_BUFFERING:
                case Player.STATE_READY:
                    try {
                        updatePlaybackState(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case Player.STATE_ENDED:
                    playMusic(queueManager.getNextMediaMetadata(),context);
                    break;
            }
        }

    }
    public void updatePlaybackState(String error) throws Exception {
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        position = simpleExoPlayer.getCurrentPosition();
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions());
        int state = getState();
        //noinspection ResourceType
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,queueManager.getCurrentMediaMetadata().getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)) //id
                .putString(MediaMetadata.METADATA_KEY_TITLE,queueManager.getCurrentMediaMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE))//标题
                .putString(MediaMetadata.METADATA_KEY_ARTIST,queueManager.getCurrentMediaMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST))//作者
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,queueManager.getCurrentMediaMetadata().getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI))//背景图片
                .putLong(MediaMetadata.METADATA_KEY_DURATION,simpleExoPlayer.getDuration())//媒体时长
                .build();
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setMetadata(metadata);
        mediaNotificationManager.startNotification();
     //   mediaSession.setActive(true);
    }
    public int getState() {
        switch (simpleExoPlayer.getPlaybackState()) {
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_PAUSED;
            case Player.STATE_BUFFERING:
                return PlaybackStateCompat.STATE_BUFFERING;//缓冲
            case Player.STATE_READY:
                return simpleExoPlayer.getPlayWhenReady()
                        ? PlaybackStateCompat.STATE_PLAYING
                        : PlaybackStateCompat.STATE_PAUSED;
            default:
                return PlaybackStateCompat.STATE_NONE;
        }
    }
    private long getAvailableActions() {
        long actions =
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        if (simpleExoPlayer.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        } else {
            actions |= PlaybackStateCompat.ACTION_PLAY;
        }
        return actions;
    }
    @Override
    public void onDestroy() {
        mediaNotificationManager.stopNotification();
        if(simpleExoPlayer!=null){
            simpleExoPlayer.release();
            simpleExoPlayer = null;
        }
        super.onDestroy();
    }
}
