package com.rama.txori_zero.managers;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.rama.txori_zero.R;
import com.rama.txori_zero.helpers.Hms;
import com.rama.txori_zero.objects.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Exports and restores lists and settings as one JSON file (schema 1, same shape as Txori).
 * Sessions and steps travel by value, never by primary key, and are recreated through the
 * same helpers the UI uses. The file is validated before anything is deleted and the restore
 * runs in a single transaction, so a bad file can never half-wipe the database.
 * The file picker (Storage Access Framework) needs API 19, the caller hides the buttons below that.
 */
public final class BackupManager {
    private static final String APP_ID = "txori_zero";
    private static final String LEGACY_APP_ID = "txori";
    private static final int SCHEMA = 1;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private BackupManager() {
    }

    /** Failure with a message that is safe to show in a toast. */
    public static final class BackupException extends Exception {
        public BackupException(String message) {
            super(message);
        }
    }

    public static final class Summary {
        public final int sessions;
        public final int steps;

        Summary(int sessions, int steps) {
            this.sessions = sessions;
            this.steps = steps;
        }
    }

    public static Summary export(Context context, Uri uri) throws BackupException {
        DatabaseHelper helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        int steps = 0;
        JSONObject json = new JSONObject();
        try {
            JSONArray sessions = new JSONArray();
            List<DatabaseHelper.Session> list = helper.getSessions(db);
            for (int i = 0; i < list.size(); i++) {
                JSONArray stepsJson = new JSONArray();
                List<Task> tasks = helper.getSessionTasks(db, list.get(i).id);
                for (int j = 0; j < tasks.size(); j++) {
                    Task task = tasks.get(j);
                    stepsJson.put(new JSONObject().put("label", task.label).put("duration", task.duration).put("restDuration", task.restDuration).put("order", j + 1));
                    steps++;
                }
                sessions.put(new JSONObject().put("name", list.get(i).name).put("order", i + 1).put("steps", stepsJson));
            }
            json.put("app", APP_ID).put("schema", SCHEMA).put("exportedAt", now()).put("sessions", sessions);
            json.put("settings", PrefsManager.getInstance(context).exportSettings());
            writeText(context, uri, json.toString(2));
            return new Summary(list.size(), steps);
        } catch (JSONException e) {
            throw new BackupException(context.getString(R.string.toast_backup_failed));
        } finally {
            db.close();
            helper.close();
        }
    }

    public static Summary restore(Context context, Uri uri) throws BackupException {
        String invalid = context.getString(R.string.toast_backup_invalid);
        JSONObject json;
        try {
            json = new JSONObject(readText(context, uri));
        } catch (JSONException e) {
            throw new BackupException(invalid);
        }
        String app = json.optString("app");
        JSONArray sessions = json.optJSONArray("sessions");
        if ((!APP_ID.equals(app) && !LEGACY_APP_ID.equals(app)) || json.optInt("schema", -1) != SCHEMA || sessions == null) {
            throw new BackupException(invalid);
        }
        Summary summary;
        try {
            summary = rebuild(context, sessions);
        } catch (JSONException e) {
            throw new BackupException(invalid);
        }
        JSONObject settings = json.optJSONObject("settings");
        if (settings != null) {
            PrefsManager.getInstance(context).importSettings(settings);
        }
        return summary;
    }

    private static Summary rebuild(Context context, JSONArray sessions) throws JSONException {
        DatabaseHelper helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        int sessionCount = 0;
        int stepCount = 0;
        db.beginTransaction();
        try {
            helper.clearAllUserData(db);
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.getJSONObject(i);
                String name = FontManager.sanitizeForFont(session.optString("name", "").trim());
                if (name.length() == 0) name = "Imported";
                long sessionId = helper.createSession(db, name);
                sessionCount++;
                JSONArray steps = session.optJSONArray("steps");
                if (steps == null) continue;
                for (int j = 0; j < steps.length(); j++) {
                    JSONObject step = steps.getJSONObject(j);
                    String label = FontManager.sanitizeForFont(step.optString("label", "").trim());
                    if (label.length() == 0) continue;
                    helper.addTaskToSession(db, sessionId, label, Hms.normalize(step.optString("duration", "")), Hms.normalize(step.optString("restDuration", "")));
                    stepCount++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
            helper.close();
        }
        return new Summary(sessionCount, stepCount);
    }

    private static void writeText(Context context, Uri uri, String text) throws BackupException {
        OutputStream out = null;
        try {
            out = context.getContentResolver().openOutputStream(uri);
            if (out == null) throw new BackupException(context.getString(R.string.toast_backup_failed));
            out.write(text.getBytes(UTF8));
        } catch (IOException e) {
            throw new BackupException(context.getString(R.string.toast_backup_failed));
        } finally {
            close(out);
        }
    }

    private static String readText(Context context, Uri uri) throws BackupException {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) throw new BackupException(context.getString(R.string.toast_backup_failed));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return new String(buffer.toByteArray(), UTF8);
        } catch (IOException e) {
            throw new BackupException(context.getString(R.string.toast_backup_failed));
        } finally {
            close(in);
        }
    }

    private static void close(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }

    private static String now() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
