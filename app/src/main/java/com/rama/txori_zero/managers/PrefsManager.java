package com.rama.txori_zero.managers;

import android.content.Context;
import android.content.SharedPreferences;

import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.objects.PrefTheme;
import com.rama.txori_zero.objects.Themes;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class PrefsManager {
    private static final String PREFS_NAME = "txori_zero";

    public static final String KEEP_AWAKE = "settings:keep_awake";
    public static final String NOTIFY_SOUNDS = "notification:sounds";
    public static final String NOTIFY_VIBRATE = "notification:vibrate";
    public static final String NOTIFY_FLASH = "notification:flash";
    public static final String NOTIFY_CAMERA = "notification:flash_camera";

    private static final String KEY_THEME = "settings:theme";
    private static final String KEY_THEME_ROLLED_ID = "settings:theme_rolled_id";
    private static final String KEY_ZOOM_PERCENT = "settings:zoom_percent";
    private static final String KEY_APP_TIMER = "app:timer";
    private static final String KEY_ROULETTE_TIMER = "roulette:timer";
    private static final String KEY_ROULETTE_LIST = "roulette:list";
    private static final String KEY_COLLAPSED = "session:collapsed_ids";
    private static final String DEFAULT_TIMER = "003000";

    private static final String[] BACKUP_BOOLEANS = {KEEP_AWAKE, NOTIFY_SOUNDS, NOTIFY_VIBRATE, NOTIFY_FLASH, NOTIFY_CAMERA};

    private static PrefsManager instance;
    private final SharedPreferences prefs;

    private PrefsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefsManager(context);
        }
        return instance;
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, PrefTheme.DEFAULT);
    }

    public void setTheme(String themeId) {
        prefs.edit().putString(KEY_THEME, themeId).commit();
    }

    public String getRolledThemeId() {
        return prefs.getString(KEY_THEME_ROLLED_ID, PrefTheme.CATPPUCCIN_MOCHA_MAUVE);
    }

    public void setRolledTheme(String themeId) {
        prefs.edit().putString(KEY_THEME_ROLLED_ID, themeId).commit();
    }

    public int getZoomPercent() {
        return prefs.getInt(KEY_ZOOM_PERCENT, 100);
    }

    public void setZoomPercent(int percent) {
        prefs.edit().putInt(KEY_ZOOM_PERCENT, percent).commit();
    }

    public String getAppTimer() {
        return prefs.getString(KEY_APP_TIMER, DEFAULT_TIMER);
    }

    public void setAppTimer(String hhmmss) {
        prefs.edit().putString(KEY_APP_TIMER, hhmmss).commit();
    }

    public String getRouletteTimer() {
        return prefs.getString(KEY_ROULETTE_TIMER, DEFAULT_TIMER);
    }

    public void setRouletteTimer(String hhmmss) {
        prefs.edit().putString(KEY_ROULETTE_TIMER, hhmmss).commit();
    }

    public long getRouletteList() {
        return prefs.getLong(KEY_ROULETTE_LIST, -1);
    }

    public void setRouletteList(long sessionId) {
        prefs.edit().putLong(KEY_ROULETTE_LIST, sessionId).commit();
    }

    public Set<Long> getCollapsedSessions() {
        Set<Long> result = new HashSet<Long>();
        String raw = prefs.getString(KEY_COLLAPSED, "");
        String[] parts = raw.split(",");
        for (String part : parts) {
            try {
                result.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // empty or malformed entry
            }
        }
        return result;
    }

    public void setCollapsedSessions(Set<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs.edit().putString(KEY_COLLAPSED, sb.toString()).commit();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
    }

    /** Boolean setting with its real default (only sounds start enabled). */
    public boolean getFlag(String key) {
        return getBoolean(key, NOTIFY_SOUNDS.equals(key));
    }

    /** Settings that travel with a backup. Session ids are recreated on import, so id based keys are left out. */
    public JSONObject exportSettings() {
        JSONObject json = new JSONObject();
        try {
            json.put(KEY_THEME, getTheme());
            json.put(KEY_ZOOM_PERCENT, getZoomPercent());
            json.put(KEY_APP_TIMER, Hms.normalize(getAppTimer()));
            json.put(KEY_ROULETTE_TIMER, Hms.normalize(getRouletteTimer()));
            for (String key : BACKUP_BOOLEANS) {
                json.put(key, getFlag(key));
            }
        } catch (JSONException ignored) {
            // keys are never null, so this cannot happen
        }
        return json;
    }

    /** Only known keys are read, so backups from other builds cannot inject anything else. */
    public void importSettings(JSONObject json) {
        SharedPreferences.Editor editor = prefs.edit();
        String theme = json.optString(KEY_THEME, null);
        if (theme != null && (PrefTheme.CATPPUCCIN_MOCHA_RANDOM.equals(theme) || Themes.byId(theme).id.equals(theme))) {
            editor.putString(KEY_THEME, theme);
        }
        int zoom = json.optInt(KEY_ZOOM_PERCENT, 0);
        if (zoom > 0) editor.putInt(KEY_ZOOM_PERCENT, ZoomManager.clamp(zoom));
        if (json.has(KEY_APP_TIMER)) editor.putString(KEY_APP_TIMER, Hms.normalize(json.optString(KEY_APP_TIMER)));
        if (json.has(KEY_ROULETTE_TIMER)) editor.putString(KEY_ROULETTE_TIMER, Hms.normalize(json.optString(KEY_ROULETTE_TIMER)));
        for (String key : BACKUP_BOOLEANS) {
            if (json.has(key)) editor.putBoolean(key, json.optBoolean(key));
        }
        editor.remove(KEY_ROULETTE_LIST).remove(KEY_COLLAPSED);
        editor.commit();
    }
}
