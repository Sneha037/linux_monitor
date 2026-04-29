package com.agent.export;

import com.agent.metrics.*;
import java.util.List;
import java.util.Map;


public class PrometheusExporter {

    public static String export(
        CpuStats cpu,
        MemoryStats mem,
        LoadAvgStats load,
        DiskStats disk,
        NetworkStats net,
        TCPConnectionStats tcp,
        Map<String, LatencyStats> latencyStatsMap,
        List<ProcessInfo> topProcs,
        double diskReadKBps,
        double diskWriteKBps,
        double netRxKBps,
        double netTxKBps,
        long timestampMs
    ) {
        StringBuilder sb = new StringBuilder(2048);

        // --- CPU ---
        gauge(sb, "node_cpu_usage_percent",
              "Overall CPU usage as a percentage 0-100",
              cpu.usagePercent, null, null, timestampMs);

        for (int i = 0; i < cpu.corePercents.length; i++) {
            gauge(sb, "node_cpu_core_usage_percent",
                  "Per-core CPU usage percent",
                  cpu.corePercents[i],
                  new String[]{"core"}, new String[]{String.valueOf(i)},
                  timestampMs);
        }

        // --- Memory ---
        gauge(sb, "node_memory_total_kb",
              "Total physical memory in KB", mem.totalKB, null, null, timestampMs);
        gauge(sb, "node_memory_used_kb",
              "Used physical memory in KB", mem.usedKB, null, null, timestampMs);
        gauge(sb, "node_memory_usage_percent",
              "Memory usage as a percentage 0-100",
              mem.usedPercent, null, null, timestampMs);

        // --- Load average ---
        gauge(sb, "node_load_average",
              "System load average",
              load.oneMin,
              new String[]{"interval"}, new String[]{"1m"}, timestampMs);
        gauge(sb, "node_load_average",
              null, // HELP/TYPE already written
              load.fiveMin,
              new String[]{"interval"}, new String[]{"5m"}, timestampMs);
        gauge(sb, "node_load_average",
              null,
              load.fifteenMin,
              new String[]{"interval"}, new String[]{"15m"}, timestampMs);

        // -- TCP connection state counts --

        tcp.stateCounts.forEach( (state, val) -> {
            gauge(sb, "tcp_connection_state_counts", "TCP Connection State Counts",
                    Double.valueOf(val), new String[]{"state"}, new String[]{state}, timestampMs
                    );
        });

        /*
        Commenting as gauges are better used for numeric values

        for(String established : tcp.established)
        {
            gauge(sb, "established_connection_address", "Established Connection Remote IP and Port",
                    0.0, new String[]{"remote_ip_port"}, new String[]{established}, timestampMs);
        }
        */

        // --- Latency stats ---

        /*
        if(latencyStatsMap != null)
        {
            sb.append("# HELP latency_stats_rtt_min_avg_max_jitter_per_target\n");
            sb.append("# TYPE latency_stats_rtt_min_avg_max_jitter_per_target gauge\n");
            for(String target : latencyStatsMap.keySet())
            {
                LatencyStats stats = latencyStatsMap.get(target);
                sb.append(String.format("latency_stats{target=\"%s\", rtt=\"%.2f\", min=\"%.2f\", avg=\"%.2f\", max=\"%.2f\", jitter=\"%.2f %n",
                        target, stats.rtt, stats.min, stats.avg, stats.max, stats.jitter));
            }
        }
        */

        sb.append("# HELP latency_rtt_ms Current RTT in ms\n");
        sb.append("# TYPE latency_rtt_ms gauge\n");
        sb.append("# HELP latency_min_ms Min RTT over last 60 samples\n");
        sb.append("# TYPE latency_min_ms gauge\n");
        sb.append("# HELP latency_avg_ms Avg RTT over last 60 samples\n");
        sb.append("# TYPE latency_avg_ms gauge\n");
        sb.append("# HELP latency_max_ms Max RTT over last 60 samples\n");
        sb.append("# TYPE latency_max_ms gauge\n");
        sb.append("# HELP latency_jitter_ms Jitter over last 60 samples\n");
        sb.append("# TYPE latency_jitter_ms gauge\n");

        latencyStatsMap.forEach((target, stats) -> {
            String[] keys = {"target"};
            String[] vals = {target};
            gauge(sb, "latency_rtt_ms",    null, stats.rtt,    keys, vals, timestampMs);
            gauge(sb, "latency_min_ms",    null, stats.min,    keys, vals, timestampMs);
            gauge(sb, "latency_avg_ms",    null, stats.avg,    keys, vals, timestampMs);
            gauge(sb, "latency_max_ms",    null, stats.max,    keys, vals, timestampMs);
            gauge(sb, "latency_jitter_ms", null, stats.jitter, keys, vals, timestampMs);
        });


        // --- Disk I/O rates ---
        gauge(sb, "node_disk_read_kbps",
              "Disk read throughput in KB/s",
              diskReadKBps, null, null, timestampMs);
        gauge(sb, "node_disk_write_kbps",
              "Disk write throughput in KB/s",
              diskWriteKBps, null, null, timestampMs);

        // --- Per-device disk I/O ---
        disk.perDevice.forEach((dev, vals) -> {
            // vals = [readKB_cumulative, writeKB_cumulative, readMs, writeMs]
            gauge(sb, "node_disk_device_read_kb_total",
                  "Total KB read from device (counter)",
                  vals[0], new String[]{"device"}, new String[]{dev}, timestampMs);
            gauge(sb, "node_disk_device_write_kb_total",
                  null,
                  vals[1], new String[]{"device"}, new String[]{dev}, timestampMs);
        });

        // --- Network ---
        gauge(sb, "node_network_rx_kbps",
              "Network receive throughput in KB/s", netRxKBps, null, null, timestampMs);
        gauge(sb, "node_network_tx_kbps",
              "Network transmit throughput in KB/s", netTxKBps, null, null, timestampMs);

        // --- Top processes ---
        if (topProcs != null) {
            sb.append("# HELP node_process_cpu_percent CPU usage percent by process\n");
            sb.append("# TYPE node_process_cpu_percent gauge\n");
            for (ProcessInfo p : topProcs) {
                String safeLabel = p.name.replace("\\", "\\\\").replace("\"", "\\\"");
                sb.append(String.format(
                    "node_process_cpu_percent{pid=\"%d\",name=\"%s\",state=\"%s\"} %.2f %d%n",
                    p.pid, safeLabel, p.stateLabel(), p.cpuPercent, timestampMs));
            }
            sb.append("# HELP node_process_rss_kb RSS memory in KB by process\n");
            sb.append("# TYPE node_process_rss_kb gauge\n");
            for (ProcessInfo p : topProcs) {
                String safeLabel = p.name.replace("\\", "\\\\").replace("\"", "\\\"");
                sb.append(String.format(
                    "node_process_rss_kb{pid=\"%d\",name=\"%s\"} %d %d%n",
                    p.pid, safeLabel, p.rssKB, timestampMs));
            }
        }

        return sb.toString();
    }

    /** Write HELP+TYPE header (only if help != null) and then one sample line. */
    private static void gauge(StringBuilder sb, String name, String help,
                               double value, String[] labelKeys, String[] labelVals,
                               long tsMs) {
        if (help != null) {
            sb.append("# HELP ").append(name).append(' ').append(help).append('\n');
            sb.append("# TYPE ").append(name).append(" gauge\n");
        }
        sb.append(name);
        if (labelKeys != null && labelKeys.length > 0) {
            sb.append('{');
            for (int i = 0; i < labelKeys.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(labelKeys[i]).append("=\"").append(labelVals[i]).append('"');
            }
            sb.append('}');
        }
        sb.append(' ');
        if (Double.isNaN(value) || Double.isInfinite(value)) sb.append("NaN");
        else sb.append(String.format("%.4f", value));
        sb.append(' ').append(tsMs).append('\n');
    }
}
