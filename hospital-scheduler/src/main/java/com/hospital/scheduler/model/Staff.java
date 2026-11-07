package com.hospital.scheduler.model;

public record Staff(int id, String name, Role role, String department, String contact) {
    public enum Role { DOCTOR, NURSE, ADMIN }
}
