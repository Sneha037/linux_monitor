package com.agent.collectors;

import java.time.Instant;

public class Logger {

    public static void info(String component, String msg) {
        System.out.printf("%s [INFO ] %s: %s%n", Instant.now(), component, msg);
    }

    public static void warn(String component, String msg) {
        System.out.printf("%s [WARN ] %s: %s%n", Instant.now(), component, msg);
    }

    public static void error(String component, Exception e) {
        System.err.printf("%s [ERROR] %s: %s%n", Instant.now(), component, e.getMessage());
    }

    public static void error(String component, String msg) {
        System.err.printf("%s [ERROR] %s: %s%n", Instant.now(), component, msg);
    }
}
