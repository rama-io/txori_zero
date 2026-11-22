package com.rama.txori_zero.managers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import com.rama.txori_zero.R;
import com.rama.txori_zero.activities.Main;

/**
 * Foreground service that shows an ongoing notification while the stopwatch or the timer is running.
 * Its only job is to keep the process alive so the clocks keep running with the screen off or the app in the
 * background. The pages own the real state and report it through {@link #setStopwatch} / {@link #setTimer}.
 */
public class TimerService extends Service {
    private static final String CHANNEL_ID = "running_clock";
    private static final int NOTIFICATION_ID = 1;

    /** elapsedRealtime() at which the stopwatch started counting, or -1 when not running. */
    private static long stopwatchStart = -1;
    /** elapsedRealtime() at which the timer reaches zero, or -1 when not running. */
    private static long timerEnd = -1;

    public static void setStopwatch(Context context, boolean running, long elapsedMs) {
        stopwatchStart = running ? SystemClock.elapsedRealtime() - elapsedMs : -1;
        sync(context);
    }

    public static void setTimer(Context context, boolean running, long remainingMs) {
        timerEnd = running ? SystemClock.elapsedRealtime() + remainingMs : -1;
        sync(context);
    }

    private static void sync(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, TimerService.class);
        if (stopwatchStart < 0 && timerEnd < 0) {
            app.stopService(intent);
        } else if (Build.VERSION.SDK_INT >= 26) {
            app.startForegroundService(intent);
        } else {
            app.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (stopwatchStart < 0 && timerEnd < 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        boolean timerRunning = timerEnd >= 0;
        long now = SystemClock.elapsedRealtime();
        long wallNow = System.currentTimeMillis();
        builder.setSmallIcon(timerRunning ? R.drawable.px_timer : R.drawable.px_stopwatch)
                .setContentTitle(getString(timerRunning ? R.string.notif_timer : R.string.notif_stopwatch))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true);
        if (Build.VERSION.SDK_INT >= 16) {
            builder.setUsesChronometer(true);
        }
        if (timerRunning) {
            builder.setWhen(wallNow + (timerEnd - now));
            if (Build.VERSION.SDK_INT >= 24) builder.setChronometerCountDown(true);
        } else {
            builder.setWhen(wallNow - (now - stopwatchStart));
        }
        if (timerRunning && stopwatchStart >= 0) {
            builder.setContentText(getString(R.string.notif_stopwatch_also));
        }

        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        Intent open = new Intent(this, Main.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, open, piFlags));
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }
}
