package com.agent.server;

import com.agent.collectors.*;
import com.agent.export.PrometheusExporter;
import com.agent.metrics.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


public class MetricsHttpServer {

    private static final int    PORT        = 8000;
    private static final long   CACHE_MS    = 1_000L;
    private static final int    TOP_PROCS   = 10;
    private static final long   START_TIME  = System.currentTimeMillis();

    // Shared cached state (written by collector thread, read by HTTP threads)
    private static volatile String cachedPrometheus = "# not yet collected\n";
    private static volatile String cachedJson       = "{}";
    private static volatile long   cachedAt         = 0L;

    // Delta state (only touched by the single collector thread — no sync needed)
    private static DiskStats    prevDisk    = null;
    private static NetworkStats prevNet     = null;
    private static long         prevTimeMs  = 0L;

    private static final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) throws Exception {

        // Prime delta state before serving any requests
        prevDisk   = DiskCollector.getDiskStats();
        prevNet    = NetworkCollector.getNetworkStats();
        prevTimeMs = System.currentTimeMillis();
        // First CPU sample is a throwaway — establishes the baseline
        CpuCollector.getCpuStats();

        // Background thread: refresh metrics every second
        Thread collector = new Thread(MetricsHttpServer::collectLoop, "metrics-collector");
        collector.setDaemon(true);
        collector.start();

        // HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/metrics", new PrometheusHandler());
        server.createContext("/metric",  new JsonHandler());
        server.createContext("/health",  new HealthHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        Logger.info("MetricsHttpServer",
            "Listening on :" + PORT
            + " | /metrics (Prometheus) | /metric (JSON) | /health");

        // Graceful shutdown on SIGTERM / SIGINT (Ctrl-C, Docker stop, systemd stop)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("MetricsHttpServer", "Shutdown signal received — stopping...");
            running.set(false);
            server.stop(2); // 2 second grace period for in-flight requests
            Logger.info("MetricsHttpServer", "Stopped cleanly.");
        }, "shutdown-hook"));
    }


    // Background collection loop

    private static void collectLoop() {
        while (running.get()) {
            try {
                Thread.sleep(CACHE_MS);
                refreshCache();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Logger.error("collectLoop", e);
            }
        }
    }

    private static void refreshCache() {
        long now = System.currentTimeMillis();

        CpuStats     cpu  = CpuCollector.getCpuStats();
        MemoryStats  mem  = MemoryCollector.getMemoryStats();
        LoadAvgStats load = LoadAvgCollector.getLoadAverages();
        TCPConnectionStats tcp = TCPCollector.getTCPStats();

        DiskStats    currDisk = DiskCollector.getDiskStats();
        NetworkStats currNet  = NetworkCollector.getNetworkStats();

        double elapsedSec = Math.max((now - prevTimeMs) / 1000.0, 0.001);

        double diskReadKBps  = prevDisk != null
            ? (currDisk.readKB  - prevDisk.readKB)  / elapsedSec : 0;
        double diskWriteKBps = prevDisk != null
            ? (currDisk.writeKB - prevDisk.writeKB) / elapsedSec : 0;

        double netRxKBps = prevNet != null
            ? ((currNet.rxBytes - prevNet.rxBytes) / 1024.0) / elapsedSec : 0;
        double netTxKBps = prevNet != null
            ? ((currNet.txBytes - prevNet.txBytes) / 1024.0) / elapsedSec : 0;

        List<ProcessInfo> topProcs = ProcessCollector.getTopProcesses(TOP_PROCS);

        // Build Prometheus format
        cachedPrometheus = PrometheusExporter.export(
            cpu, mem, load, currDisk, currNet, tcp, topProcs,
            diskReadKBps, diskWriteKBps, netRxKBps, netTxKBps, now);

        // Build JSON (kept for compatibility)
        cachedJson = buildJson(cpu, mem, load, tcp,
            diskReadKBps, diskWriteKBps, netRxKBps, netTxKBps,
            topProcs, now);

        cachedAt  = now;
        prevDisk  = currDisk;
        prevNet   = currNet;
        prevTimeMs = now;
    }


    // HTTP handlers

    static class PrometheusHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = cachedPrometheus.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type",
                "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    static class JsonHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = cachedJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            long uptimeSec = (System.currentTimeMillis() - START_TIME) / 1000;
            String body = String.format(
                "{\"status\":\"ok\",\"uptime_sec\":%d,\"last_collection_ms\":%d}",
                uptimeSec, cachedAt);
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        }
    }

    // -------------------------------------------------------------------------
    // JSON builder
    // -------------------------------------------------------------------------

    private static String buildJson(
        CpuStats cpu, MemoryStats mem, LoadAvgStats load, TCPConnectionStats tcp,
        double diskReadKBps, double diskWriteKBps,
        double netRxKBps, double netTxKBps,
        List<ProcessInfo> procs, long ts)
    {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('{');
        sb.append("\"timestamp_ms\":").append(ts).append(',');
        sb.append("\"cpu_percent\":").append(fmt(cpu.usagePercent)).append(',');

        // Per-core array
        sb.append("\"cpu_cores\":[");
        for (int i = 0; i < cpu.corePercents.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(fmt(cpu.corePercents[i]));
        }
        sb.append("],");

        sb.append("\"memory_percent\":").append(fmt(mem.usedPercent)).append(',');
        sb.append("\"memory_total_kb\":").append(mem.totalKB).append(',');
        sb.append("\"memory_used_kb\":").append(mem.usedKB).append(',');
        sb.append("\"load_1m\":").append(fmt(load.oneMin)).append(',');
        sb.append("\"load_5m\":").append(fmt(load.fiveMin)).append(',');
        sb.append("\"load_15m\":").append(fmt(load.fifteenMin)).append(',');

        tcp.stateCounts.forEach( (state, val) -> {
            sb.append("\"").append(state).append("\":").append(fmt(Double.valueOf(val))).append(',');
        });

        sb.append("\"established_connections\":["); int c = 0;
        for(String established : tcp.established)
        {
            if(c > 0)
            {
                sb.append(",");
            }
            sb.append("\"").append(established).append("\"");
        }
        sb.append("\"disk_read_kbps\":").append(fmt(diskReadKBps)).append(',');
        sb.append("\"disk_write_kbps\":").append(fmt(diskWriteKBps)).append(',');
        sb.append("\"net_rx_kbps\":").append(fmt(netRxKBps)).append(',');
        sb.append("\"net_tx_kbps\":").append(fmt(netTxKBps)).append(',');

        // Top processes
        sb.append("\"top_processes\":[");
        for (int i = 0; i < procs.size(); i++) {
            ProcessInfo p = procs.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"pid\":").append(p.pid).append(',');
            sb.append("\"name\":\"").append(p.name.replace("\"", "\\\"")).append("\",");
            sb.append("\"state\":\"").append(p.stateLabel()).append("\",");
            sb.append("\"cpu_percent\":").append(fmt(p.cpuPercent)).append(',');
            sb.append("\"rss_kb\":").append(p.rssKB);
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        return String.format("%.2f", v);
    }
}
