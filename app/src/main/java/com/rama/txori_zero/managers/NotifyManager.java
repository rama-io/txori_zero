package com.rama.txori_zero.managers;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;

/** Single entry point for "something finished" feedback. Fires only what the user enabled. */
public final class NotifyManager {
    private static final Handler HANDLER = new Handler();

    private NotifyManager() {
    }

    /** Countdown beep for the last seconds. Respects the sounds toggle. */
    public static void tick(Context context) {
        if (PrefsManager.getInstance(context).getFlag(PrefsManager.NOTIFY_SOUNDS)) {
            SoundManager.beepTick();
        }
    }

    /** Sound, vibration, screen flash and camera flash, as configured. */
    public static void finish(Context context, Runnable flashScreen) {
        PrefsManager prefs = PrefsManager.getInstance(context);
        if (prefs.getFlag(PrefsManager.NOTIFY_SOUNDS)) {
            SoundManager.beepFinish();
        }
        if (prefs.getFlag(PrefsManager.NOTIFY_VIBRATE)) {
            vibrate(context);
        }
        if (prefs.getFlag(PrefsManager.NOTIFY_FLASH) && flashScreen != null) {
            flashScreen.run();
        }
        if (prefs.getFlag(PrefsManager.NOTIFY_CAMERA) && Build.VERSION.SDK_INT >= 23) {
            TorchHelper.blink(context, HANDLER);
        }
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(400);
    }
}
