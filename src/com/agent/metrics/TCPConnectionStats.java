package com.agent.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TCPConnectionStats
{
    private final Map<String, Integer> stateCounts = new HashMap<>();
    private final List<String> established = new ArrayList<>();

    public TCPConnectionStats(Map<String, Integer> stateCounts, List<String> established)
    {
        this.stateCounts.putAll(stateCounts);
        this.established.addAll(established);
    }
}
