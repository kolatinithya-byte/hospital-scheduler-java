package com.hospital.scheduler.exception;

/** Thrown when a staff member would be double-booked. Maps to HTTP 409. */
public class ShiftConflictException extends Exception {
    public ShiftConflictException(String message) { super(message); }
}
