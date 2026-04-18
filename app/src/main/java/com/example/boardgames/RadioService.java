package com.example.boardgames;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class RadioService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final String CHANNEL_ID = "radio_playback";
    private static final int NOTIFICATION_ID = 42;
    private static final String PREFS_NAME = "radio_prefs";
    private static final String KEY_LAST_STATION_URL = "last_station_url";

    public static final String ACTION_STOP = "com.example.boardgames.ACTION_STOP_RADIO";

    public interface RadioServiceListener {
        void onPlaybackStarted(RadioStation station);
        void onPlaybackStopped();
        void onBuffering();
        void onError(String message);
    }

    private final IBinder binder = new RadioBinder();
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private RadioStation currentStation;
    private RadioServiceListener listener;
    private boolean isPreparing;

    public class RadioBinder extends Binder {
        public RadioService getService() {
            return RadioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stop();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    public void setListener(RadioServiceListener listener) {
        this.listener = listener;
    }

    public void play(RadioStation station) {
        // Clean up previous playback (including any in-progress preparation)
        releasePlayer();
        abandonAudioFocus();
        isPreparing = false;

        currentStation = station;
        isPreparing = true;

        if (listener != null) {
            listener.onBuffering();
        }

        if (!requestAudioFocus()) {
            isPreparing = false;
            currentStation = null;
            if (listener != null) {
                listener.onError("Could not obtain audio focus");
            }
            return;
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);

        try {
            mediaPlayer.setDataSource(station.getStreamUrl());
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            isPreparing = false;
            releasePlayer();
            currentStation = null;
            if (listener != null) {
                listener.onError("Failed to load stream");
            }
        }
    }

    public void stop() {
        isPreparing = false;
        releasePlayer();
        abandonAudioFocus();
        currentStation = null;
        stopForeground(true);
        stopSelf();
        if (listener != null) {
            listener.onPlaybackStopped();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public boolean isPreparing() {
        return isPreparing;
    }

    public RadioStation getCurrentStation() {
        return currentStation;
    }

    public String getLastStationUrl() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LAST_STATION_URL, null);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPreparing = false;
        mp.start();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_STATION_URL, currentStation.getStreamUrl()).apply();

        startForeground(NOTIFICATION_ID, buildNotification());

        if (listener != null) {
            listener.onPlaybackStarted(currentStation);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPreparing = false;
        releasePlayer();
        abandonAudioFocus();
        currentStation = null;
        stopForeground(true);
        if (listener != null) {
            listener.onError("Stream error — try another station");
        }
        return true;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        // No-op: streaming doesn't report meaningful percentages
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stop();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
                // Player may not be started
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private boolean requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(this)
                    .build();
            return audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return true;
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Radio Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Ambient radio streaming during gameplay");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, DungeonMasterActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, RadioService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        String stationName = currentStation != null ? currentStation.getName() : "Radio";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(getString(R.string.radio_now_playing))
                .setContentText(stationName)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_media_pause,
                        getString(R.string.radio_stop), stopPending)
                .setOngoing(true)
                .build();
    }
}
