package com.hospital.scheduler.dao;

import com.hospital.scheduler.db.DatabaseConnection;
import com.hospital.scheduler.exception.ShiftConflictException;
import com.hospital.scheduler.model.Shift;
import com.hospital.scheduler.model.Staff;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Shift persistence + conflict prevention.
 *
 * Conflict rule: two shifts overlap when  existing.start < new.end  AND  existing.end > new.start
 * (same staff, same date, ignoring CANCELLED). Back-to-back shifts (08-16, 16-23) are allowed.
 *
 * Race-condition safety: every write runs in a transaction that first locks the staff row
 * (SELECT ... FOR UPDATE), so two concurrent requests for the same person are serialized
 * and the second one sees the first one's committed shift.
 */
public class ShiftDAO {

    private static final String SELECT_BASE =
            "SELECT s.id, s.staff_id, st.name AS staff_name, st.role AS staff_role, s.department, " +
            "s.shift_date, s.start_time, s.end_time, s.status " +
            "FROM shifts s JOIN staff st ON st.id = s.staff_id ";

    // ---------------------------------------------------------------- READ

    public List<Shift> findAll() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT_BASE + "ORDER BY s.shift_date, s.start_time, st.name");
             ResultSet rs = ps.executeQuery()) {
            List<Shift> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        }
    }

    public Optional<Shift> findById(long id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            return findById(c, id);
        }
    }

    // -------------------------------------------------------------- CREATE

    /** Validates, checks for conflicts and inserts atomically. Returns the saved shift. */
    public Shift create(Shift shift) throws SQLException, ShiftConflictException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                String staffDept = lockStaff(c, shift.staffId());          // 404 if unknown staff
                String dept = (shift.department() == null || shift.department().isBlank())
                        ? staffDept : shift.department();
                assertNoConflict(c, shift.staffId(), shift.shiftDate(),
                        shift.startTime(), shift.endTime(), null);
                long id = insert(c, shift, dept);
                c.commit();
                return findById(c, id).orElseThrow();
            } catch (SQLException | ShiftConflictException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // -------------------------------------------------------------- UPDATE

    /** Changes status. Re-activating a shift (-> SCHEDULED) is re-checked for conflicts. */
    public void updateStatus(long id, Shift.Status status) throws SQLException, ShiftConflictException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Shift s = findById(c, id)
                        .orElseThrow(() -> new NoSuchElementException("Shift " + id + " not found."));
                if (status == Shift.Status.SCHEDULED) {
                    lockStaff(c, s.staffId());
                    assertNoConflict(c, s.staffId(), s.shiftDate(), s.startTime(), s.endTime(), id);
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE shifts SET status = ? WHERE id = ?")) {
                    ps.setString(1, status.name());
                    ps.setLong(2, id);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | ShiftConflictException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // -------------------------------------------------------------- DELETE

    public boolean delete(long id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shifts WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------ INTERNALS

    /** Locks the staff row for the duration of the transaction; returns their home department. */
    private String lockStaff(Connection c, int staffId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT department FROM staff WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Staff member " + staffId + " not found.");
                return rs.getString(1);
            }
        }
    }

    /** Throws ShiftConflictException (with details of the clashing shift) on any overlap. */
    private void assertNoConflict(Connection c, int staffId, LocalDate date,
                                  LocalTime start, LocalTime end, Long excludeId)
            throws SQLException, ShiftConflictException {
        String sql = SELECT_BASE +
                "WHERE s.staff_id = ? AND s.shift_date = ? AND s.status <> 'CANCELLED' " +
                "AND s.start_time < ? AND s.end_time > ? " +
                (excludeId != null ? "AND s.id <> ? " : "") + "LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, staffId);
            ps.setObject(2, date);
            ps.setObject(3, end);     // existing.start < new.end
            ps.setObject(4, start);   // existing.end   > new.start
            if (excludeId != null) ps.setLong(5, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Shift clash = map(rs);
                    throw new ShiftConflictException(String.format(
                            "Staff conflict detected! %s is already booked %s–%s on %s.",
                            clash.staffName(), clash.startTime(), clash.endTime(), clash.shiftDate()));
                }
            }
        }
    }

    private long insert(Connection c, Shift s, String department) throws SQLException {
        String sql = "INSERT INTO shifts (staff_id, department, shift_date, start_time, end_time, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.staffId());
            ps.setString(2, department);
            ps.setObject(3, s.shiftDate());
            ps.setObject(4, s.startTime());
            ps.setObject(5, s.endTime());
            ps.setString(6, s.status().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);   // id is the first column in both MySQL and PostgreSQL
            }
        }
    }

    private Optional<Shift> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT_BASE + "WHERE s.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private Shift map(ResultSet rs) throws SQLException {
        return new Shift(rs.getLong("id"), rs.getInt("staff_id"), rs.getString("staff_name"),
                Staff.Role.valueOf(rs.getString("staff_role")), rs.getString("department"),
                rs.getObject("shift_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                Shift.Status.valueOf(rs.getString("status")));
    }
}
