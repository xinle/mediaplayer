package com.xinle.mediaplayer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * MediaplayerPlugin
 */
public class MediaplayerPlugin implements MethodCallHandler {
    private static final String ID = "flutter/media/channel";

    private final MethodChannel channel;
    private final AudioManager am;
    private final Handler handler = new Handler();
    private MediaPlayer mediaPlayer;

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), ID);
        channel.setMethodCallHandler(new MediaplayerPlugin(registrar, channel));
    }

    private MediaplayerPlugin(Registrar registrar, MethodChannel channel) {
        this.channel = channel;
        channel.setMethodCallHandler(this);
        Context context = registrar.context().getApplicationContext();
        this.am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result response) {
        switch (call.method) {
            case "play":
                String url = call.argument("url");
                Integer progress = call.argument("progress");
                play(url ,progress);
                response.success(null);
                break;
            case "pause":
                pause();
                response.success(null);
                break;
            case "stop":
                stop();
                response.success(null);
                break;
            case "seek":
                double position = call.arguments();
                seek(position);
                response.success(null);
                break;
            case "mute":
                Boolean muted = call.arguments();
                mute(muted);
                response.success(null);
                break;
            default:
                response.notImplemented();
        }
    }

    private void mute(Boolean muted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } else {
            am.setStreamMute(AudioManager.STREAM_MUSIC, muted);
        }
    }

    private void seek(double position) {
        mediaPlayer.seekTo((int) (position * 1000));
    }

    private void stop() {
        handler.removeCallbacks(sendData);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            channel.invokeMethod("audio.onStop", null);
        }
    }

    private boolean mShoudPlay = true;
    private void pause() {
        handler.removeCallbacks(sendData);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            channel.invokeMethod("audio.onPause", true);
        } else {
            mShoudPlay = false;
            channel.invokeMethod("audio.onPause", false);
        }
    }

    private String lastUrl;
    private int currProgresss;
    private boolean lastIsPrepared = false;

    private void play(String url, Integer progress) {
        if(progress == null) {
            progress = 0;
        }

        currProgresss = progress;
        mShoudPlay = true;

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    lastIsPrepared = true;
                    if(mShoudPlay) {
                        playStart();
                    }
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stop();
                    channel.invokeMethod("audio.onComplete", null);
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    channel.invokeMethod("audio.onError", String.format("{\"what\":%d,\"extra\":%d}", what, extra));
                    return true;
                }
            });
        }

        if (!TextUtils.equals(url, lastUrl)) {
            try {
                mediaPlayer.setDataSource(url);
            } catch (IOException e) {
                Log.w(ID, "Invalid DataSource", e);
                channel.invokeMethod("audio.onError", "初始化数据错误");
                return;
            }
            mediaPlayer.prepareAsync();
            lastIsPrepared = false;
            channel.invokeMethod("audio.onPrepareing", null);
            lastUrl = url;
            return;
        }

        if(lastIsPrepared) {
            playStart();
        } else {
            mediaPlayer.prepareAsync();
            channel.invokeMethod("audio.onPrepareing", null);
        }
    }

    private void playStart() {
        if(mediaPlayer != null) {
            if(currProgresss > 0) {
                mediaPlayer.seekTo(currProgresss * 1000);
            }
            currProgresss = 0;
            mediaPlayer.start();
            channel.invokeMethod("audio.onStart", mediaPlayer.getDuration());
        }
        handler.removeCallbacks(sendData);
        handler.post(sendData);
    }

    private final Runnable sendData = new Runnable() {
        public void run() {
            try {
                if (!mediaPlayer.isPlaying()) {
                    handler.removeCallbacks(sendData);
                }
                int time = mediaPlayer.getCurrentPosition();
                channel.invokeMethod("audio.onCurrentPosition", time);
                handler.postDelayed(this, 800);
            } catch (Exception e) {
                Log.w(ID, "When running handler", e);
            }
        }
    };
}
