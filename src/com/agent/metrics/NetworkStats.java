package com.agent.metrics;
public class NetworkStats {

    public final long rxBytes, txBytes;

    public NetworkStats(long rxBytes, long txBytes) {

        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }
}
