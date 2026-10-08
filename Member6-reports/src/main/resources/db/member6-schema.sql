-- Member 6 schema additions (MySQL 8). Run once, after Member 5's base schema exists.
-- certificate_issues is a separate table so Member 6 does not have to change anyone else's tables.

CREATE TABLE IF NOT EXISTS certificate_issues (
    certificate_id  VARCHAR(30)  NOT NULL PRIMARY KEY,
    patient_id      INT          NOT NULL,
    issued_at       DATETIME     NOT NULL,
    issued_by       INT          NOT NULL,
    dose_count      INT          NOT NULL,
    snapshot_hash   CHAR(64)     NOT NULL,
    CONSTRAINT fk_ci_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id),
    CONSTRAINT fk_ci_user    FOREIGN KEY (issued_by)  REFERENCES users (user_id),
    INDEX idx_ci_patient (patient_id, issued_at)
);

-- Speeds up the daily summary report (FR16) as records grow (NFR04/NFR05).
CREATE INDEX idx_vr_date_administered ON vaccination_records (date_administered);
