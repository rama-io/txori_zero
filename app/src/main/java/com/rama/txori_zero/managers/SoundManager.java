package com.rama.txori_zero.managers;

import android.media.AudioManager;
import android.media.ToneGenerator;

public final class SoundManager {
    private static ToneGenerator tone;

    private SoundManager() {
    }

    public static void init() {
        if (tone != null) return;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            tone = null;
        }
    }

    public static void beepTick() {
        if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
    }

    public static void beepFinish() {
        if (tone != null) tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150);
    }

    public static void release() {
        if (tone != null) tone.release();
        tone = null;
    }
}
