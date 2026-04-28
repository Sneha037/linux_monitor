package com.agent.metrics;

public class CpuStats {
    public final double usagePercent;  // aggregate 0-100
    public final double[] corePercents; // per-core 0-100, index = core number

    public CpuStats(double usagePercent, double[] corePercents) {
        this.usagePercent  = usagePercent;
        this.corePercents  = corePercents;
    }
}
