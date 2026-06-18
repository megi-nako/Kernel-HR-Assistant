package com.kernel.hr;

import com.kernel.hr.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class HrAssistantApplication {

    public static void main(String[] args) {
        ensureDatabaseExists();
        SpringApplication.run(HrAssistantApplication.class, args);
    }

    /**
     * Runs before Spring initializes its DataSource. Connects to the PostgreSQL
     * administrative database ("postgres") on the same host and creates the target
     * database if it does not already exist.
     *
     * CREATE DATABASE cannot run inside a transaction, so auto-commit must be true
     * (which is the JDBC default).
     */
    private static void ensureDatabaseExists() {
        String url      = env("DATABASE_URL",      "jdbc:postgresql://localhost:5432/hrdb");
        String user     = env("DATABASE_USER",     "postgres");
        String password = env("DATABASE_PASSWORD", "admin");

        String dbName  = extractDatabaseName(url);
        String adminUrl = buildAdminUrl(url);

        try (Connection conn = DriverManager.getConnection(adminUrl, user, password)) {
            conn.setAutoCommit(true); // CREATE DATABASE must not run in a transaction

            if (!databaseExists(conn, dbName)) {
                try (Statement stmt = conn.createStatement()) {
                    // Identifier is validated above — only alphanumeric/underscore allowed.
                    stmt.execute("CREATE DATABASE \"" + dbName + "\"");
                    System.out.printf("[DB INIT] Database '%s' created.%n", dbName);
                }
            } else {
                System.out.printf("[DB INIT] Database '%s' already exists.%n", dbName);
            }
        } catch (SQLException e) {
            // Log but do not abort — Spring will surface the real error if the DB is truly unavailable.
            System.err.printf("[DB INIT] Warning: could not auto-create database '%s': %s%n",
                    dbName, e.getMessage());
        }
    }

    private static boolean databaseExists(Connection conn, String dbName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Extracts the database name from a JDBC URL of the form
     * jdbc:postgresql://host:port/dbname[?params]
     * and validates that it contains only safe identifier characters.
     */
    private static String extractDatabaseName(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0) throw new IllegalArgumentException("Cannot parse DATABASE_URL: " + url);
        String name = url.substring(lastSlash + 1);
        int queryStart = name.indexOf('?');
        if (queryStart >= 0) name = name.substring(0, queryStart);
        if (!name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Database name contains unsafe characters: " + name);
        }
        return name;
    }

    /**
     * Replaces the database name in the URL with "postgres" to connect as admin.
     */
    private static String buildAdminUrl(String url) {
        int lastSlash = url.lastIndexOf('/');
        String base = url.substring(0, lastSlash);
        String rest = url.substring(lastSlash + 1);
        int queryStart = rest.indexOf('?');
        String suffix = queryStart >= 0 ? rest.substring(queryStart) : "";
        return base + "/postgres" + suffix;
    }

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
