package com.agent.metrics;

public class LatencyStats
{
    public double rtt;
    public double min;
    public double avg;
    public double max;
    public double jitter;

    public LatencyStats(double rtt, double min, double avg, double max, double jitter)
    {
        this.rtt = rtt;
        this.min = min;
        this.avg = avg;
        this.max = max;
        this.jitter = jitter;
    }
}
