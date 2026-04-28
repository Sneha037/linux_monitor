package com.agent.collectors;

import com.agent.metrics.CpuStats;
import java.io.*;
import java.util.*;


public class CpuCollector {

    // State for aggregate CPU line
    private static long prevIdle  = 0;
    private static long prevTotal = 0;

    // State for per-core lines: index = core number
    private static long[] prevCoreIdle  = new long[0];
    private static long[] prevCoreTotal = new long[0];

    public static CpuStats getCpuStats() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {

            List<String> coreLines = new ArrayList<>();
            String aggregateLine = null;
            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("cpu "))  { aggregateLine = line; }
                else if (line.startsWith("cpu")) { coreLines.add(line); }
                else if (!coreLines.isEmpty())   { break; } // past cpu lines
            }

            if (aggregateLine == null) return new CpuStats(-1, new double[0]);

            // --- aggregate ---
            long[] agg  = parseJiffies(aggregateLine);
            long idle    = agg[0];
            long total   = agg[1];

            long totalDiff = total - prevTotal;
            long idleDiff  = idle  - prevIdle;   // FIX: was (total - prevIdle)
            prevTotal = total;
            prevIdle  = idle;

            double overallPercent = totalDiff > 0
                ? (1.0 - (double) idleDiff / totalDiff) * 100.0
                : 0.0;

            // --- per-core ---
            int cores = coreLines.size();
            if (prevCoreIdle.length != cores) {
                prevCoreIdle  = new long[cores];
                prevCoreTotal = new long[cores];
            }

            double[] corePercents = new double[cores];
            for (int i = 0; i < cores; i++) {
                long[] c     = parseJiffies(coreLines.get(i));
                long cIdle   = c[0];
                long cTotal  = c[1];

                long cTotalDiff = cTotal - prevCoreTotal[i];
                long cIdleDiff  = cIdle  - prevCoreIdle[i];
                prevCoreTotal[i] = cTotal;
                prevCoreIdle[i]  = cIdle;

                corePercents[i] = cTotalDiff > 0
                    ? (1.0 - (double) cIdleDiff / cTotalDiff) * 100.0
                    : 0.0;
            }

            return new CpuStats(clamp(overallPercent), clamp(corePercents));

        } catch (Exception e) {
            Logger.error("CpuCollector", e);
            return new CpuStats(-1, new double[0]);
        }
    }

    /**
     * Parse a "cpu..." line from /proc/stat.
     * Returns [idleAll, total] where idleAll = idle + iowait.
     */
    private static long[] parseJiffies(String line) {
        String[] t = line.trim().split("\\s+");
        // tokens: label user nice system idle iowait irq softirq steal...
        long user    = Long.parseLong(t[1]);
        long nice    = Long.parseLong(t[2]);
        long system  = Long.parseLong(t[3]);
        long idle    = Long.parseLong(t[4]);
        long iowait  = t.length > 5 ? Long.parseLong(t[5]) : 0;
        long irq     = t.length > 6 ? Long.parseLong(t[6]) : 0;
        long softirq = t.length > 7 ? Long.parseLong(t[7]) : 0;
        long steal   = t.length > 8 ? Long.parseLong(t[8]) : 0;

        long idleAll = idle + iowait;
        long total   = user + nice + system + idleAll + irq + softirq + steal;
        return new long[]{idleAll, total};
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private static double[] clamp(double[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = clamp(arr[i]);
        return arr;
    }
}
