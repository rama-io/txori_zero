package com.rama.txori_zero.pages;

import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;

import com.rama.txori_zero.R;
import com.rama.txori_zero.activities.Main;
import com.rama.txori_zero.helpers.Hms;

public class StopwatchPage implements Page {
    private final Main host;
    private final Button counter;
    private final Handler handler = new Handler();
    private boolean running;
    private long startTime;
    private long pausedElapsed;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            counter.setText(Hms.stopwatch(SystemClock.elapsedRealtime() - startTime));
            handler.postDelayed(this, 100);
        }
    };

    public StopwatchPage(Main host, View root) {
        this.host = host;
        counter = (Button) root.findViewById(R.id.stopwatch_counter);
        counter.setOnClickListener(v -> {
            if (running) pause();
            else start();
        });
        counter.setOnLongClickListener(v -> {
            reset();
            return true;
        });
        root.findViewById(R.id.stopwatch_reset).setOnClickListener(v -> reset());
    }

    private void start() {
        startTime = SystemClock.elapsedRealtime() - pausedElapsed;
        running = true;
        handler.post(ticker);
    }

    private void pause() {
        pausedElapsed = SystemClock.elapsedRealtime() - startTime;
        running = false;
        handler.removeCallbacks(ticker);
    }

    private void reset() {
        handler.removeCallbacks(ticker);
        pausedElapsed = 0;
        start();
    }

    @Override
    public void onShow() {
        syncTopBar();
    }

    @Override
    public void onEdit() {
    }

    @Override
    public void onClose() {
    }

    @Override
    public void syncTopBar() {
        host.setTopBar(false, false);
    }

    @Override
    public void destroy() {
        handler.removeCallbacks(ticker);
    }
}
