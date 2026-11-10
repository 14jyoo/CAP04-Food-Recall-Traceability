package com.fyndig.recall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RecallTraceabilityApplication {
    public static void main(String[] args) {
        // Store and compare all timestamps in UTC. Also avoids legacy zone ids (e.g. Asia/Calcutta) that
        // PostgreSQL 16+ rejects at connection time.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RecallTraceabilityApplication.class, args);
    }
}
