package com.rama.txori_zero.pages;

import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.txori_zero.R;
import com.rama.txori_zero.activities.Main;
import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.managers.DatabaseHelper;
import com.rama.txori_zero.managers.FontManager;
import com.rama.txori_zero.managers.NotifyManager;
import com.rama.txori_zero.managers.PrefsManager;
import com.rama.txori_zero.managers.ThemeManager;
import com.rama.txori_zero.managers.ZoomManager;
import com.rama.txori_zero.objects.Task;
import com.rama.txori_zero.widgets.WdRadioGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Picks a list, shuffles its tasks and gives each one the same short timer. */
public class RoulettePage implements Page {
    private final Main host;
    private final DatabaseHelper helper;
    private final SQLiteDatabase db;
    private final PrefsManager prefs;
    private final View chooseSection;
    private final View runningSection;
    private final EditText timerInput;
    private final WdRadioGroup lists;
    private final Button counter;
    private final TextView taskName;
    private final Button startButton;
    private final Button nextButton;
    private final Handler handler = new Handler();

    private final List<Long> sessionIds = new ArrayList<Long>();
    private List<Task> shuffled = new ArrayList<Task>();
    private int currentIndex;
    private boolean started;
    private boolean running;
    private boolean waiting;
    private long timerMs;
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
                waiting = true;
                counter.setText(R.string.status_go);
                NotifyManager.finish(host, host.flashAction());
                return;
            }
            counter.setText(Hms.clockMs(left));
            handler.postDelayed(this, 50);
        }
    };

    public RoulettePage(Main host, View root, DatabaseHelper helper, SQLiteDatabase db) {
        this.host = host;
        this.helper = helper;
        this.db = db;
        prefs = PrefsManager.getInstance(host);
        chooseSection = root.findViewById(R.id.roulette_choose);
        runningSection = root.findViewById(R.id.roulette_running);
        timerInput = (EditText) root.findViewById(R.id.roulette_timer_input);
        lists = (WdRadioGroup) root.findViewById(R.id.roulette_lists);
        counter = (Button) root.findViewById(R.id.roulette_counter);
        taskName = (TextView) root.findViewById(R.id.roulette_task_name);
        startButton = (Button) root.findViewById(R.id.roulette_start);
        nextButton = (Button) root.findViewById(R.id.roulette_next);

        String saved = Hms.normalize(prefs.getRouletteTimer());
        timerMs = Hms.toMs(saved);
        timerInput.setText(saved.replaceFirst("^0+", ""));

        root.findViewById(R.id.roulette_timer_save).setOnClickListener(v -> saveTimer());
        startButton.setOnClickListener(v -> start());
        nextButton.setOnClickListener(v -> next());
        View.OnClickListener tap = v -> {
            if (waiting) next();
            else if (running) pause();
            else resume();
        };
        counter.setOnClickListener(tap);
        runningSection.setOnClickListener(tap);
        populateLists();
    }

    private void saveTimer() {
        String digits = Hms.normalize(timerInput.getText().toString());
        timerMs = Hms.toMs(digits);
        prefs.setRouletteTimer(digits);
        timerInput.clearFocus();
        Toast.makeText(host, R.string.toast_timer_saved, Toast.LENGTH_SHORT).show();
    }

    private void populateLists() {
        List<DatabaseHelper.Session> sessions = helper.getSessions(db);
        sessionIds.clear();
        lists.removeAllOptions();
        long saved = prefs.getRouletteList();
        int pick = -1;
        for (int i = 0; i < sessions.size(); i++) {
            DatabaseHelper.Session s = sessions.get(i);
            sessionIds.add(s.id);
            lists.addOption(s.name);
            if (s.id == saved) pick = i;
            else if (pick == -1 && "Roulette".equals(s.name) && saved == -1) pick = i;
        }
        if (pick == -1 && !sessions.isEmpty()) pick = 0;
        if (pick >= 0) lists.checkIndex(pick);
        lists.setOnCheckedChangeListener((group, id) -> {
            int index = group.getCheckedIndex();
            if (index >= 0 && index < sessionIds.size()) prefs.setRouletteList(sessionIds.get(index));
        });
        FontManager.apply(lists, FontManager.getJersey25(host));
        ZoomManager.apply(host, lists);
        ThemeManager.applyViews(host, lists);
    }

    private void start() {
        int index = lists.getCheckedIndex();
        if (index < 0 || index >= sessionIds.size()) return;
        List<Task> tasks = helper.getSessionTasks(db, sessionIds.get(index));
        if (tasks.isEmpty()) return;
        shuffled = new ArrayList<Task>(tasks);
        Collections.shuffle(shuffled);
        currentIndex = 0;
        started = true;
        showRunning();
        showCurrent();
    }

    private void showCurrent() {
        taskName.setText(shuffled.get(currentIndex).label);
        waiting = false;
        remainingMs = timerMs;
        counter.setText(Hms.clockMs(remainingMs));
        startTimer();
    }

    private void startTimer() {
        if (remainingMs <= 0) return;
        running = true;
        startTime = SystemClock.elapsedRealtime();
        handler.post(ticker);
    }

    private void pause() {
        remainingMs -= SystemClock.elapsedRealtime() - startTime;
        running = false;
        handler.removeCallbacks(ticker);
    }

    private void resume() {
        startTimer();
    }

    private void next() {
        handler.removeCallbacks(ticker);
        running = false;
        currentIndex++;
        if (currentIndex >= shuffled.size()) {
            finishRoulette();
            return;
        }
        showCurrent();
    }

    private void finishRoulette() {
        started = false;
        running = false;
        waiting = false;
        handler.removeCallbacks(ticker);
        runningSection.setVisibility(View.GONE);
        chooseSection.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.GONE);
        startButton.setVisibility(View.VISIBLE);
        syncTopBar();
    }

    private void showRunning() {
        chooseSection.setVisibility(View.GONE);
        runningSection.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.VISIBLE);
        syncTopBar();
    }

    @Override
    public void onShow() {
        if (!started) populateLists();
        syncTopBar();
    }

    @Override
    public void onEdit() {
    }

    @Override
    public void onClose() {
        finishRoulette();
    }

    @Override
    public void syncTopBar() {
        host.setTopBar(false, started);
    }

    @Override
    public void destroy() {
        handler.removeCallbacks(ticker);
    }
}
