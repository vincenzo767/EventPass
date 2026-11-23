package com.cubetech.eventpass;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Zero-configuration local launcher: starts a real embedded PostgreSQL 17 server (data kept in
 * {@code backend/.local-db}) and runs EventPass against it with demo data.
 *
 * <p>Run with {@code mvn spring-boot:test-run} from the {@code backend} directory. Not used in
 * production, where the app connects to the PostgreSQL configured by {@code DB_URL}.
 */
public final class DevEventPassApplication {

    private static final int PORT = 54329;

    private DevEventPassApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Path.of(".local-db").toAbsolutePath();
        Files.createDirectories(dataDirectory);
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(dataDirectory)
                .setCleanDataDirectory(false)
                .setPort(PORT)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (Exception ignored) {
                // Best effort on shutdown.
            }
        }));

        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        // Relaxed rate limits so the automated end-to-end suite can run repeatedly from one machine.
        // Production keeps the stricter defaults from application.yml.
        setIfAbsent("app.rate-limit.registration", "300");
        setIfAbsent("app.rate-limit.ticket-lookup", "120");
        setIfAbsent("app.rate-limit.login", "120");
        SpringApplication.run(EventPassApplication.class, args);
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
