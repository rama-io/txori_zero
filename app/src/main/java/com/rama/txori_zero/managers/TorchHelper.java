package com.rama.txori_zero.managers;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;

/** Blinks the rear torch. Needs API 23+ (setTorchMode), where no camera permission is required. */
@TargetApi(23)
final class TorchHelper {
    private static final int BLINKS = 3;
    private static final long STEP_MS = 150;

    private TorchHelper() {
    }

    static void blink(Context context, Handler handler) {
        final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;
        String found = null;
        try {
            for (String id : manager.getCameraIdList()) {
                Boolean flash = manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    found = id;
                    break;
                }
            }
        } catch (Exception e) {
            return;
        }
        if (found == null) return;
        final String cameraId = found;
        long t = 0;
        for (int i = 0; i < BLINKS; i++) {
            handler.postDelayed(() -> setTorch(manager, cameraId, true), t);
            t += STEP_MS;
            handler.postDelayed(() -> setTorch(manager, cameraId, false), t);
            t += STEP_MS;
        }
    }

    private static void setTorch(CameraManager manager, String cameraId, boolean on) {
        try {
            manager.setTorchMode(cameraId, on);
        } catch (Exception ignored) {
            // torch can be busy (used by another app)
        }
    }
}
