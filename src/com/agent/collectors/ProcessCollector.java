package com.agent.collectors;

import com.agent.metrics.ProcessInfo;
import java.io.*;
import java.util.*;

public class ProcessCollector {

    private static final int HZ = 100;

    // pid -> [utime, stime, wallMs]
    private static final Map<Integer, long[]> prevSamples = new HashMap<>();

    public static List<ProcessInfo> getTopProcesses(int topN) {
        File procDir = new File("/proc");
        File[] entries = procDir.listFiles(f -> {
            String n = f.getName();
            // Only numeric directories (pid directories)
            for (char c : n.toCharArray()) if (!Character.isDigit(c)) return false;
            return f.isDirectory();
        });

        if (entries == null) return Collections.emptyList();

        long nowMs = System.currentTimeMillis();
        List<ProcessInfo> result = new ArrayList<>();

        for (File pidDir : entries) {
            try {
                int pid = Integer.parseInt(pidDir.getName());
                ProcessInfo info = readProcess(pid, pidDir, nowMs);
                if (info != null) result.add(info);
            } catch (NumberFormatException ignored) {}
        }

        // Sort by CPU% descending, then by RSS descending as tiebreak
        result.sort((a, b) -> {
            int c = Double.compare(b.cpuPercent, a.cpuPercent);
            return c != 0 ? c : Long.compare(b.rssKB, a.rssKB);
        });

        return result.size() > topN ? result.subList(0, topN) : result;
    }

    private static ProcessInfo readProcess(int pid, File pidDir, long nowMs) {
        try {
            // Read /proc/[pid]/stat
            String statLine = readFirstLine(new File(pidDir, "stat"));
            if (statLine == null) return null;

            // The comm field (2nd token) is wrapped in () and may contain spaces.
            // Safe parsing: find last ')' to split the line.
            int commEnd = statLine.lastIndexOf(')');
            if (commEnd < 0) return null;

            int commStart = statLine.indexOf('(');
            String comm  = statLine.substring(commStart + 1, commEnd);
            String[] rest = statLine.substring(commEnd + 2).trim().split("\\s+");
            // rest[0] = state, rest[11] = utime, rest[12] = stime, rest[21] = rss (pages)
            if (rest.length < 22) return null;

            char state  = rest[0].charAt(0);
            long utime  = Long.parseLong(rest[11]);
            long stime  = Long.parseLong(rest[12]);
            long rssPages = Long.parseLong(rest[21]);

            // Read /proc/[pid]/status for VmRSS (more reliable than pages * pageSize)
            long rssKB = rssPages * 4; // fallback: assume 4KB pages
            File statusFile = new File(pidDir, "status");
            if (statusFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(statusFile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("VmRSS:")) {
                            rssKB = Long.parseLong(line.replaceAll("\\D+", ""));
                            break;
                        }
                    }
                }
            }

            // Delta CPU calculation
            long totalJiffies = utime + stime;
            double cpuPercent = 0.0;

            long[] prev = prevSamples.get(pid);
            if (prev != null) {
                long jiffiesDiff = totalJiffies - prev[0];
                long msDiff      = nowMs - prev[1];
                if (msDiff > 0) {
                    // Convert elapsed ms to jiffies: ms * HZ / 1000
                    double elapsedJiffies = msDiff * HZ / 1000.0;
                    cpuPercent = (jiffiesDiff / elapsedJiffies) * 100.0;
                }
            }

            prevSamples.put(pid, new long[]{totalJiffies, nowMs});

            return new ProcessInfo(pid, comm, state, cpuPercent, rssKB);

        } catch (Exception e) {
            // Normal: processes exit mid-scan
            return null;
        }
    }

    private static String readFirstLine(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /** Clean up state for PIDs that no longer exist (prevent memory leak). */
    public static synchronized void evictDeadProcesses(Set<Integer> livePids) {
        prevSamples.keySet().retainAll(livePids);
    }
}
