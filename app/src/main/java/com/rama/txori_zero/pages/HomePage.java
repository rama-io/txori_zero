package com.rama.txori_zero.pages;

import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.rama.txori_zero.R;
import com.rama.txori_zero.activities.Main;
import com.rama.txori_zero.adapters.SessionAdapter;
import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.managers.DatabaseHelper;
import com.rama.txori_zero.managers.NotifyManager;
import com.rama.txori_zero.managers.ThemeManager;
import com.rama.txori_zero.managers.WorkoutManager;
import com.rama.txori_zero.objects.SessionItem;

import java.util.ArrayList;
import java.util.List;

/** The lists screen: pick a session, run its tasks one after another, or edit lists. */
public class HomePage implements Page, WorkoutManager.Listener, SessionAdapter.Listener {
    private final Main host;
    private final View root;
    private final ListView listView;
    private final TextView taskName;
    private final TextView timer;
    private final TextView nextTask;
    private final View controllers;
    private final View timeContainer;
    private final View addGroupButton;
    private final ImageView playPauseIcon;
    private final View activeHeader;
    private final TextView activeLabel;
    private final ImageView activePlayPauseIcon;

    private final List<SessionItem> items = new ArrayList<SessionItem>();
    private final SessionAdapter adapter;
    private final WorkoutManager workout;
    private boolean editing;
    private String activeName = "";

    public HomePage(Main host, View root, DatabaseHelper helper, SQLiteDatabase db) {
        this.host = host;
        this.root = root;
        listView = (ListView) root.findViewById(R.id.task_list);
        taskName = (TextView) root.findViewById(R.id.current_task_name);
        timer = (TextView) root.findViewById(R.id.current_task_timer);
        nextTask = (TextView) root.findViewById(R.id.next_task_name);
        controllers = root.findViewById(R.id.controllers);
        timeContainer = root.findViewById(R.id.time_container);
        addGroupButton = root.findViewById(R.id.add_group_button);
        playPauseIcon = (ImageView) root.findViewById(R.id.play_pause_icon);
        activeHeader = root.findViewById(R.id.active_group_header);
        activeLabel = (TextView) root.findViewById(R.id.active_group_label);
        activePlayPauseIcon = (ImageView) root.findViewById(R.id.active_play_pause_icon);

        workout = new WorkoutManager(this, () -> NotifyManager.tick(host), () -> NotifyManager.finish(host, host.flashAction()));
        workout.items = items;
        adapter = new SessionAdapter(host, listView, items, helper, db, this);
        adapter.reload();
        listView.setAdapter(adapter);

        root.findViewById(R.id.repeat_task).setOnClickListener(v -> workout.repeatCurrentTask());
        root.findViewById(R.id.add_time).setOnClickListener(v -> workout.addTime(30000L));
        root.findViewById(R.id.play_pause).setOnClickListener(v -> workout.togglePlayPause());
        root.findViewById(R.id.skip_task).setOnClickListener(v -> workout.skipTask());
        root.findViewById(R.id.active_reset_group).setOnClickListener(v -> workout.resetGroup(workout.getActiveSessionId()));
        root.findViewById(R.id.active_start_group).setOnClickListener(v -> workout.togglePlayPause());
        addGroupButton.setOnClickListener(v -> adapter.showGroupDialog(null));
    }

    public void refreshTheme() {
        adapter.notifyDataSetChanged();
    }

    // Page

    @Override
    public void onShow() {
        syncTopBar();
    }

    @Override
    public void onEdit() {
        editing = true;
        workout.stopAndClear();
        adapter.stopAllPlaying();
        hideActiveHeader();
        taskName.setText(R.string.status_greeting);
        timer.setText(R.string.clock_zero);
        nextTask.setText(R.string.status_next_placeholder);
        controllers.setVisibility(View.GONE);
        timeContainer.setVisibility(View.GONE);
        addGroupButton.setVisibility(View.VISIBLE);
        adapter.setEditMode(true);
        syncTopBar();
    }

    @Override
    public void onClose() {
        editing = false;
        addGroupButton.setVisibility(View.GONE);
        timeContainer.setVisibility(View.VISIBLE);
        adapter.setEditMode(false);
        syncTopBar();
    }

    @Override
    public void syncTopBar() {
        host.setTopBar(!editing && !workout.isRunning(), editing);
    }

    @Override
    public void destroy() {
        workout.release();
    }

    // SessionAdapter.Listener

    @Override
    public void onStartGroup(long sessionId, int rawIndex) {
        workout.startGroup(sessionId, rawIndex);
    }

    @Override
    public void onResetGroup(long sessionId) {
        workout.resetGroup(sessionId);
    }

    @Override
    public void onItemsChanged() {
        host.refreshTheme();
    }

    // WorkoutManager.Listener

    @Override
    public void onTaskStarted(int index, String label, long remainingMs) {
        taskName.setText(label);
        showTime(remainingMs);
        adapter.setActiveItemIndex(index);
        showNext(index, workout.getActiveSessionId());
        controllers.setVisibility(View.VISIBLE);
        showActiveHeader();
        final int position = adapter.visiblePosition(index);
        if (position >= 0) {
            listView.post(() -> listView.setSelectionFromTop(position, 0));
        }
    }

    @Override
    public void onTaskTick(int index, long remainingMs, float progress) {
        showTime(remainingMs);
        adapter.setProgress(index, progress);
    }

    @Override
    public void onTaskFinished(int index) {
        adapter.setProgress(index, 1f);
    }

    @Override
    public void onRestStarted(int index, long restDurationMs) {
        taskName.setText(R.string.status_resting);
        showTime(restDurationMs);
        adapter.setRestProgress(index, 0f);
    }

    @Override
    public void onRestTick(int index, long remainingMs, float progress) {
        showTime(remainingMs);
        adapter.setRestProgress(index, progress);
    }

    @Override
    public void onRestFinished(int index) {
        adapter.setRestProgress(index, 0f);
    }

    @Override
    public void onSessionTick(long sessionId, long remainingMs) {
        adapter.updateHeaderTimer(sessionId, remainingMs);
        showActiveTotal(remainingMs);
    }

    @Override
    public void onPlayingStateChanged(long sessionId, boolean playing, Long headerRemainingMs) {
        if (headerRemainingMs != null) adapter.setLiveTotal(sessionId, headerRemainingMs);
        else adapter.clearLiveTotal(sessionId);
        adapter.setGroupPlaying(sessionId, playing);
        int icon = playing ? R.drawable.px_pause : R.drawable.px_play;
        playPauseIcon.setImageResource(icon);
        activePlayPauseIcon.setImageResource(icon);
        syncTopBar();
    }

    @Override
    public void onGroupFinished(long sessionId) {
        taskName.setText(R.string.status_done);
        idle(sessionId);
        syncTopBar();
    }

    @Override
    public void onGroupReset(long sessionId) {
        taskName.setText(R.string.status_greeting);
        idle(sessionId);
        syncTopBar();
    }

    // Helpers

    private void idle(long sessionId) {
        timer.setText(R.string.clock_zero);
        nextTask.setText(R.string.status_next_placeholder);
        adapter.clearLiveTotal(sessionId);
        adapter.setActiveItemIndex(-1);
        adapter.setGroupPlaying(sessionId, false);
        playPauseIcon.setImageResource(R.drawable.px_play);
        controllers.setVisibility(View.GONE);
        hideActiveHeader();
    }

    private void showTime(long ms) {
        timer.setText(Hms.clockMs(ms));
    }

    private void showNext(int currentIndex, long sessionId) {
        for (int i = currentIndex + 1; i < items.size(); i++) {
            SessionItem item = items.get(i);
            if (item.isRow() && item.sessionId == sessionId) {
                nextTask.setText(host.getString(R.string.status_next_task, item.task.label));
                return;
            }
        }
        nextTask.setText(R.string.status_next_placeholder);
    }

    private void showActiveHeader() {
        for (int i = 0; i < items.size(); i++) {
            SessionItem item = items.get(i);
            if (item.isHeader() && item.sessionId == workout.getActiveSessionId()) {
                activeName = item.name;
                activeHeader.setVisibility(View.VISIBLE);
                showActiveTotal(workout.getGlobalRemainingMs());
                ThemeManager.applyViews(host, activeHeader);
                return;
            }
        }
    }

    private void showActiveTotal(long ms) {
        activeLabel.setText(activeName + " :: " + Hms.clockMs(ms));
    }

    private void hideActiveHeader() {
        activeHeader.setVisibility(View.GONE);
        activeName = "";
    }
}
