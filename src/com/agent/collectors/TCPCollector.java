package com.agent.collectors;

import com.agent.metrics.TCPConnectionStats;

import java.io.*;
import java.util.*;

public class TCPCollector {

    // State codes from /proc/net/tcp
    private static final Map<String, String> STATES = new HashMap<>();
    static {
        STATES.put("01", "ESTABLISHED");
        STATES.put("02", "SYN_SENT");
        STATES.put("03", "SYN_RECV");
        STATES.put("04", "FIN_WAIT1");
        STATES.put("05", "FIN_WAIT2");
        STATES.put("06", "TIME_WAIT");
        STATES.put("07", "CLOSE");
        STATES.put("08", "CLOSE_WAIT");
        STATES.put("09", "LAST_ACK");
        STATES.put("0A", "LISTEN");
        STATES.put("0B", "CLOSING");
    }

    public static TCPConnectionStats getTCPStats() {
        Map<String, Integer> stateCounts = new HashMap<>();
        List<String> established = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new FileReader("/proc/net/tcp"))) {

            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // skip header

                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;

                String[] local  = parts[1].split(":");
                String[] remote = parts[2].split(":");
                String stateHex = parts[3].toUpperCase();

                String localIp   = hexToIp(local[0]);
                int    localPort = hexToPort(local[1]);
                String remoteIp  = hexToIp(remote[0]);
                int    remotePort= hexToPort(remote[1]);

                String state = STATES.getOrDefault(stateHex, "UNKNOWN");

                stateCounts.put(state, stateCounts.getOrDefault(state, 0) + 1);

                if(state.equals("ESTABLISHED"))
                   established.add(remoteIp + ":" + remotePort);
            }

        } catch (Exception e) {
            Logger.error("TCPCollector", e);
        }

        return new TCPConnectionStats(stateCounts, established);
    }

    private static String hexToIp(String hex) {

        // /proc/net/dev stores IPV4 bytes little-endian
        long l = Long.parseLong(hex, 16);
        StringBuilder sb = new StringBuilder();
        int c = 4;
        while (c > 0) {
            long p = l & 0xff;
            if(c == 1)
             sb.append(p);
            else
            {
                sb.append(p).append(".");
            }
            l = l >> 8;
            c--;
        }

        return sb.toString();
    }

    private static int hexToPort(String hex) {
        return Integer.parseInt(hex, 16);
    }
}