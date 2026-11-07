package com.hospital.scheduler;

import com.hospital.scheduler.db.DatabaseConnection;
import com.hospital.scheduler.web.ApiServer;

import java.sql.Connection;

public final class Main {
    public static void main(String[] args) throws Exception {
        // Fail fast if the database is unreachable.
        try (Connection ignored = DatabaseConnection.getConnection()) {
            System.out.println("Database connection OK.");
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        new ApiServer(port).start();
    }
}
