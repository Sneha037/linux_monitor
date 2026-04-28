package com.agent.metrics;

public class ProcessInfo {
    public final int    pid;
    public final String name;
    public final char   state;      // R, S, D, Z, T
    public final double cpuPercent;
    public final long   rssKB;

    public ProcessInfo(int pid, String name, char state, double cpuPercent, long rssKB) {
        this.pid        = pid;
        this.name       = name;
        this.state      = state;
        this.cpuPercent = cpuPercent;
        this.rssKB      = rssKB;
    }

    /** Human-readable state description. Useful for dashboards. */
    public String stateLabel() {
        return switch (state) {
            case 'R' -> "running";
            case 'S' -> "sleeping";
            case 'D' -> "disk-wait";
            case 'Z' -> "zombie";
            case 'T' -> "stopped";
            default  -> String.valueOf(state);
        };
    }
}
