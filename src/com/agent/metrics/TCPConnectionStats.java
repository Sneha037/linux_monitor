package com.agent.metrics;

import java.util.*;

public class TCPConnectionStats
{
    public final Map<String, Integer> stateCounts = new LinkedHashMap<>();
    public final List<String> established = new ArrayList<>();

    public TCPConnectionStats(Map<String, Integer> stateCounts, List<String> established)
    {
        this.stateCounts.putAll(stateCounts);
        this.established.addAll(established);
    }
}
