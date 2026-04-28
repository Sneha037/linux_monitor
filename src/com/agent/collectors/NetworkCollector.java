package com.agent.collectors;

import com.agent.metrics.NetworkStats;
import java.io.*;

public class NetworkCollector
{
    public static NetworkStats getNetworkStats()
    {
        long rx = 0, tx = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/dev")))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                if (!line.contains(":"))
                    continue;
                String[] parts = line.split(":");
                String iface = parts[0].trim();
                if (iface.equals("lo"))
                    continue;
                String[] data = parts[1].trim().split("\\s+");
                rx += Long.parseLong(data[0]);
                tx += Long.parseLong(data[8]);
            }

            return new NetworkStats(rx, tx);
        }
        catch (Exception e)
        {
            Logger.error("NetworkCollector", e);
            return new NetworkStats(-1, -1);
        }
    }
}
