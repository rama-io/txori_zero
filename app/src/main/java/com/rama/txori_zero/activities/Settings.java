package com.rama.txori_zero.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import com.rama.txori_zero.R;
import com.rama.txori_zero.helpers.DialogHelper;
import com.rama.txori_zero.helpers.SystemBars;
import com.rama.txori_zero.managers.BackupManager;
import com.rama.txori_zero.managers.FontManager;
import com.rama.txori_zero.managers.PrefsManager;
import com.rama.txori_zero.managers.ThemeManager;
import com.rama.txori_zero.managers.ZoomManager;
import com.rama.txori_zero.objects.PrefTheme;
import com.rama.txori_zero.objects.Themes;
import com.rama.txori_zero.widgets.WdCheckbox;
import com.rama.txori_zero.widgets.WdRadio;
import com.rama.txori_zero.widgets.WdRadioGroup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Settings extends BaseActivity {
    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_IMPORT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        View root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        FontManager.apply(root, FontManager.getJersey25(this));
        setupSystem();
        setupNotifications();
        setupThemes();
        setupData();
        ThemeManager.applyTheme(this, root);
        findViewById(R.id.go_about).setOnClickListener(v -> startActivity(new Intent(Settings.this, About.class)));
        findViewById(R.id.go_back).setOnClickListener(v -> finish());
    }

    private void bindFlag(int id, final String key) {
        final PrefsManager prefs = PrefsManager.getInstance(this);
        WdCheckbox box = (WdCheckbox) findViewById(id);
        box.setChecked(prefs.getFlag(key));
        box.setOnCheckedChangeListener(checked -> prefs.setBoolean(key, checked));
    }

    private void setupSystem() {
        bindFlag(R.id.keep_screen_awake, PrefsManager.KEEP_AWAKE);
        final EditText zoom = (EditText) findViewById(R.id.zoom);
        zoom.setText(String.valueOf(ZoomManager.getPercent(this)));
        zoom.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) applyZoom();
            return false;
        });
        zoom.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyZoom();
        });
    }

    private void setupNotifications() {
        bindFlag(R.id.notify_sounds, PrefsManager.NOTIFY_SOUNDS);
        bindFlag(R.id.notify_vibrate, PrefsManager.NOTIFY_VIBRATE);
        bindFlag(R.id.notify_flash, PrefsManager.NOTIFY_FLASH);
        bindFlag(R.id.notify_camera, PrefsManager.NOTIFY_CAMERA);
        if (Build.VERSION.SDK_INT < 23) {
            // setTorchMode needs API 23
            findViewById(R.id.notify_camera).setVisibility(View.GONE);
            findViewById(R.id.notify_camera_separator).setVisibility(View.GONE);
        }
    }

    private void setupThemes() {
        if (Build.VERSION.SDK_INT < 11) {
            findViewById(R.id.themes_section).setVisibility(View.GONE);
            findViewById(R.id.themes_separator).setVisibility(View.GONE);
            return;
        }
        final PrefsManager prefs = PrefsManager.getInstance(this);
        WdRadioGroup group = (WdRadioGroup) findViewById(R.id.theme_group);
        boolean randomMode = PrefTheme.CATPPUCCIN_MOCHA_RANDOM.equals(prefs.getTheme());
        String current = ThemeManager.currentPalette(this).id;

        WdRadio random = group.addOption(getString(R.string.label_theme_random));
        random.setChecked(randomMode);
        random.setOnClickListener(v -> {
            prefs.setTheme(PrefTheme.CATPPUCCIN_MOCHA_RANDOM);
            ThemeManager.roll(Settings.this);
            ThemeManager.applyTheme(Settings.this, findViewById(R.id.root));
        });
        List<Themes.Palette> palettes = Themes.all();
        for (int i = 0; i < palettes.size(); i++) {
            final Themes.Palette palette = palettes.get(i);
            WdRadio radio = group.addOption(palette.label);
            radio.setChecked(!randomMode && palette.id.equals(current));
            radio.setOnClickListener(v -> {
                prefs.setTheme(palette.id);
                ThemeManager.applyTheme(Settings.this, findViewById(R.id.root));
            });
        }
        FontManager.apply(group, FontManager.getJersey25(this));
    }

    // Zoom

    private void applyZoom() {
        saveZoom();
        ZoomManager.apply(this, findViewById(R.id.root));
    }

    private void saveZoom() {
        EditText zoom = (EditText) findViewById(R.id.zoom);
        int percent;
        try {
            percent = ZoomManager.clamp(Integer.parseInt(zoom.getText().toString().trim()));
        } catch (NumberFormatException e) {
            percent = ZoomManager.getPercent(this);
        }
        String normalized = String.valueOf(percent);
        if (!normalized.equals(zoom.getText().toString())) zoom.setText(normalized);
        PrefsManager.getInstance(this).setZoomPercent(percent);
    }

    @Override
    protected void onPause() {
        saveZoom();
        super.onPause();
    }

    // Backup (Storage Access Framework, API 19+)

    private void setupData() {
        if (Build.VERSION.SDK_INT < 19) {
            findViewById(R.id.data_section).setVisibility(View.GONE);
            findViewById(R.id.data_separator).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.export_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "txori_zero_backup_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json");
            startActivityForResult(intent, REQUEST_EXPORT);
        });
        findViewById(R.id.import_button).setOnClickListener(v -> confirmImport());
    }

    private void confirmImport() {
        DialogHelper.show(this, R.layout.dialog_confirm, (view, dialog) -> view.findViewById(R.id.yes_button).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // JSON files are labelled inconsistently by file providers, the content is validated instead
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_IMPORT);
        }));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT) {
                BackupManager.Summary s = BackupManager.export(this, uri);
                toast(getString(R.string.toast_backup_exported, s.sessions, s.steps));
            } else if (requestCode == REQUEST_IMPORT) {
                BackupManager.Summary s = BackupManager.restore(this, uri);
                toast(getString(R.string.toast_backup_imported, s.sessions, s.steps));
                // Main loads its lists once, so restart it to pick up the new data.
                Intent intent = new Intent(this, Main.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        } catch (BackupManager.BackupException e) {
            toast(e.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
