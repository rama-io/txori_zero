package com.rama.txori_zero.objects;

/**
 * One row of the home list: either a session header (task == null)
 * or a task row that belongs to a session.
 */
public final class SessionItem {
    public final long sessionId;
    public final Task task;
    public String name;

    private SessionItem(long sessionId, String name, Task task) {
        this.sessionId = sessionId;
        this.name = name;
        this.task = task;
    }

    public static SessionItem header(long sessionId, String name) {
        return new SessionItem(sessionId, name, null);
    }

    public static SessionItem row(long sessionId, Task task) {
        return new SessionItem(sessionId, null, task);
    }

    public boolean isHeader() {
        return task == null;
    }

    public boolean isRow() {
        return task != null;
    }
}
