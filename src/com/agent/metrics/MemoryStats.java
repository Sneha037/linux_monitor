package com.agent.metrics;
public class MemoryStats {

    public final long totalKB, usedKB;
    public final double usedPercent;

    public MemoryStats(long totalKB, long usedKB, double usedPercent) {

        this.totalKB = totalKB;
        this.usedKB = usedKB;
        this.usedPercent = usedPercent;
    }
}
