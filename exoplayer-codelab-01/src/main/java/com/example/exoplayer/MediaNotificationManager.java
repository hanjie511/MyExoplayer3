package com.example.exoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

public class MediaNotificationManager extends BroadcastReceiver {
    private final static String ACTION_PLAY="CMD_PLAY";
    private final static String ACTION_PAUSE="CMD_PAUSE";
    private final static String ACTION_NEXT="CMD_NEXT";
    private final static String ACTION_PRE="CMD_PRE";
    private final static String ACTION_LIKE="CMD_LIKE";
    private final static String ACTION_UNLIKE="CMD_UNLIKE";
    private static final String CHANNEL_ID = "Hanjie";
    private static final int REQUEST_CODE=100;
    private static int NOTIFICATION_ID=666;
    private  final MediaService mediaService;
    private MediaMetadataCompat mediaMetadataCompat;
    private final NotificationManager notificationManager;
    private MediaSessionCompat.Token meToken;
    private MediaControllerCompat mediaControllerCompat;
    private MediaControllerCompat.TransportControls transportControls;
    private PlaybackStateCompat mPlaybackState;
    private boolean isStarted=false;
    public MediaNotificationManager(MediaService mediaService) throws Exception{
        this.mediaService = mediaService;
        notificationManager= (NotificationManager) mediaService.getSystemService(Context.NOTIFICATION_SERVICE);
        updateSessionToken();
        notificationManager.cancelAll();
    }
    @Override
    public void onReceive(Context context, Intent intent) {
    }
    private final MediaControllerCompat.Callback callback=new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            Log.i("message-----------:","PlaybackStateCompat发生改变");
            mPlaybackState=state;
            if(state.getState()==PlaybackStateCompat.STATE_STOPPED||
                    state.getState()==PlaybackStateCompat.STATE_NONE){
                //  stopNotification();
            }else if(state.getState()==PlaybackStateCompat.STATE_PLAYING){
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Samples.fetchBitMap(mediaMetadataCompat.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),null, new Samples.FetchListener() {
                            @Override
                            public void onFetched(final Bitmap bigImage) {
                                Notification notification=createNotification(bigImage);
                                if(notification!=null){
                                    notificationManager.notify(NOTIFICATION_ID,notification);
                                }
                            }
                        });
                    }
                },1000);
            }
        }


        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if(metadata==null){
                return;
            }
            Log.i("message-----------:","MediaMetadataCompat发生改变");
            mediaMetadataCompat=metadata;
            Samples.fetchBitMap(mediaMetadataCompat.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),null, new Samples.FetchListener() {
                @Override
                public void onFetched(final Bitmap bigImage) {
                    Notification notification=createNotification(bigImage);
                    if(notification!=null){
                        notificationManager.notify(NOTIFICATION_ID,notification);
                    }
                }
            });
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            try {
                updateSessionToken();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
    private Notification createNotification(Bitmap bitmap){
        if(mediaMetadataCompat==null||mPlaybackState==null){
            return null;
        }
        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
        final NotificationCompat.Builder notificationBuilder=new
                NotificationCompat.Builder(mediaService,CHANNEL_ID);
        notificationBuilder.setStyle(new androidx.media.app.NotificationCompat
                .MediaStyle().setMediaSession(meToken)
                .setShowCancelButton(true).setShowActionsInCompactView(addActions(notificationBuilder)));
        notificationBuilder.setColor(Color.WHITE);
        notificationBuilder.setSmallIcon(R.drawable.ic_toolbar_24);
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        notificationBuilder.setContentText(mediaMetadataCompat.getString(MediaMetadata.METADATA_KEY_ARTIST));
        notificationBuilder.setContentTitle(mediaMetadataCompat.getString(MediaMetadata.METADATA_KEY_TITLE));
        notificationBuilder.setContentIntent(PendingIntent.getActivity(mediaService, REQUEST_CODE, new Intent(mediaService, MainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        notificationBuilder.setAutoCancel(true);
        notificationBuilder.setOnlyAlertOnce(true);
        if(bitmap!=null){
            notificationBuilder.setLargeIcon(bitmap);
        }
        setNotificationPlaybackState(notificationBuilder);
        return notificationBuilder.build();
    }
    public void startNotification(){
        if (!isStarted) {
            mediaMetadataCompat = mediaControllerCompat.getMetadata();
            mPlaybackState = mediaControllerCompat.getPlaybackState();

            // The notification must be updated after setting started to true
            Notification notification = createNotification(null);
            if (notification != null) {
                mediaControllerCompat.registerCallback(callback);
                mediaService.startForeground(NOTIFICATION_ID, notification);
                isStarted = true;
            }
        }
    }
    public void stopNotification(){
        if (isStarted) {
            isStarted = false;
            mediaControllerCompat.unregisterCallback(callback);
            try {
                notificationManager.cancel(NOTIFICATION_ID);
                mediaService.unregisterReceiver(this);
            } catch (IllegalArgumentException ex) {
                // ignore if the receiver is not registered.
            }
            mediaService.stopForeground(true);
        }
    }
    private void updateSessionToken() throws RemoteException {
        MediaSessionCompat.Token freshToken = mediaService.getSessionToken();
        if (meToken == null && freshToken != null ||
                meToken != null && !meToken.equals(freshToken)) {
            if (mediaControllerCompat != null) {
                mediaControllerCompat.unregisterCallback(callback);
            }
            meToken = freshToken;
            if (meToken != null) {
                mediaControllerCompat = new MediaControllerCompat(mediaService, meToken);
                transportControls = mediaControllerCompat.getTransportControls();
                if (!isStarted) {
                    mediaControllerCompat.registerCallback(callback);
                }
            }
        }
    }
    private void setNotificationPlaybackState(NotificationCompat.Builder builder) {
        if (mPlaybackState == null || !isStarted) {
            mediaService.stopForeground(true);
            return;
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING);
    }

    /**
     * According PlaybackState to add media Button on Notification
     * @param builder
     * @return
     */
    private int[] addActions(NotificationCompat.Builder builder){
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_outline_skip_previous_24,"上一曲",
                MediaButtonReceiver.buildMediaButtonPendingIntent(mediaService,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
        if(mPlaybackState.getState()==PlaybackStateCompat.STATE_PLAYING){
            builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_outline_pause_circle_outline_24,"暂停",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(mediaService,
                            PlaybackStateCompat.ACTION_PLAY_PAUSE)));

        }else{
            builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_outline_play_circle_outline_24,"播放",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(mediaService,
                            PlaybackStateCompat.ACTION_PLAY)));
        }
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_outline_skip_next_24,"下一首",
                MediaButtonReceiver.buildMediaButtonPendingIntent(mediaService,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
        return new int[]{0,1,2};
    }
    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(CHANNEL_ID,
                            CHANNEL_ID,
                            NotificationManager.IMPORTANCE_HIGH);

            notificationChannel.setDescription(
                    CHANNEL_ID);

            notificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
