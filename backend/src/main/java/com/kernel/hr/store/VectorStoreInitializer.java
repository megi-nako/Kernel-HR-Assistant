package com.kernel.hr.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Enables the pgvector extension before the rest of the application starts using
 * the vector store. Runs after the DataSource is ready but before any ingestion.
 *
 * Gives a clear, actionable error message if pgvector is not installed on the OS,
 * instead of an opaque Spring stack trace.
 */
@Component
public class VectorStoreInitializer {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreInitializer.class);

    private final JdbcTemplate jdbc;

    public VectorStoreInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void enablePgVector() {
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            log.info("pgvector extension is enabled.");
        } catch (Exception e) {
            log.error("""
                    ============================================================
                    pgvector extension is NOT installed on this PostgreSQL server.
                    Vector search will not work until it is installed.

                    Quick fix — Docker (recommended):
                      docker run -d --name hr-postgres \\
                        -e POSTGRES_USER=postgres \\
                        -e POSTGRES_PASSWORD=admin \\
                        -p 5432:5432 \\
                        pgvector/pgvector:pg17

                    Windows native (PowerShell as Administrator):
                      $pg = "C:\\Program Files\\PostgreSQL\\17"
                      # Download v0.8.0 zip from github.com/pgvector/pgvector/releases
                      # Copy vector.dll  -> $pg\\lib
                      # Copy vector.control + vector--*.sql -> $pg\\share\\extension
                      Restart-Service postgresql-x64-17

                    After installing, restart the application.
                    ============================================================
                    """);
        }
    }
}
