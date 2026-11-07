-- Hospital Job Scheduling System: MySQL 8.0.16+ (CHECKs enforced).
-- PostgreSQL: replace "INT AUTO_INCREMENT" with "SERIAL"; everything else is portable.
-- MySQL:  CREATE DATABASE hospital_scheduler; USE hospital_scheduler;
-- Postgres: CREATE DATABASE hospital_scheduler;  (then connect to it)

DROP TABLE IF EXISTS shifts;
DROP TABLE IF EXISTS staff;

CREATE TABLE staff (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    department  VARCHAR(60)  NOT NULL,
    contact     VARCHAR(100) NOT NULL,
    CONSTRAINT uq_staff_contact UNIQUE (contact),
    CONSTRAINT chk_staff_role   CHECK (role IN ('DOCTOR', 'NURSE', 'ADMIN'))
);

CREATE TABLE shifts (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    staff_id    INT          NOT NULL,
    department  VARCHAR(60)  NOT NULL,
    shift_date  DATE         NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    status      VARCHAR(15)  NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT fk_shift_staff   FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_time   CHECK (end_time > start_time),
    CONSTRAINT chk_shift_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED'))
);

-- Speeds up the overlap lookup (same staff + same day).
CREATE INDEX idx_shifts_staff_date ON shifts (staff_id, shift_date);

-- ---------- Seed data ----------
INSERT INTO staff (name, role, department, contact) VALUES
 ('Dr. Sarah Chen',      'DOCTOR', 'Cardiology', 'sarah.chen@hospital.org'),
 ('Dr. Marcus Okafor',   'DOCTOR', 'Emergency',  'marcus.okafor@hospital.org'),
 ('Dr. Priya Raman',     'DOCTOR', 'Pediatrics', 'priya.raman@hospital.org'),
 ('Elena Rossi, RN',     'NURSE',  'Emergency',  'elena.rossi@hospital.org'),
 ('James Whitaker, RN',  'NURSE',  'Cardiology', 'james.whitaker@hospital.org'),
 ('Amara Diallo, RN',    'NURSE',  'Pediatrics', 'amara.diallo@hospital.org'),
 ('Daniel Foster',       'ADMIN',  'Admissions', 'daniel.foster@hospital.org'),
 ('Laura Kim',           'ADMIN',  'Billing',    'laura.kim@hospital.org');

INSERT INTO shifts (staff_id, department, shift_date, start_time, end_time, status) VALUES
 (1, 'Cardiology', '2026-11-09', '08:00', '16:00', 'SCHEDULED'),
 (2, 'Emergency',  '2026-11-09', '16:00', '23:00', 'SCHEDULED'),
 (4, 'Emergency',  '2026-11-09', '07:00', '15:00', 'SCHEDULED'),
 (5, 'Cardiology', '2026-11-10', '07:00', '15:00', 'SCHEDULED'),
 (6, 'Pediatrics', '2026-11-10', '12:00', '20:00', 'SCHEDULED'),
 (7, 'Admissions', '2026-11-10', '09:00', '17:00', 'SCHEDULED'),
 (3, 'Pediatrics', '2026-11-03', '08:00', '16:00', 'COMPLETED');
