package com.agent.collectors;

import com.agent.metrics.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LatencyCollector {

    private static final String[] TARGETS = {
            "8.8.8.8",        // Google DNS
            "1.1.1.1",        // Cloudflare
            "208.67.222.222", // OpenDNS
            "9.9.9.9",        // Quad9
            "8.8.4.4"         // Google DNS secondary
    };

    // Store last 60 samples per target
    private static final Map<String, ArrayDeque<Double>> history = new LinkedHashMap<>();
    static {
        for (String t : TARGETS) history.put(t, new ArrayDeque<>(60));
    }

    public static Map<String, LatencyStats> getLatencyStats() {
        Map<String, LatencyStats> results = new LinkedHashMap<>();

        for (String target : TARGETS) {
            double rtt = ping(target);

            ArrayDeque<Double> samples = history.get(target);

            if(samples.size() >= 60)
            {
                samples.pollFirst();
            }
            samples.addFirst(rtt);

            if(rtt < 0)
            {
                results.put(target, new LatencyStats(rtt, -1, -1, -1, -1));
            }

            Iterator i = new ArrayDeque<>(samples).iterator();
            double min = Double.MAX_VALUE;
            double max = 0;
            double sum = 0;
            while(i.hasNext())
            {
                double a = Double.parseDouble(i.next().toString());
                if(a < min)
                {
                    min = a;
                }

                if(a > max)
                {
                    max = a;
                }

                sum+= a;
            }
            double avg    = sum/samples.size();

            i = new ArrayDeque<>(samples).iterator();
            double jitter = 0;
            double totalVariance = 0;
            while(i.hasNext())
            {
                Double a = Double.parseDouble(i.next().toString());
                double dev = Math.abs(a - avg);
                double variance = dev * dev;
                totalVariance+= variance;
            }

            jitter = Math.sqrt(totalVariance/ samples.size());

            results.put(target, new LatencyStats(rtt, min, avg, max, jitter));
        }

        return results;
    }

    private static double ping(String host) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ping", "-c", "4", "-W", "2", host);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output = new String(
                    p.getInputStream().readAllBytes());
            p.waitFor(15, TimeUnit.SECONDS);

            for (String line : output.split("\n")) {
                if (line.contains("rtt min")) {
                    String val = line.split("=")[1];

                    String[] parts = val.split("/");
                    return Double.parseDouble(parts[1]);
                }
            }

        } catch (Exception e) {
            Logger.error("LatencyCollector", e);
        }
        return -1.0;
    }
}