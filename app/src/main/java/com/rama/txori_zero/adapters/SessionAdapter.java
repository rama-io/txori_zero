package com.rama.txori_zero.adapters;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.rama.txori_zero.R;
import com.rama.txori_zero.helpers.DialogHelper;
import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.managers.DatabaseHelper;
import com.rama.txori_zero.managers.FontManager;
import com.rama.txori_zero.managers.PrefsManager;
import com.rama.txori_zero.managers.ThemeManager;
import com.rama.txori_zero.managers.ZoomManager;
import com.rama.txori_zero.objects.SessionItem;
import com.rama.txori_zero.objects.Task;
import com.rama.txori_zero.widgets.WdRadio;
import com.rama.txori_zero.widgets.WdRadioGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Home list: session headers with their task rows, in normal and edit mode. */
public class SessionAdapter extends BaseAdapter {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_HEADER_EDIT = 1;
    private static final int TYPE_TASK = 2;
    private static final int TYPE_TASK_EDIT = 3;

    public interface Listener {
        void onStartGroup(long sessionId, int rawIndex);

        void onResetGroup(long sessionId);

        /** The list was edited in place, themes and timers may need a refresh. */
        void onItemsChanged();
    }

    private final Activity context;
    private final ListView listView;
    private final List<SessionItem> items;
    private final SQLiteDatabase db;
    private final DatabaseHelper helper;
    private final PrefsManager prefs;
    private final Listener listener;

    private final Set<Long> collapsed;
    private final Set<Long> playing = new HashSet<Long>();
    private final Map<Long, Long> liveTotals = new HashMap<Long, Long>();
    private boolean editMode;
    private int activeIndex = -1;
    private float activeProgress;
    private float activeRestProgress;

    public SessionAdapter(Activity context, ListView listView, List<SessionItem> items, DatabaseHelper helper, SQLiteDatabase db, Listener listener) {
        this.context = context;
        this.listView = listView;
        this.items = items;
        this.helper = helper;
        this.db = db;
        this.listener = listener;
        prefs = PrefsManager.getInstance(context);
        collapsed = prefs.getCollapsedSessions();
    }

    // State

    public void setEditMode(boolean editing) {
        editMode = editing;
        notifyDataSetChanged();
    }

    public void stopAllPlaying() {
        playing.clear();
        notifyDataSetChanged();
    }

    public void setActiveItemIndex(int index) {
        activeIndex = index;
        activeProgress = 0;
        activeRestProgress = 0;
        notifyDataSetChanged();
    }

    public void setGroupPlaying(long sessionId, boolean value) {
        if (value) playing.add(sessionId);
        else playing.remove(sessionId);
        notifyDataSetChanged();
    }

    public void setLiveTotal(long sessionId, long remainingMs) {
        liveTotals.put(sessionId, remainingMs / 1000);
    }

    public void clearLiveTotal(long sessionId) {
        liveTotals.remove(sessionId);
    }

    public void setProgress(int rawIndex, float progress) {
        if (rawIndex != activeIndex) return;
        activeProgress = progress;
        View row = visibleRow(rawIndex);
        if (row != null) applyBar(row, R.id.progress_bg, progress);
    }

    public void setRestProgress(int rawIndex, float progress) {
        if (rawIndex != activeIndex) return;
        activeRestProgress = progress;
        View row = visibleRow(rawIndex);
        if (row != null) applyBar(row, R.id.progress_rest_bg, progress);
    }

    public void updateHeaderTimer(long sessionId, long remainingMs) {
        setLiveTotal(sessionId, remainingMs);
        for (int i = 0; i < items.size(); i++) {
            SessionItem item = items.get(i);
            if (item.isHeader() && item.sessionId == sessionId) {
                View row = visibleRow(i);
                if (row != null) {
                    TextView label = (TextView) row.findViewById(R.id.group_label);
                    if (label != null) label.setText(headerText(item));
                }
                return;
            }
        }
    }

    /** Re-reads everything from the database. Only valid while nothing is running. */
    public void reload() {
        items.clear();
        List<DatabaseHelper.Session> sessions = helper.getSessions(db);
        for (int i = 0; i < sessions.size(); i++) {
            DatabaseHelper.Session s = sessions.get(i);
            items.add(SessionItem.header(s.id, s.name));
            List<Task> tasks = helper.getSessionTasks(db, s.id);
            for (int j = 0; j < tasks.size(); j++) items.add(SessionItem.row(s.id, tasks.get(j)));
        }
        notifyDataSetChanged();
    }

    // Positions: a collapsed session hides its rows, so everything below shifts up

    private boolean hidden(SessionItem item) {
        return item.isRow() && collapsed.contains(item.sessionId);
    }

    public int visiblePosition(int rawIndex) {
        if (rawIndex < 0 || rawIndex >= items.size() || hidden(items.get(rawIndex))) return -1;
        int visible = 0;
        for (int i = 0; i < rawIndex; i++) {
            if (!hidden(items.get(i))) visible++;
        }
        return visible;
    }

    private int rawIndex(int visiblePosition) {
        int visible = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!hidden(items.get(i))) {
                if (visible == visiblePosition) return i;
                visible++;
            }
        }
        return visiblePosition;
    }

    private View visibleRow(int rawIndex) {
        int position = visiblePosition(rawIndex);
        if (position < 0) return null;
        return listView.getChildAt(position - listView.getFirstVisiblePosition());
    }

    // BaseAdapter

    @Override
    public int getCount() {
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!hidden(items.get(i))) count++;
        }
        return count;
    }

    @Override
    public Object getItem(int position) {
        return items.get(rawIndex(position));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        boolean header = items.get(rawIndex(position)).isHeader();
        if (header) return editMode ? TYPE_HEADER_EDIT : TYPE_HEADER;
        return editMode ? TYPE_TASK_EDIT : TYPE_TASK;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int raw = rawIndex(position);
        SessionItem item = items.get(raw);
        View view = convertView;
        if (view == null) {
            int layout;
            if (item.isHeader()) layout = editMode ? R.layout.list_item_header_edit : R.layout.list_item_header;
            else layout = editMode ? R.layout.list_item_task_edit : R.layout.list_item_task;
            view = LayoutInflater.from(context).inflate(layout, parent, false);
            FontManager.apply(view, FontManager.getJersey25(context));
        }
        if (item.isHeader()) bindHeader(view, item, raw);
        else bindTask(view, item, raw);
        ThemeManager.applyViews(context, view);
        ZoomManager.apply(context, view);
        return view;
    }

    // Headers

    private String headerText(SessionItem header) {
        long seconds;
        Long live = liveTotals.get(header.sessionId);
        if (live != null) {
            seconds = live;
        } else {
            seconds = 0;
            for (int i = 0; i < items.size(); i++) {
                SessionItem item = items.get(i);
                if (item.isRow() && item.sessionId == header.sessionId) {
                    seconds += Hms.toSeconds(item.task.duration) + Hms.toSeconds(item.task.restDuration);
                }
            }
        }
        int indicator = collapsed.contains(header.sessionId) ? R.string.label_expand_indicator : R.string.label_collapse_indicator;
        return context.getString(indicator) + " " + header.name + " :: " + Hms.clock(seconds);
    }

    private void bindHeader(View view, final SessionItem header, final int raw) {
        TextView label = (TextView) view.findViewById(R.id.group_label);
        label.setText(headerText(header));
        label.setOnClickListener(v -> {
            if (!collapsed.remove(header.sessionId)) collapsed.add(header.sessionId);
            prefs.setCollapsedSessions(collapsed);
            notifyDataSetChanged();
        });
        if (editMode) {
            view.findViewById(R.id.ascend_button).setOnClickListener(v -> moveSession(header.sessionId, -1));
            view.findViewById(R.id.descend_button).setOnClickListener(v -> moveSession(header.sessionId, 1));
            view.findViewById(R.id.edit_session_button).setOnClickListener(v -> showGroupDialog(header));
            view.findViewById(R.id.add_task).setOnClickListener(v -> showTaskDialog(null, header.sessionId));
        } else {
            ImageView icon = (ImageView) view.findViewById(R.id.start_group_icon);
            icon.setImageResource(playing.contains(header.sessionId) ? R.drawable.px_pause : R.drawable.px_play);
            view.findViewById(R.id.start_group).setOnClickListener(v -> listener.onStartGroup(header.sessionId, raw + 1));
            view.findViewById(R.id.reset_group).setOnClickListener(v -> listener.onResetGroup(header.sessionId));
        }
    }

    /** Swaps a whole session block (header and rows) with its neighbour. */
    private void moveSession(long sessionId, int direction) {
        List<List<SessionItem>> blocks = new ArrayList<List<SessionItem>>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isHeader()) blocks.add(new ArrayList<SessionItem>());
            blocks.get(blocks.size() - 1).add(items.get(i));
        }
        int from = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).get(0).sessionId == sessionId) from = i;
        }
        int to = from + direction;
        if (from < 0 || to < 0 || to >= blocks.size()) return;
        helper.swapSessionOrder(db, sessionId, blocks.get(to).get(0).sessionId);
        List<SessionItem> moved = blocks.get(from);
        blocks.set(from, blocks.get(to));
        blocks.set(to, moved);
        items.clear();
        for (int i = 0; i < blocks.size(); i++) items.addAll(blocks.get(i));
        notifyDataSetChanged();
        listener.onItemsChanged();
    }

    // Task rows

    private void bindTask(View view, final SessionItem row, final int raw) {
        ((TextView) view.findViewById(R.id.task_label)).setText(row.task.label);
        ((TextView) view.findViewById(R.id.task_duration)).setText(Hms.display(row.task.duration));

        int total = 0;
        int index = 0;
        for (int i = 0; i < items.size(); i++) {
            SessionItem other = items.get(i);
            if (other.isRow() && other.sessionId == row.sessionId && other.task.label.equals(row.task.label)) {
                if (i == raw) index = total;
                total++;
            }
        }
        TextView frequency = (TextView) view.findViewById(R.id.task_frequency);
        frequency.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        frequency.setText((index + 1) + " / " + total);

        if (editMode) {
            view.findViewById(R.id.ascend_button).setOnClickListener(v -> moveTask(raw, -1));
            view.findViewById(R.id.descend_button).setOnClickListener(v -> moveTask(raw, 1));
            view.findViewById(R.id.edit_task_button).setOnClickListener(v -> showTaskDialog(row, row.sessionId));
        } else {
            applyBar(view, R.id.progress_bg, raw == activeIndex ? activeProgress : 0f);
            applyBar(view, R.id.progress_rest_bg, raw == activeIndex ? activeRestProgress : 0f);
        }
    }

    private void moveTask(int raw, int direction) {
        int other = raw + direction;
        if (other < 0 || other >= items.size()) return;
        SessionItem a = items.get(raw);
        SessionItem b = items.get(other);
        if (!b.isRow() || b.sessionId != a.sessionId) return;
        helper.swapStepOrder(db, a.task.stepId, b.task.stepId);
        items.set(raw, b);
        items.set(other, a);
        notifyDataSetChanged();
        listener.onItemsChanged();
    }

    private void applyBar(final View row, int barId, final float progress) {
        final View container = row.findViewById(R.id.row_container);
        final View bar = row.findViewById(barId);
        if (container == null || bar == null) return;
        container.post(() -> {
            int width = container.getWidth();
            if (width > 0) {
                bar.getLayoutParams().width = (int) (width * progress);
                bar.requestLayout();
            }
        });
    }

    // Dialogs

    public void showGroupDialog(final SessionItem header) {
        DialogHelper.show(context, R.layout.dialog_group, (view, dialog) -> {
            ((TextView) view.findViewById(R.id.dialog_title)).setText(header == null ? R.string.header_add_group : R.string.header_edit_group);
            final EditText input = (EditText) view.findViewById(R.id.edit_text);
            View delete = view.findViewById(R.id.delete_button);
            if (header == null) {
                ((TextView) view.findViewById(R.id.yes_button)).setText(R.string.btn_create);
                delete.setVisibility(View.GONE);
            } else {
                input.setText(header.name);
                input.setSelection(input.getText().length());
                delete.setOnClickListener(v -> {
                    helper.deleteSession(db, header.sessionId);
                    collapsed.remove(header.sessionId);
                    prefs.setCollapsedSessions(collapsed);
                    reload();
                    listener.onItemsChanged();
                    dialog.dismiss();
                });
            }
            view.findViewById(R.id.yes_button).setOnClickListener(v -> {
                String name = FontManager.sanitizeForFont(input.getText().toString().trim());
                if (name.length() == 0) {
                    input.setError(context.getString(R.string.toast_name_empty));
                    return;
                }
                if (header == null) helper.createSession(db, name);
                else helper.renameSession(db, header.sessionId, name);
                reload();
                listener.onItemsChanged();
                dialog.dismiss();
            });
        });
    }

    /** row == null adds a task to sessionId, otherwise edits row. */
    private void showTaskDialog(final SessionItem row, final long sessionId) {
        DialogHelper.show(context, R.layout.dialog_task, (view, dialog) -> {
            ((TextView) view.findViewById(R.id.dialog_title)).setText(row == null ? R.string.header_add_task : R.string.header_edit_task);
            final AutoCompleteTextView label = (AutoCompleteTextView) view.findViewById(R.id.task_label);
            final EditText duration = (EditText) view.findViewById(R.id.task_duration);
            final EditText rest = (EditText) view.findViewById(R.id.task_rest);
            final WdRadioGroup groups = (WdRadioGroup) view.findViewById(R.id.task_groups);
            View delete = view.findViewById(R.id.delete_button);

            List<String> labels = new ArrayList<String>();
            List<Task> all = helper.getAllTasks(db);
            for (int i = 0; i < all.size(); i++) {
                if (!labels.contains(all.get(i).label)) labels.add(all.get(i).label);
            }
            label.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, labels));

            final List<DatabaseHelper.Session> sessions = helper.getSessions(db);
            for (int i = 0; i < sessions.size(); i++) {
                WdRadio radio = groups.addOption(sessions.get(i).name);
                if (sessions.get(i).id == sessionId) radio.setChecked(true);
            }
            FontManager.apply(groups, FontManager.getJersey25(context));
            ThemeManager.applyViews(context, groups);
            ZoomManager.apply(context, groups);

            if (row == null) {
                duration.setText("000100");
                rest.setText("000100");
                ((TextView) view.findViewById(R.id.yes_button)).setText(R.string.btn_create);
                delete.setVisibility(View.GONE);
            } else {
                label.setText(row.task.label);
                duration.setText(row.task.duration);
                rest.setText(row.task.restDuration);
                delete.setOnClickListener(v -> {
                    helper.removeStep(db, row.task.stepId);
                    reload();
                    listener.onItemsChanged();
                    dialog.dismiss();
                });
            }

            view.findViewById(R.id.yes_button).setOnClickListener(v -> {
                String text = FontManager.sanitizeForFont(label.getText().toString().trim());
                if (text.length() == 0) {
                    label.setError(context.getString(R.string.toast_label_empty));
                    return;
                }
                String d = Hms.normalize(duration.getText().toString());
                String r = Hms.normalize(rest.getText().toString());
                int picked = groups.getCheckedIndex();
                long target = picked >= 0 ? sessions.get(picked).id : sessionId;
                if (row == null) {
                    helper.addTaskToSession(db, target, text, d, r);
                } else {
                    helper.updateTask(db, row.task.id, text, d, r);
                    if (target != sessionId) helper.moveStep(db, row.task.stepId, target);
                }
                reload();
                listener.onItemsChanged();
                dialog.dismiss();
            });
        });
    }
}
