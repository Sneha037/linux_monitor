package com.agent.metrics;

import java.util.Map;

public class DiskStats {
    public final long readKB;
    public final long writeKB;
    /** device name -> [readKB, writeKB, readMs, writeMs] */
    public final Map<String, long[]> perDevice;

    public DiskStats(long readKB, long writeKB, Map<String, long[]> perDevice) {
        this.readKB    = readKB;
        this.writeKB   = writeKB;
        this.perDevice = perDevice;
    }
}
