package com.rama.txori_zero.managers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rama.txori_zero.objects.Task;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String NAME = "txori_zero.db";
    private static final int VERSION = 1;

    /** A session id with its display name. */
    public static final class Session {
        public final long id;
        public final String name;

        Session(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT, duration TEXT, rest_duration TEXT DEFAULT '000000')");
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, session_order INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE session_steps (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, task_id INTEGER, step_order INTEGER)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema, nothing to migrate yet.
    }

    // Queries

    public List<Task> getAllTasks(SQLiteDatabase db) {
        List<Task> tasks = new ArrayList<Task>();
        Cursor c = db.rawQuery("SELECT id, label, duration, rest_duration FROM tasks ORDER BY label", null);
        try {
            while (c.moveToNext()) {
                tasks.add(new Task(c.getLong(0), 0, c.getString(1), orZero(c.getString(2)), orZero(c.getString(3))));
            }
        } finally {
            c.close();
        }
        return tasks;
    }

    /** Tasks of one session in order. stepId is the session_steps row so duplicates can be told apart. */
    public List<Task> getSessionTasks(SQLiteDatabase db, long sessionId) {
        List<Task> tasks = new ArrayList<Task>();
        Cursor c = db.rawQuery("SELECT ss.id, t.id, t.label, t.duration, t.rest_duration FROM session_steps ss JOIN tasks t ON ss.task_id = t.id WHERE ss.session_id = ? ORDER BY ss.step_order", new String[]{String.valueOf(sessionId)});
        try {
            while (c.moveToNext()) {
                tasks.add(new Task(c.getLong(1), c.getLong(0), c.getString(2), orZero(c.getString(3)), orZero(c.getString(4))));
            }
        } finally {
            c.close();
        }
        return tasks;
    }

    public List<Session> getSessions(SQLiteDatabase db) {
        List<Session> result = new ArrayList<Session>();
        Cursor c = db.rawQuery("SELECT id, name FROM sessions ORDER BY session_order, id", null);
        try {
            while (c.moveToNext()) {
                result.add(new Session(c.getLong(0), c.getString(1)));
            }
        } finally {
            c.close();
        }
        return result;
    }

    // Writes

    public long createSession(SQLiteDatabase db, String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("session_order", maxOf(db, "SELECT COALESCE(MAX(session_order), 0) FROM sessions", null) + 1);
        return db.insert("sessions", null, values);
    }

    public void renameSession(SQLiteDatabase db, long sessionId, String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        db.update("sessions", values, "id = ?", new String[]{String.valueOf(sessionId)});
    }

    public void deleteSession(SQLiteDatabase db, long sessionId) {
        String[] args = {String.valueOf(sessionId)};
        db.delete("session_steps", "session_id = ?", args);
        db.delete("sessions", "id = ?", args);
    }

    /** Wipes every session, step and task. Used by backup restore before re-seeding. */
    public void clearAllUserData(SQLiteDatabase db) {
        db.delete("session_steps", null, null);
        db.delete("sessions", null, null);
        db.delete("tasks", null, null);
    }

    public long addTaskToSession(SQLiteDatabase db, long sessionId, String label, String duration, String restDuration) {
        long taskId = getOrCreateTaskId(db, label, duration, restDuration);
        insertStep(db, sessionId, taskId, maxOf(db, "SELECT COALESCE(MAX(step_order), 0) FROM session_steps WHERE session_id = ?", new String[]{String.valueOf(sessionId)}) + 1);
        return taskId;
    }

    public void updateTask(SQLiteDatabase db, long taskId, String label, String duration, String restDuration) {
        ContentValues values = new ContentValues();
        values.put("label", label);
        values.put("duration", duration);
        values.put("rest_duration", restDuration);
        db.update("tasks", values, "id = ?", new String[]{String.valueOf(taskId)});
    }

    /** Moves a step to the end of another session. */
    public void moveStep(SQLiteDatabase db, long stepId, long targetSessionId) {
        ContentValues values = new ContentValues();
        values.put("session_id", targetSessionId);
        values.put("step_order", maxOf(db, "SELECT COALESCE(MAX(step_order), 0) FROM session_steps WHERE session_id = ?", new String[]{String.valueOf(targetSessionId)}) + 1);
        db.update("session_steps", values, "id = ?", new String[]{String.valueOf(stepId)});
    }

    /** Removes one step by its own key, so identical tasks in other sessions stay. */
    public void removeStep(SQLiteDatabase db, long stepId) {
        db.delete("session_steps", "id = ?", new String[]{String.valueOf(stepId)});
    }

    public void swapStepOrder(SQLiteDatabase db, long stepA, long stepB) {
        swapOrder(db, "session_steps", "step_order", stepA, stepB);
    }

    public void swapSessionOrder(SQLiteDatabase db, long sessionA, long sessionB) {
        swapOrder(db, "sessions", "session_order", sessionA, sessionB);
    }

    // Helpers

    private void swapOrder(SQLiteDatabase db, String table, String column, long idA, long idB) {
        int orderA = maxOf(db, "SELECT " + column + " FROM " + table + " WHERE id = ?", new String[]{String.valueOf(idA)});
        int orderB = maxOf(db, "SELECT " + column + " FROM " + table + " WHERE id = ?", new String[]{String.valueOf(idB)});
        db.execSQL("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", new Object[]{orderB, idA});
        db.execSQL("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", new Object[]{orderA, idB});
    }

    private int maxOf(SQLiteDatabase db, String sql, String[] args) {
        Cursor c = db.rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private String orZero(String value) {
        return value == null ? "000000" : value;
    }

    private long getOrCreateTaskId(SQLiteDatabase db, String label, String duration, String restDuration) {
        Cursor c = db.rawQuery("SELECT id FROM tasks WHERE label = ? AND duration = ? AND rest_duration = ?", new String[]{label, duration, restDuration});
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally {
            c.close();
        }
        ContentValues values = new ContentValues();
        values.put("label", label);
        values.put("duration", duration);
        values.put("rest_duration", restDuration);
        return db.insert("tasks", null, values);
    }

    private void insertStep(SQLiteDatabase db, long sessionId, long taskId, int order) {
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("task_id", taskId);
        values.put("step_order", order);
        db.insert("session_steps", null, values);
    }

    // Starter data

    private void seed(SQLiteDatabase db) {
        long morning = createSession(db, "Morning Reset");
        add(db, morning, "Drink Water", "000500", "000000");
        add(db, morning, "Brush Teeth", "000500", "000000");
        add(db, morning, "Shower", "001000", "000000");
        add(db, morning, "Tidy Room", "000500", "000000");
        add(db, morning, "Wash Dishes", "000500", "000000");

        long workout = createSession(db, "Workout");
        add(db, workout, "Getting Ready", "000015", "000000");
        add(db, workout, "Chest Opener", "000130", "000100");
        add(db, workout, "Dead Hang", "000040", "000100");
        for (int i = 0; i < 2; i++) {
            add(db, workout, "Pull-Up x12", "000030", "000130");
            add(db, workout, "Push-Up x40", "000100", "000100");
        }
        for (int i = 0; i < 2; i++) add(db, workout, "Chin-Up x12", "000030", "000130");
        for (int i = 0; i < 2; i++) add(db, workout, "Hip Thrust x30", "000100", "000100");
        add(db, workout, "Wall Sit", "000100", "000100");
        for (int i = 0; i < 2; i++) add(db, workout, "Crunches x12", "000100", "000100");
        add(db, workout, "Plank", "000100", "000100");
        add(db, workout, "Dead Hang", "000100", "000015");
        add(db, workout, "Deep Squat", "000100", "000015");
        add(db, workout, "Split Stretch", "000100", "000000");

        long roulette = createSession(db, "Roulette");
        String[] breaks = {"Drink Water", "Stand Up", "Walk Around", "Change Sitting Position", "Roll Shoulders", "Neck Stretch", "Wrist Stretch", "Goblin Posture Detected", "Look Away From Screen", "Touch Grass"};
        for (String label : breaks) add(db, roulette, label, "000100", "000000");
    }

    private void add(SQLiteDatabase db, long sessionId, String label, String duration, String rest) {
        addTaskToSession(db, sessionId, label, duration, rest);
    }
}
