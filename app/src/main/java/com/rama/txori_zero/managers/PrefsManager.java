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

    private static final String KEY_FLAVOR = "settings:flavor";
    private static final String KEY_ACCENT = "settings:accent";
    private static final String KEY_ACCENT_ROLLED = "settings:accent_rolled";
    /** Older builds stored flavor and accent as one id, e.g. "catppuccin_mocha_blue". Only read to migrate. */
    private static final String KEY_LEGACY_THEME = "settings:theme";
    private static final String KEY_LEGACY_THEME_ROLLED = "settings:theme_rolled_id";
    private static final String LEGACY_MOCHA_PREFIX = "catppuccin_mocha_";
    private static final String KEY_TAB_ORDER = "settings:tab_order";
    private static final String DEFAULT_TAB_ORDER = "2,0,1,3";
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
        migrateLegacyTheme();
    }

    /** Splits an old single theme id into {flavor, accent}, or null when it is not one. */
    private static String[] parseLegacyTheme(String id) {
        if (id == null) return null;
        if (id.startsWith(LEGACY_MOCHA_PREFIX)) {
            String accent = id.substring(LEGACY_MOCHA_PREFIX.length());
            if (PrefTheme.ACCENT_RANDOM.equals(accent) || Themes.isAccent(accent)) {
                return new String[]{"mocha", accent};
            }
            return null;
        }
        // Latte used to be fixed to its blue accent.
        if ("catppuccin_latte".equals(id)) return new String[]{"latte", "blue"};
        if (Themes.isFlavor(id) && !Themes.hasAccents(id)) return new String[]{id, PrefTheme.DEFAULT_ACCENT};
        return null;
    }

    private void migrateLegacyTheme() {
        if (!prefs.contains(KEY_LEGACY_THEME) && !prefs.contains(KEY_LEGACY_THEME_ROLLED)) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains(KEY_FLAVOR)) {
            String[] legacy = parseLegacyTheme(prefs.getString(KEY_LEGACY_THEME, null));
            if (legacy != null) {
                editor.putString(KEY_FLAVOR, legacy[0]);
                editor.putString(KEY_ACCENT, legacy[1]);
            }
        }
        String[] rolled = parseLegacyTheme(prefs.getString(KEY_LEGACY_THEME_ROLLED, null));
        if (rolled != null && Themes.isAccent(rolled[1]) && !prefs.contains(KEY_ACCENT_ROLLED)) {
            editor.putString(KEY_ACCENT_ROLLED, rolled[1]);
        }
        editor.remove(KEY_LEGACY_THEME).remove(KEY_LEGACY_THEME_ROLLED).commit();
    }

    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefsManager(context);
        }
        return instance;
    }

    public String getFlavor() {
        String id = prefs.getString(KEY_FLAVOR, PrefTheme.DEFAULT_FLAVOR);
        return Themes.isFlavor(id) ? id : PrefTheme.DEFAULT_FLAVOR;
    }

    public void setFlavor(String flavorId) {
        prefs.edit().putString(KEY_FLAVOR, flavorId).commit();
    }

    public String getAccent() {
        String id = prefs.getString(KEY_ACCENT, PrefTheme.DEFAULT_ACCENT);
        return Themes.isAccent(id) || PrefTheme.ACCENT_RANDOM.equals(id) ? id : PrefTheme.DEFAULT_ACCENT;
    }

    public void setAccent(String accentId) {
        prefs.edit().putString(KEY_ACCENT, accentId).commit();
    }

    public String getRolledAccent() {
        String id = prefs.getString(KEY_ACCENT_ROLLED, Themes.DEFAULT_ACCENT_COLOR);
        return Themes.isAccent(id) ? id : Themes.DEFAULT_ACCENT_COLOR;
    }

    public void setRolledAccent(String accentId) {
        prefs.edit().putString(KEY_ACCENT_ROLLED, accentId).commit();
    }

    /** Page ids (see WdNavbar) in tab order. Always a permutation of 0..3, whatever was stored. */
    public int[] getTabOrder() {
        return parseTabOrder(prefs.getString(KEY_TAB_ORDER, DEFAULT_TAB_ORDER));
    }

    public void setTabOrder(int[] order) {
        prefs.edit().putString(KEY_TAB_ORDER, joinTabOrder(order)).commit();
    }

    private static String joinTabOrder(int[] order) {
        StringBuilder sb = new StringBuilder();
        for (int id : order) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private static int[] parseTabOrder(String raw) {
        int count = DEFAULT_TAB_ORDER.split(",").length;
        int[] order = new int[count];
        boolean[] seen = new boolean[count];
        String[] parts = raw == null ? new String[0] : raw.split(",");
        int n = 0;
        for (String part : parts) {
            try {
                int id = Integer.parseInt(part.trim());
                if (id >= 0 && id < count && !seen[id] && n < count) {
                    seen[id] = true;
                    order[n++] = id;
                }
            } catch (NumberFormatException ignored) {
                // malformed entry
            }
        }
        // Anything missing keeps its default relative position at the end.
        for (String part : DEFAULT_TAB_ORDER.split(",")) {
            int id = Integer.parseInt(part);
            if (!seen[id]) order[n++] = id;
        }
        return order;
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
            json.put(KEY_FLAVOR, getFlavor());
            json.put(KEY_ACCENT, getAccent());
            json.put(KEY_ZOOM_PERCENT, getZoomPercent());
            json.put(KEY_TAB_ORDER, joinTabOrder(getTabOrder()));
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
        String flavor = json.optString(KEY_FLAVOR, null);
        String accent = json.optString(KEY_ACCENT, null);
        if (flavor == null) {
            // Backups from older builds carry a single theme id.
            String[] legacy = parseLegacyTheme(json.optString(KEY_LEGACY_THEME, null));
            if (legacy != null) {
                flavor = legacy[0];
                accent = legacy[1];
            }
        }
        if (flavor != null && Themes.isFlavor(flavor)) editor.putString(KEY_FLAVOR, flavor);
        if (accent != null && (Themes.isAccent(accent) || PrefTheme.ACCENT_RANDOM.equals(accent))) {
            editor.putString(KEY_ACCENT, accent);
        }
        int zoom = json.optInt(KEY_ZOOM_PERCENT, 0);
        if (zoom > 0) editor.putInt(KEY_ZOOM_PERCENT, ZoomManager.clamp(zoom));
        if (json.has(KEY_TAB_ORDER)) editor.putString(KEY_TAB_ORDER, joinTabOrder(parseTabOrder(json.optString(KEY_TAB_ORDER))));
        if (json.has(KEY_APP_TIMER)) editor.putString(KEY_APP_TIMER, Hms.normalize(json.optString(KEY_APP_TIMER)));
        if (json.has(KEY_ROULETTE_TIMER)) editor.putString(KEY_ROULETTE_TIMER, Hms.normalize(json.optString(KEY_ROULETTE_TIMER)));
        for (String key : BACKUP_BOOLEANS) {
            if (json.has(key)) editor.putBoolean(key, json.optBoolean(key));
        }
        editor.remove(KEY_ROULETTE_LIST).remove(KEY_COLLAPSED);
        editor.commit();
    }
}
