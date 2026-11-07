package com.hospital.scheduler.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A scheduled shift. staffName/staffRole are read-only values joined from the staff
 * table for display; they are null when creating a new shift (id is also null then).
 */
public record Shift(Long id, int staffId, String staffName, Staff.Role staffRole,
                    String department, LocalDate shiftDate,
                    LocalTime startTime, LocalTime endTime, Status status) {

    public enum Status { SCHEDULED, COMPLETED, CANCELLED }

    /** Compact constructor: validates invariants and applies defaults. */
    public Shift {
        Objects.requireNonNull(shiftDate, "shiftDate is required");
        Objects.requireNonNull(startTime, "startTime is required");
        Objects.requireNonNull(endTime, "endTime is required");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        if (status == null) status = Status.SCHEDULED;
    }
}
