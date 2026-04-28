package com.agent.collectors;

import com.agent.metrics.LoadAvgStats;
import java.io.*;

public class LoadAvgCollector {
    public static LoadAvgStats getLoadAverages() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String[] parts = br.readLine().split("\\s+");
            return new LoadAvgStats(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]));
        } catch (Exception e) {
            Logger.error("LoadAvgCollector", e);
            return new LoadAvgStats(-1, -1, -1);
        }
    }
}
