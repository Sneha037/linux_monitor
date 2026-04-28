package com.agent.metrics;
public class LoadAvgStats {
    public final double oneMin, fiveMin, fifteenMin;
    public LoadAvgStats(double oneMin, double fiveMin, double fifteenMin) {
        this.oneMin = oneMin;
        this.fiveMin = fiveMin;
        this.fifteenMin = fifteenMin;
    }
}
