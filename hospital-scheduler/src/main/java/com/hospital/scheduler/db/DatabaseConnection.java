package com.hospital.scheduler.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Minimal JDBC connection factory. Configuration comes from environment variables:
 *   DB_URL      e.g. jdbc:mysql://localhost:3306/hospital_scheduler
 *               or   jdbc:postgresql://localhost:5432/hospital_scheduler
 *   DB_USER, DB_PASSWORD
 * For production, replace DriverManager with a pool (e.g. HikariCP).
 */
public final class DatabaseConnection {

    private static final String URL      = env("DB_URL", "jdbc:mysql://localhost:3306/hospital_scheduler");
    private static final String USER     = env("DB_USER", "root");
    private static final String PASSWORD = env("DB_PASSWORD", "");

    private DatabaseConnection() {}

    /** Returns a NEW connection; callers must close it (use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
