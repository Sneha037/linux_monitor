package com.agent.collectors;

import com.agent.metrics.MemoryStats;
import java.io.*;

public class MemoryCollector
{
    public static MemoryStats getMemoryStats()
    {
        long total = 0, available = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:"))
                    total     = Long.parseLong(line.replaceAll("\\D+", ""));
                if (line.startsWith("MemAvailable:"))
                    available = Long.parseLong(line.replaceAll("\\D+", ""));
            }
            long used = total - available;
            return new MemoryStats(total, used, (used * 100.0) / total);
        } catch (Exception e)
        {
            Logger.error("MemoryCollector", e);
            return new MemoryStats(0, 0, -1);
        }
    }
}
