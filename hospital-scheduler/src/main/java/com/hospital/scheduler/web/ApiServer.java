package com.hospital.scheduler.web;

import com.hospital.scheduler.dao.ShiftDAO;
import com.hospital.scheduler.dao.StaffDAO;
import com.hospital.scheduler.exception.ShiftConflictException;
import com.hospital.scheduler.model.Shift;
import com.hospital.scheduler.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * REST API + static file host.
 *
 *   GET    /api/shifts                              all shifts
 *   POST   /api/shifts                              schedule a shift (validated, conflict-checked)
 *   PUT    /api/shifts/{id}/status                  {"status":"COMPLETED|CANCELLED|SCHEDULED"}
 *   DELETE /api/shifts/{id}
 *   GET    /api/staff                               all staff
 *   GET    /api/staff/available?date=&start=&end=   staff free for that window
 *   GET    /*                                       frontend from classpath /static
 */
public class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    private final HttpServer server;
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final StaffDAO staffDAO = new StaffDAO();

    @FunctionalInterface
    private interface Route { void handle(HttpExchange ex) throws Exception; }

    public ApiServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/shifts", safe(this::handleShifts));
        server.createContext("/api/staff", safe(this::handleStaff));
        server.createContext("/", this::serveStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
        LOG.info("Server running at http://localhost:" + server.getAddress().getPort());
    }

    // ------------------------------------------------------------ ROUTES

    private void handleShifts(HttpExchange ex) throws Exception {
        String method = ex.getRequestMethod();
        String[] p = ex.getRequestURI().getPath().split("/"); // ["", "api", "shifts", "{id}", "status"]

        if (p.length == 3) {
            switch (method) {
                case "GET" -> send(ex, 200, Json.array(shiftDAO.findAll(), Json::shiftToJson));
                case "POST" -> {
                    Shift created = shiftDAO.create(parseNewShift(readBody(ex)));
                    send(ex, 201, Json.shiftToJson(created));
                }
                default -> send(ex, 405, Json.error("Method not allowed."));
            }
        } else if (p.length == 4 && method.equals("DELETE")) {
            long id = Long.parseLong(p[3]);
            if (!shiftDAO.delete(id)) throw new NoSuchElementException("Shift " + id + " not found.");
            send(ex, 204, null);
        } else if (p.length == 5 && p[4].equals("status") && method.equals("PUT")) {
            long id = Long.parseLong(p[3]);
            String raw = Json.parseFlat(readBody(ex)).get("status");
            if (raw == null) throw new IllegalArgumentException("Field 'status' is required.");
            shiftDAO.updateStatus(id, Shift.Status.valueOf(raw.toUpperCase()));
            send(ex, 200, Json.shiftToJson(shiftDAO.findById(id).orElseThrow()));
        } else {
            send(ex, 404, Json.error("Unknown endpoint."));
        }
    }

    private void handleStaff(HttpExchange ex) throws Exception {
        if (!ex.getRequestMethod().equals("GET")) {
            send(ex, 405, Json.error("Method not allowed."));
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/api/staff")) {
            send(ex, 200, Json.array(staffDAO.findAll(), Json::staffToJson));
        } else if (path.equals("/api/staff/available")) {
            Map<String, String> q = query(ex.getRequestURI());
            for (String k : new String[]{"date", "start", "end"}) {
                if (q.getOrDefault(k, "").isBlank())
                    throw new IllegalArgumentException("Query parameter '" + k + "' is required.");
            }
            LocalTime start = LocalTime.parse(q.get("start"));
            LocalTime end = LocalTime.parse(q.get("end"));
            if (!end.isAfter(start)) throw new IllegalArgumentException("End time must be after start time.");
            send(ex, 200, Json.array(
                    staffDAO.findAvailable(LocalDate.parse(q.get("date")), start, end), Json::staffToJson));
        } else {
            send(ex, 404, Json.error("Unknown endpoint."));
        }
    }

    /** Request-level validation; the DAO handles conflict detection. */
    private Shift parseNewShift(String body) {
        Map<String, String> f = Json.parseFlat(body);
        for (String k : new String[]{"staffId", "shiftDate", "startTime", "endTime"}) {
            if (f.get(k) == null || f.get(k).isBlank())
                throw new IllegalArgumentException("Field '" + k + "' is required.");
        }
        LocalDate date = LocalDate.parse(f.get("shiftDate"));
        if (date.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Cannot schedule a shift in the past.");
        return new Shift(null, Integer.parseInt(f.get("staffId")), null, null, f.get("department"),
                date, LocalTime.parse(f.get("startTime")), LocalTime.parse(f.get("endTime")), null);
    }

    // ----------------------------------------------------- STATIC FILES

    private void serveStatic(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            if (path.contains("..")) { send(ex, 400, Json.error("Bad path.")); return; }

            try (InputStream in = ApiServer.class.getResourceAsStream("/static" + path)) {
                if (in == null) { send(ex, 404, Json.error("Not found.")); return; }
                byte[] data = in.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", mime(path));
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(data); }
            }
        } finally {
            ex.close();
        }
    }

    private static String mime(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------ HELPERS

    /** Wraps a route: maps exceptions to HTTP status codes so handlers stay clean. */
    private HttpHandler safe(Route route) {
        return ex -> {
            try {
                route.handle(ex);
            } catch (ShiftConflictException e) {
                send(ex, 409, Json.error(e.getMessage()));
            } catch (NoSuchElementException e) {
                send(ex, 404, Json.error(e.getMessage()));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                send(ex, 400, Json.error(e instanceof DateTimeParseException
                        ? "Invalid date or time format." : e.getMessage()));
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Database error", e);
                send(ex, 500, Json.error("Database error. Please try again."));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Unexpected error", e);
                send(ex, 500, Json.error("Internal server error."));
            } finally {
                ex.close();
            }
        };
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        if (body == null) { ex.sendResponseHeaders(status, -1); return; }
        byte[] bytes = body.getBytes(UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), UTF_8);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> m = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) return m;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            m.put(URLDecoder.decode(kv[0], UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], UTF_8) : "");
        }
        return m;
    }
}
