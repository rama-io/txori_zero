package com.rama.txori_zero.objects;

public final class Task {
    public final long id;
    public final long stepId;
    public String label;
    public String duration;
    public String restDuration;

    public Task(long id, long stepId, String label, String duration, String restDuration) {
        this.id = id;
        this.stepId = stepId;
        this.label = label;
        this.duration = duration;
        this.restDuration = restDuration;
    }
}
