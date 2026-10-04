package com.resplan.scheduling;

/** Задача с ранними (es, ef) и поздними (ls, lf) сроками в рабочих днях от начала проекта */
public record ScheduledTask(TaskNode task, int es, int ef, int ls, int lf) {

    public int slack() {
        return ls - es;
    }

    public boolean critical() {
        return slack() == 0;
    }
}
