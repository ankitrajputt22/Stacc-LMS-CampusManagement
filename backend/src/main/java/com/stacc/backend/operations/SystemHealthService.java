package com.stacc.backend.operations;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.stacc.backend.lms.ai.AiProperties;
import com.stacc.backend.notification.MailProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * The system health view (v0.16.7): the overall health and each part's status from Actuator, the database schema
 * version from Flyway, whether optional features and metrics are on, and the JVM's uptime and memory. Read live on each
 * request; nothing is stored, and no address, credential, or other setting is sent.
 */
@Service
public class SystemHealthService {

    public record SystemHealth(String status, Map<String, String> parts, String schemaVersion, int migrations, boolean mailEnabled,
            boolean aiEnabled, boolean metricsEnabled, List<String> profiles, Instant startedAt, long uptimeMinutes, long heapUsedMb,
            long heapMaxMb, int processors, int threads) {
    }

    private final HealthEndpoint health;
    private final ObjectProvider<Flyway> flyway;
    private final MailProperties mail;
    private final AiProperties ai;
    private final Environment environment;

    public SystemHealthService(HealthEndpoint health, ObjectProvider<Flyway> flyway, MailProperties mail, AiProperties ai, Environment environment) {
        this.health = health;
        this.flyway = flyway;
        this.mail = mail;
        this.ai = ai;
        this.environment = environment;
    }

    public SystemHealth now() {
        HealthDescriptor overall = health.health();
        Map<String, String> parts = new TreeMap<>();
        if (overall instanceof CompositeHealthDescriptor composite) {
            composite.getComponents().forEach((name, part) -> parts.put(name, part.getStatus().getCode()));
        }
        Flyway migrations = flyway.getIfAvailable();
        MigrationInfo current = migrations == null ? null : migrations.info().current();
        int applied = migrations == null ? 0 : migrations.info().applied().length;
        var runtime = ManagementFactory.getRuntimeMXBean();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        List<String> profiles = Arrays.stream(environment.getActiveProfiles()).sorted().toList();
        return new SystemHealth(overall.getStatus().getCode(), parts, current == null ? null : current.getVersion().getVersion(), applied,
                mail.enabled(), ai.enabled(), profiles.contains("monitoring"), profiles, Instant.ofEpochMilli(runtime.getStartTime()),
                Duration.ofMillis(runtime.getUptime()).toMinutes(), heap.getUsed() / (1024 * 1024),
                heap.getMax() < 0 ? -1 : heap.getMax() / (1024 * 1024), Runtime.getRuntime().availableProcessors(),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }
}
