package com.rama.txori_zero.managers;

import android.os.CountDownTimer;

import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.objects.SessionItem;

import java.util.ArrayList;
import java.util.List;

/** Runs one session at a time: task countdown, optional rest, and the session total. */
public class WorkoutManager {

    public interface Listener {
        void onTaskStarted(int index, String label, long remainingMs);

        void onTaskTick(int index, long remainingMs, float progress);

        void onTaskFinished(int index);

        void onRestStarted(int index, long restDurationMs);

        void onRestTick(int index, long remainingMs, float progress);

        void onRestFinished(int index);

        void onSessionTick(long sessionId, long remainingMs);

        /** headerRemainingMs: total the header should show, or null to revert to the static sum. */
        void onPlayingStateChanged(long sessionId, boolean playing, Long headerRemainingMs);

        void onGroupFinished(long sessionId);

        void onGroupReset(long sessionId);
    }

    private final Listener listener;
    private final Runnable onTick;
    private final Runnable onFinishNotify;

    /** Shared with the adapter, which mutates it when the user edits the lists. */
    public List<SessionItem> items = new ArrayList<SessionItem>();

    private long activeSessionId = -1;
    private int currentItemIndex = -1;
    private boolean running = false;
    private long remainingMs = 0;
    private long globalRemainingMs = 0;

    private CountDownTimer taskTimer;
    private CountDownTimer globalTimer;
    private CountDownTimer restTimer;
    private int taskGeneration = 0;
    private long lastBeepSecond = -1;
    private long taskDurationMs = 0;
    private long restDurationMs = 0;
    private long restRemainingMs = 0;
    private boolean resting = false;

    public WorkoutManager(Listener listener, Runnable onTick, Runnable onFinishNotify) {
        this.listener = listener;
        this.onTick = onTick;
        this.onFinishNotify = onFinishNotify;
    }

    public long getActiveSessionId() {
        return activeSessionId;
    }

    public int getCurrentItemIndex() {
        return currentItemIndex;
    }

    public boolean isRunning() {
        return running;
    }

    public long getRemainingMs() {
        return remainingMs;
    }

    public long getGlobalRemainingMs() {
        return globalRemainingMs;
    }

    // Public actions

    public void startGroup(long sessionId, int startIndex) {
        if (activeSessionId == sessionId && running) {
            pause();
        } else if (activeSessionId == sessionId && currentItemIndex >= 0) {
            resume();
        } else {
            stopTask();
            cancelGlobalTimer();
            if (activeSessionId != -1 && activeSessionId != sessionId) {
                listener.onPlayingStateChanged(activeSessionId, false, null);
            }
            activeSessionId = sessionId;
            startFromIndex(startIndex);
        }
    }

    public void resetGroup(long sessionId) {
        if (activeSessionId != sessionId) return;
        stopTask();
        cancelGlobalTimer();
        globalRemainingMs = 0;
        activeSessionId = -1;
        currentItemIndex = -1;
        running = false;
        listener.onGroupReset(sessionId);
    }

    public void togglePlayPause() {
        if (currentItemIndex < 0) return;
        if (running) pause();
        else resume();
    }

    public void addTime(long ms) {
        if (!running) return;
        cancelGlobalTimer();
        globalRemainingMs += ms;
        launchGlobalTimer(globalRemainingMs);
        if (resting) {
            restRemainingMs += ms;
            restDurationMs += ms;
            launchRestTimer(currentItemIndex, restRemainingMs, true);
        } else {
            cancelTaskTimer();
            remainingMs += ms;
            taskDurationMs += ms;
            launchTaskTimer(remainingMs);
        }
    }

    public void repeatCurrentTask() {
        if (currentItemIndex < 0) return;
        SessionItem row = rowAt(currentItemIndex);
        if (row == null) return;
        if (resting) {
            long restMs = Hms.toMs(row.task.restDuration);
            if (restRemainingMs > restMs) return;
            if (restMs > 0) launchRestTimer(currentItemIndex, restMs, false);
            return;
        }
        long originalMs = Hms.toMs(row.task.duration);
        if (remainingMs > originalMs) return;
        globalRemainingMs = calcSessionMs(activeSessionId, currentItemIndex);
        launchGlobalTimer(globalRemainingMs);
        listener.onSessionTick(activeSessionId, globalRemainingMs);
        loadTask(currentItemIndex);
    }

    public void skipTask() {
        cancelTaskTimer();
        interruptRest();
        startFromIndex(currentItemIndex + 1);
    }

    public void stopAndClear() {
        stopTask();
        cancelGlobalTimer();
        long previous = activeSessionId;
        activeSessionId = -1;
        currentItemIndex = -1;
        globalRemainingMs = 0;
        if (previous != -1) listener.onPlayingStateChanged(previous, false, null);
    }

    public void release() {
        stopTask();
        cancelGlobalTimer();
    }

    // Private helpers

    private SessionItem rowAt(int index) {
        if (index < 0 || index >= items.size()) return null;
        SessionItem item = items.get(index);
        return item.isRow() ? item : null;
    }

    private void pause() {
        cancelTaskTimer();
        cancelRestTimer();
        cancelGlobalTimer();
        running = false;
        listener.onPlayingStateChanged(activeSessionId, false, globalRemainingMs);
    }

    private void resume() {
        running = true;
        listener.onPlayingStateChanged(activeSessionId, true, globalRemainingMs);
        if (resting) launchRestTimer(currentItemIndex, restRemainingMs, true);
        else launchTaskTimer(remainingMs);
        if (globalRemainingMs > 0) launchGlobalTimer(globalRemainingMs);
    }

    private void startFromIndex(int index) {
        int target = -1;
        for (int i = Math.max(index, 0); i < items.size(); i++) {
            SessionItem item = items.get(i);
            if (item.isRow() && item.sessionId == activeSessionId) {
                target = i;
                break;
            }
        }
        if (target == -1) {
            finishGroup();
        } else {
            globalRemainingMs = calcSessionMs(activeSessionId, target);
            launchGlobalTimer(globalRemainingMs);
            listener.onSessionTick(activeSessionId, globalRemainingMs);
            loadTask(target);
        }
    }

    private void loadTask(int index) {
        SessionItem row = rowAt(index);
        if (row == null) return;
        cancelTaskTimer();
        resting = false;
        restRemainingMs = 0;
        currentItemIndex = index;
        remainingMs = Hms.toMs(row.task.duration);
        taskDurationMs = remainingMs;
        lastBeepSecond = -1;
        running = true;
        listener.onTaskStarted(index, row.task.label, remainingMs);
        listener.onPlayingStateChanged(activeSessionId, true, globalRemainingMs);
        launchTaskTimer(remainingMs);
    }

    private void finishGroup() {
        running = false;
        cancelGlobalTimer();
        globalRemainingMs = 0;
        long doneId = activeSessionId;
        activeSessionId = -1;
        currentItemIndex = -1;
        listener.onGroupFinished(doneId);
    }

    private void stopTask() {
        cancelTaskTimer();
        cancelRestTimer();
        resting = false;
        restRemainingMs = 0;
        running = false;
    }

    private void launchTaskTimer(long durationMs) {
        cancelTaskTimer();
        final int generation = ++taskGeneration;
        taskTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (generation != taskGeneration) return;
                remainingMs = millisUntilFinished;
                float progress = taskDurationMs <= 0 ? 1f : 1f - (float) millisUntilFinished / taskDurationMs;
                listener.onTaskTick(currentItemIndex, millisUntilFinished, Math.max(0f, Math.min(1f, progress)));
                long secondsLeft = millisUntilFinished / 1000;
                if (secondsLeft <= 5 && secondsLeft != lastBeepSecond) {
                    lastBeepSecond = secondsLeft;
                    onTick.run();
                }
            }

            @Override
            public void onFinish() {
                if (generation != taskGeneration) return;
                onFinishNotify.run();
                listener.onTaskFinished(currentItemIndex);
                SessionItem row = rowAt(currentItemIndex);
                long restMs = row == null ? 0 : Hms.toMs(row.task.restDuration);
                if (restMs > 0) launchRestTimer(currentItemIndex, restMs, false);
                else startFromIndex(currentItemIndex + 1);
            }
        }.start();
    }

    private void launchGlobalTimer(long durationMs) {
        cancelGlobalTimer();
        globalTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long ms) {
                globalRemainingMs = ms;
                listener.onSessionTick(activeSessionId, ms);
            }

            @Override
            public void onFinish() {
                globalRemainingMs = 0;
                listener.onSessionTick(activeSessionId, 0);
            }
        }.start();
    }

    private void launchRestTimer(final int index, long durationMs, boolean isResume) {
        cancelRestTimer();
        resting = true;
        restRemainingMs = durationMs;
        if (!isResume) {
            restDurationMs = durationMs;
            listener.onRestStarted(index, durationMs);
        }
        restTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                restRemainingMs = millisUntilFinished;
                float progress = restDurationMs <= 0 ? 1f : 1f - (float) millisUntilFinished / restDurationMs;
                listener.onRestTick(index, millisUntilFinished, Math.max(0f, Math.min(1f, progress)));
            }

            @Override
            public void onFinish() {
                resting = false;
                restRemainingMs = 0;
                onFinishNotify.run();
                listener.onRestFinished(index);
                startFromIndex(index + 1);
            }
        }.start();
    }

    private void cancelTaskTimer() {
        if (taskTimer != null) taskTimer.cancel();
        taskTimer = null;
    }

    private void cancelRestTimer() {
        if (restTimer != null) restTimer.cancel();
        restTimer = null;
    }

    private void cancelGlobalTimer() {
        if (globalTimer != null) globalTimer.cancel();
        globalTimer = null;
    }

    private void interruptRest() {
        if (!resting) return;
        cancelRestTimer();
        resting = false;
        restRemainingMs = 0;
        listener.onRestFinished(currentItemIndex);
    }

    private long calcSessionMs(long sessionId, int fromIndex) {
        long total = 0;
        for (int i = fromIndex; i < items.size(); i++) {
            SessionItem item = items.get(i);
            if (item.isRow() && item.sessionId == sessionId) {
                total += Hms.toMs(item.task.duration) + Hms.toMs(item.task.restDuration);
            }
        }
        return total;
    }
}
