package com.rama.txori_zero.pages;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.rama.txori_zero.R;
import com.rama.txori_zero.activities.Main;
import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.managers.NotifyManager;
import com.rama.txori_zero.managers.PrefsManager;

public class TimerPage implements Page {
    private final Main host;
    private final PrefsManager prefs;
    private final Button timerButton;
    private final View editView;
    private final EditText input;
    private final Button saveButton;
    private final Button resetButton;
    private final Handler handler = new Handler();

    private boolean running;
    private boolean editing;
    private long initialMs;
    private long remainingMs;
    private long startTime;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long left = remainingMs - (SystemClock.elapsedRealtime() - startTime);
            if (left <= 0) {
                running = false;
                remainingMs = 0;
                timerButton.setText(R.string.clock_zero_full);
                NotifyManager.finish(host, host.flashAction());
                return;
            }
            timerButton.setText(Hms.clockMs(left));
            handler.postDelayed(this, 50);
        }
    };

    public TimerPage(Main host, View root) {
        this.host = host;
        prefs = PrefsManager.getInstance(host);
        timerButton = (Button) root.findViewById(R.id.timer_button);
        editView = root.findViewById(R.id.timer_edit_view);
        input = (EditText) root.findViewById(R.id.timer_input);
        saveButton = (Button) root.findViewById(R.id.timer_save);
        resetButton = (Button) root.findViewById(R.id.timer_reset);

        String saved = Hms.normalize(prefs.getAppTimer());
        initialMs = Hms.toMs(saved);
        remainingMs = initialMs;
        timerButton.setText(Hms.display(saved));

        timerButton.setOnClickListener(v -> toggle());
        timerButton.setOnLongClickListener(v -> {
            reset();
            return true;
        });
        resetButton.setOnClickListener(v -> reset());
        saveButton.setOnClickListener(v -> save());
    }

    private void toggle() {
        if (initialMs <= 0) return;
        if (running) pause();
        else if (remainingMs <= 0) reset();
        else start();
    }

    private void start() {
        if (remainingMs <= 0) return;
        running = true;
        startTime = SystemClock.elapsedRealtime();
        handler.post(ticker);
    }

    private void pause() {
        if (!running) return;
        remainingMs -= SystemClock.elapsedRealtime() - startTime;
        running = false;
        handler.removeCallbacks(ticker);
    }

    private void reset() {
        handler.removeCallbacks(ticker);
        remainingMs = initialMs;
        timerButton.setText(Hms.clockMs(initialMs));
        start();
    }

    private void save() {
        String digits = Hms.normalize(input.getText().toString());
        handler.removeCallbacks(ticker);
        running = false;
        initialMs = Hms.toMs(digits);
        remainingMs = initialMs;
        timerButton.setText(Hms.display(digits));
        prefs.setAppTimer(digits);
        Toast.makeText(host, R.string.toast_timer_saved, Toast.LENGTH_SHORT).show();
        setEditing(false);
    }

    private void setEditing(boolean value) {
        editing = value;
        InputMethodManager imm = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (editing) {
            timerButton.setVisibility(View.GONE);
            editView.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            resetButton.setVisibility(View.GONE);
            String digits = Hms.digits(Hms.normalize(prefs.getAppTimer()));
            input.setText(digits.replaceFirst("^0+", ""));
            input.setSelection(input.getText().length());
            input.requestFocus();
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        } else {
            editView.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            resetButton.setVisibility(View.VISIBLE);
            timerButton.setVisibility(View.VISIBLE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        syncTopBar();
    }

    @Override
    public void onShow() {
        syncTopBar();
    }

    @Override
    public void onEdit() {
        setEditing(true);
    }

    @Override
    public void onClose() {
        setEditing(false);
    }

    @Override
    public void syncTopBar() {
        host.setTopBar(!editing, editing);
    }

    @Override
    public void destroy() {
        handler.removeCallbacks(ticker);
    }
}
