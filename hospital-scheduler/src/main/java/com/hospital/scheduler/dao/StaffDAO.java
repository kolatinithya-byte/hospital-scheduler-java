package com.hospital.scheduler.dao;

import com.hospital.scheduler.db.DatabaseConnection;
import com.hospital.scheduler.model.Staff;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class StaffDAO {

    private static final String COLS = "id, name, role, department, contact";

    public List<Staff> findAll() throws SQLException {
        return query("SELECT " + COLS + " FROM staff ORDER BY role, name");
    }

    /** Staff with NO active (non-cancelled) shift overlapping [start, end) on the given date. */
    public List<Staff> findAvailable(LocalDate date, LocalTime start, LocalTime end) throws SQLException {
        String sql = "SELECT " + COLS + " FROM staff s WHERE NOT EXISTS (" +
                " SELECT 1 FROM shifts sh WHERE sh.staff_id = s.id AND sh.shift_date = ?" +
                " AND sh.status <> 'CANCELLED' AND sh.start_time < ? AND sh.end_time > ?)" +
                " ORDER BY role, name";
        return query(sql, date, end, start);
    }

    private List<Staff> query(String sql, Object... params) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Staff> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Staff(rs.getInt("id"), rs.getString("name"),
                            Staff.Role.valueOf(rs.getString("role")),
                            rs.getString("department"), rs.getString("contact")));
                }
                return out;
            }
        }
    }
}
