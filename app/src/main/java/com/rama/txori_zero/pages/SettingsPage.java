package com.rama.txori_zero.pages;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.txori_zero.R;
import com.rama.txori_zero.activities.About;
import com.rama.txori_zero.activities.Main;
import com.rama.txori_zero.helpers.DialogHelper;
import com.rama.txori_zero.managers.BackupManager;
import com.rama.txori_zero.managers.FontManager;
import com.rama.txori_zero.managers.PrefsManager;
import com.rama.txori_zero.managers.ThemeManager;
import com.rama.txori_zero.managers.ZoomManager;
import com.rama.txori_zero.objects.PrefTheme;
import com.rama.txori_zero.objects.Themes;
import com.rama.txori_zero.widgets.WdCheckbox;
import com.rama.txori_zero.widgets.WdNavbar;
import com.rama.txori_zero.widgets.WdRadio;
import com.rama.txori_zero.widgets.WdRadioGroup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsPage implements Page {
    public static final int REQUEST_EXPORT = 1;
    public static final int REQUEST_IMPORT = 2;

    private final Main host;
    private final View root;
    private final PrefsManager prefs;

    public SettingsPage(Main host, View root) {
        this.host = host;
        this.root = root;
        prefs = PrefsManager.getInstance(host);
        setupSystem();
        setupTabOrder();
        setupNotifications();
        setupThemes();
        setupAccents();
        updateAccentVisibility();
        setupData();
        root.findViewById(R.id.go_about).setOnClickListener(v -> host.startActivity(new Intent(host, About.class)));
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
    }

    /** Persists what is still being typed. Called when leaving the page or the app. */
    public void flush() {
        saveZoom();
    }

    private void bindFlag(int id, final String key) {
        WdCheckbox box = (WdCheckbox) root.findViewById(id);
        box.setChecked(prefs.getFlag(key));
        box.setOnCheckedChangeListener(checked -> prefs.setBoolean(key, checked));
    }

    // System

    private void setupSystem() {
        WdCheckbox keepAwake = (WdCheckbox) root.findViewById(R.id.keep_screen_awake);
        keepAwake.setChecked(prefs.getFlag(PrefsManager.KEEP_AWAKE));
        keepAwake.setOnCheckedChangeListener(checked -> {
            prefs.setBoolean(PrefsManager.KEEP_AWAKE, checked);
            if (checked) host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });

        final EditText zoom = (EditText) root.findViewById(R.id.zoom);
        zoom.setText(String.valueOf(ZoomManager.getPercent(host)));
        zoom.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) applyZoom();
            return false;
        });
        zoom.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyZoom();
        });
    }

    private void applyZoom() {
        saveZoom();
        host.refreshTheme();
    }

    private void saveZoom() {
        EditText zoom = (EditText) root.findViewById(R.id.zoom);
        int percent;
        try {
            percent = ZoomManager.clamp(Integer.parseInt(zoom.getText().toString().trim()));
        } catch (NumberFormatException e) {
            percent = ZoomManager.getPercent(host);
        }
        String normalized = String.valueOf(percent);
        if (!normalized.equals(zoom.getText().toString())) zoom.setText(normalized);
        prefs.setZoomPercent(percent);
    }

    // Tabs order

    private int tabLabel(int page) {
        switch (page) {
            case WdNavbar.STOPWATCH:
                return R.string.alt_nav_stopwatch;
            case WdNavbar.TIMER:
                return R.string.alt_nav_timer;
            case WdNavbar.ROULETTE:
                return R.string.alt_nav_roulette;
            default:
                return R.string.alt_nav_home;
        }
    }

    private void setupTabOrder() {
        renderTabOrder();
    }

    /** One row per tab with up / down arrows, like the list editor. Rebuilt after every move. */
    private void renderTabOrder() {
        final int[] order = prefs.getTabOrder();
        LinearLayout list = (LinearLayout) root.findViewById(R.id.tab_order_list);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host);
        for (int i = 0; i < order.length; i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.list_item_tab_order, list, false);
            ((TextView) row.findViewById(R.id.tab_label)).setText(tabLabel(order[i]));
            View up = row.findViewById(R.id.tab_up);
            View down = row.findViewById(R.id.tab_down);
            // The ends can't move further; keep the space so the arrows stay aligned.
            up.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
            down.setVisibility(i == order.length - 1 ? View.INVISIBLE : View.VISIBLE);
            up.setOnClickListener(v -> moveTab(order, index, index - 1));
            down.setOnClickListener(v -> moveTab(order, index, index + 1));
            if (i > 0) {
                View separator = new View(host);
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, host.getResources().getDimensionPixelSize(R.dimen.space_between_elements)));
                list.addView(separator);
            }
            list.addView(row);
        }
        FontManager.apply(list, FontManager.getJersey25(host));
        ZoomManager.apply(host, list);
        ThemeManager.applyViews(host, list);
    }

    private void moveTab(int[] order, int from, int to) {
        int tmp = order[from];
        order[from] = order[to];
        order[to] = tmp;
        prefs.setTabOrder(order);
        renderTabOrder();
        // The navbar lives in Main and is already on screen, so reorder it right away.
        host.refreshTabOrder();
    }

    // Notifications

    private void setupNotifications() {
        bindFlag(R.id.notify_sounds, PrefsManager.NOTIFY_SOUNDS);
        bindFlag(R.id.notify_vibrate, PrefsManager.NOTIFY_VIBRATE);
        bindFlag(R.id.notify_flash, PrefsManager.NOTIFY_FLASH);
        bindFlag(R.id.notify_camera, PrefsManager.NOTIFY_CAMERA);
        if (Build.VERSION.SDK_INT < 23) {
            // setTorchMode needs API 23
            root.findViewById(R.id.notify_camera).setVisibility(View.GONE);
            root.findViewById(R.id.notify_camera_separator).setVisibility(View.GONE);
        }
    }

    // Theme and accent

    private void setupThemes() {
        if (Build.VERSION.SDK_INT < 11) {
            root.findViewById(R.id.themes_section).setVisibility(View.GONE);
            root.findViewById(R.id.themes_separator).setVisibility(View.GONE);
            return;
        }
        WdRadioGroup group = (WdRadioGroup) root.findViewById(R.id.theme_group);
        String current = prefs.getFlavor();
        for (final Themes.Flavor flavor : Themes.flavors()) {
            WdRadio radio = group.addOption(flavor.label);
            radio.setChecked(flavor.id.equals(current));
            radio.setOnClickListener(v -> {
                prefs.setFlavor(flavor.id);
                updateAccentVisibility();
                host.refreshTheme();
            });
        }
        FontManager.apply(group, FontManager.getJersey25(host));
    }

    /** Fixed palettes (the mono ones) have no accents, so the picker is hidden for them. */
    private void updateAccentVisibility() {
        int visibility = Build.VERSION.SDK_INT >= 11 && Themes.hasAccents(prefs.getFlavor()) ? View.VISIBLE : View.GONE;
        root.findViewById(R.id.accent_section).setVisibility(visibility);
        root.findViewById(R.id.accent_separator).setVisibility(visibility);
    }

    private void setupAccents() {
        if (Build.VERSION.SDK_INT < 11) return;
        WdRadioGroup group = (WdRadioGroup) root.findViewById(R.id.accent_group);
        String current = prefs.getAccent();
        boolean random = PrefTheme.ACCENT_RANDOM.equals(current);

        WdRadio randomRadio = group.addOption(host.getString(R.string.label_accent_random));
        randomRadio.setChecked(random);
        randomRadio.setOnClickListener(v -> {
            prefs.setAccent(PrefTheme.ACCENT_RANDOM);
            ThemeManager.roll(host);
            host.refreshTheme();
        });
        for (final Themes.Accent accent : Themes.accents()) {
            WdRadio radio = group.addOption(accent.label);
            radio.setChecked(!random && accent.id.equals(current));
            radio.setOnClickListener(v -> {
                prefs.setAccent(accent.id);
                host.refreshTheme();
            });
        }
        FontManager.apply(group, FontManager.getJersey25(host));
    }

    // Backup (Storage Access Framework, API 19+)

    private void setupData() {
        if (Build.VERSION.SDK_INT < 19) {
            root.findViewById(R.id.data_section).setVisibility(View.GONE);
            root.findViewById(R.id.data_separator).setVisibility(View.GONE);
            return;
        }
        root.findViewById(R.id.export_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "txori_zero_backup_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json");
            host.startActivityForResult(intent, REQUEST_EXPORT);
        });
        root.findViewById(R.id.import_button).setOnClickListener(v -> confirmImport());
    }

    private void confirmImport() {
        DialogHelper.show(host, R.layout.dialog_confirm, (view, dialog) -> view.findViewById(R.id.yes_button).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // JSON files are labelled inconsistently by file providers, the content is validated instead
            intent.setType("*/*");
            host.startActivityForResult(intent, REQUEST_IMPORT);
        }));
    }

    /** Forwarded by Main, which owns the activity result callback. */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Main.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT) {
                BackupManager.Summary s = BackupManager.export(host, uri);
                toast(host.getString(R.string.toast_backup_exported, s.sessions, s.steps));
            } else if (requestCode == REQUEST_IMPORT) {
                BackupManager.Summary s = BackupManager.restore(host, uri);
                toast(host.getString(R.string.toast_backup_imported, s.sessions, s.steps));
                // Main loads its lists once, so restart it to pick up the new data.
                Intent intent = new Intent(host, Main.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                host.startActivity(intent);
                host.finish();
            }
        } catch (BackupManager.BackupException e) {
            toast(e.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(host, message, Toast.LENGTH_LONG).show();
    }
}
