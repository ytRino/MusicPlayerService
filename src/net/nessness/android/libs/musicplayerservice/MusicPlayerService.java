
package net.nessness.android.libs.musicplayerservice;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * cf. http://developer.android.com/guide/components/services.html<br>
 * cf. http://developer.android.com/guide/components/bound-services.html
 */
public class MusicPlayerService extends Service {
    private static final String TAG = MusicPlayerService.class.getSimpleName();

    public static final int MSG_KILL = -1;
    public static final int MSG_PLAY = 1;
    public static final int MSG_STOP = 2;
    public static final int POSITION_MSG_MAX = 2;

    public static final String URI = "uri";
    public static final String TITLE = "title";
    public static final String TEXT = "text";
    public static final String INFO = "info";
    public static final String INTENT = "intent";

    public static boolean DEBUG = true;

    private Messenger mMessenger = new Messenger(new IncomingHandler());

    protected class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

            if (DEBUG) {
                Log.i(TAG, "Received Message.");
                Log.i(TAG, "listing msg data: " + msg.what);
                Bundle data = msg.getData();
                for (String key : data.keySet()) {
                    Log.i(TAG, " " + key + ": " + data.get(key).toString());
                }
            }

            final Message m = new Message();
            m.copyFrom(msg);
            if (!takeMessage(m)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        onMessage(m);
                    }
                }).start();
            }
        }
    }

    /**
     * client sent Message to this service.
     * @param msg
     * @return true if handled Message and deny default handling
     */
    protected boolean takeMessage(Message msg) {
        return false;
    }

    /**
     * handle message.<br>
     * this method will NOT call on main thread.<br>
     * you can handle your custom message type on
     * {@link #onCustomMessage(int, Bundle)}.
     * @param msg
     */
    synchronized protected void onMessage(Message msg) {
        Bundle data = msg.getData();
        switch (msg.what) {
        case MSG_PLAY:
            play(data.getString(URI), data);
            break;
        case MSG_STOP:
            stop(data);
            break;
        case MSG_KILL:
            finish(data);
            break;
        default:
            onCustomMessage(msg);
        }
    }

    /**
     * handle your custom message type. deault implementation does nothing.
     * @param msg
     */
    protected void onCustomMessage(Message msg) {
    }

    private MediaPlayer mPlayer;

    /**
     * get MediaPlayer instance.<br>
     * each time client starts play, Service will create new MediaPlayer
     * instance.
     * @return
     */
    public MediaPlayer getMediaPlayer() {
        return mPlayer;
    }

    /**
     * start to play uri .<br>
     * default implementation start forground playing with notification using
     * getNotificationXxx.
     * @param uri
     * @param data
     */
    synchronized protected void play(String uri, final Bundle data) {
        stop(null);
        if (DEBUG) {
            Log.i(TAG, "play: " + uri);
        }
        if (uri == null) {
            Log.e(TAG,
                    "uri is null. please set uri by bundle.putString with key \"TITLE\" and put this in message.");
            return;
        }
        onPreparePlay(uri, data);
        try {
            mPlayer = new MediaPlayer();
            mPlayer.setDataSource(getApplicationContext(), Uri.parse(uri));
            mPlayer.setOnPreparedListener(new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {

                    Notification notification = createNotification(data);

                    onPlayReady(data, mPlayer);

                    startForeground(MSG_PLAY, notification);
                    mPlayer.start();
                }
            });
            mPlayer.setOnErrorListener(new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    boolean preventOnComplete = false;
                    if (DEBUG) {
                        Log.e(TAG, "onError: " + "(what: " + what + ", extra: " + extra + ")");
                        preventOnComplete = onPlayerError(mp, extra, extra);
                        stop(null);
                    }
                    return preventOnComplete;
                }
            });
            mPlayer.prepareAsync();
        } catch (IllegalArgumentException e1) {
            e1.printStackTrace();
            stop(null);
        } catch (SecurityException e1) {
            e1.printStackTrace();
            stop(null);
        } catch (IllegalStateException e1) {
            e1.printStackTrace();
            stop(null);
        } catch (IOException e1) {
            e1.printStackTrace();
            stop(null);
        }
    }

    protected void onPreparePlay(String uri, Bundle data) {
    }

    /**
     * called when music is ready to play.
     * @param data
     * @param mp
     */
    protected void onPlayReady(Bundle data, MediaPlayer mp) {
    }

    protected boolean onPlayerError(MediaPlayer mp, int what, int extra) {
        return true;
    }

    protected Notification createNotification(Bundle data) {
        return new NotificationCompat.Builder(this)
                .setTicker(getNotificationInfo(data))
                .setContentText(getNotificationText(data))
                .setContentTitle(getNotificationTitle(data))
                .setContentIntent(getNotificationPendingIntent(data))
                .setSmallIcon(getNotificationIconId(data))
                .setWhen(System.currentTimeMillis())
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .getNotification();
    }

    protected int getNotificationIconId(Bundle data) {
        return android.R.drawable.stat_notify_voicemail;
    }

    /**
     * default implementation returns string with
     * {@link MusicPlayerService.TITLE TITLE} key in data or "" if missing.
     * @param data Bundle that You put to Message object.
     * @return title of notification
     */
    protected CharSequence getNotificationTitle(Bundle data) {
        return data.getString(TITLE);
    }

    /**
     * default implementation returns string with
     * {@link MusicPlayerService.TEXT TEXT} key in data or "" if missing.
     * @param data Bundle that You put to Message object.
     * @return short message of notification
     */
    protected CharSequence getNotificationText(Bundle data) {
        return data.getString(TEXT);
    }

    /**
     * default implementation returns string with
     * {@link MusicPlayerService.INFO INFO} key in data or "" if missing.
     * @param data Bundle that You put to Message object.
     * @return ticker text when notification appears.
     */
    protected CharSequence getNotificationInfo(Bundle data) {
        return data.getString(INFO);
    }

    /**
     * default implementation returns pending intent with
     * {@link MusicPlayerService.INTENT INTENT} key in data or null if missing.
     * @param data Bundle that You put to Message object.
     * @return Pending Intent that activated when noriication tapped by user.
     */
    protected PendingIntent getNotificationPendingIntent(Bundle data) {
        return (PendingIntent) data.getParcelable(INTENT);
    }

    protected void stop(Bundle data) {
        if (DEBUG) {
            Log.i(TAG, "stop");
        }
        if (mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
        stopForeground(true);
    }

    protected void finish(Bundle data) {
        stop(data);
        stopSelf();
    }

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.i(TAG, "onCreate");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.i(TAG, "onStartCommand: " + startId);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) {
            Log.i(TAG, "onBind");
        }
        return mMessenger.getBinder();
    }

    @Override
    public void onRebind(Intent intent) {
        if (DEBUG) {
            Log.i(TAG, "onRebind");
        }
    }

    /**
     * すべてのクライアントがunbindされると呼ばれる。
     * @return trueを返すと、次にクライアントがbindされるときに onRebindが呼ばれる。<br>
     *         クライアント側にはonServiceConnectedで 以前のIBinderを受け取る事ができる。
     */
    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) {
            Log.i(TAG, "onUnbind");
        }
        return true;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.i(TAG, "onDestroy");
        }
    }

    /**
     * @param serviceName
     * @return
     */
    public boolean isServiceRunning(String serviceName) {
        boolean serviceRunning = false;
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> l = am.getRunningServices(50);
        Iterator<ActivityManager.RunningServiceInfo> i = l.iterator();
        while (i.hasNext()) {
            ActivityManager.RunningServiceInfo runningServiceInfo = (ActivityManager.RunningServiceInfo) i
                    .next();
            // Log.d(TAG, runningServiceInfo.service.getClassName() + "");
            if (runningServiceInfo.service.getClassName().equals(serviceName)) {
                serviceRunning = true;
                if (DEBUG)
                    Log.d(TAG, "running in " + runningServiceInfo.foreground);
            }
        }
        return serviceRunning;
    }
}
