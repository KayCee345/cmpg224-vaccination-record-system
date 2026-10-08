package com.immunitech.immunecare.certificate;

import com.immunitech.immunecare.certificate.CertificateModels.DoseLine;
import com.immunitech.immunecare.certificate.CertificateModels.IssueRecord;
import com.immunitech.immunecare.certificate.CertificateModels.PatientIdentity;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CertificateRepository {

    private static final RowMapper<IssueRecord> ISSUE_MAPPER = (rs, i) -> new IssueRecord(
            rs.getString("certificate_id"),
            rs.getLong("patient_id"),
            rs.getTimestamp("issued_at").toLocalDateTime(),
            rs.getLong("issued_by"),
            rs.getInt("dose_count"),
            rs.getString("snapshot_hash"));

    private final JdbcTemplate jdbc;

    public CertificateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PatientIdentity> findPatient(long patientId) {
        return jdbc.query("""
                        SELECT patient_id, full_name, national_id, date_of_birth, gender
                        FROM patients WHERE patient_id = ?
                        """,
                (rs, i) -> new PatientIdentity(
                        rs.getLong("patient_id"),
                        rs.getString("full_name"),
                        rs.getString("national_id"),
                        rs.getDate("date_of_birth").toLocalDate(),
                        rs.getString("gender")),
                patientId).stream().findFirst();
    }

    /** All doses for the patient, oldest first (FR10 order). */
    public List<DoseLine> findDoses(long patientId) {
        return jdbc.query("""
                        SELECT vr.record_id, v.name AS vaccine_name, vr.dose_number, vr.date_administered,
                               vr.batch_number, vr.worker_id
                        FROM vaccination_records vr
                        JOIN vaccines v ON v.vaccine_id = vr.vaccine_id
                        WHERE vr.patient_id = ?
                        ORDER BY vr.date_administered, vr.record_id
                        """,
                (rs, i) -> new DoseLine(
                        rs.getLong("record_id"),
                        rs.getString("vaccine_name"),
                        rs.getInt("dose_number"),
                        rs.getDate("date_administered").toLocalDate(),
                        rs.getString("batch_number"),
                        rs.getLong("worker_id")),
                patientId);
    }

    public Optional<IssueRecord> findLatestIssue(long patientId) {
        return jdbc.query("""
                        SELECT certificate_id, patient_id, issued_at, issued_by, dose_count, snapshot_hash
                        FROM certificate_issues WHERE patient_id = ?
                        ORDER BY issued_at DESC LIMIT 1
                        """,
                ISSUE_MAPPER, patientId).stream().findFirst();
    }

    public Optional<IssueRecord> findIssue(String certificateId) {
        return jdbc.query("""
                        SELECT certificate_id, patient_id, issued_at, issued_by, dose_count, snapshot_hash
                        FROM certificate_issues WHERE certificate_id = ?
                        """,
                ISSUE_MAPPER, certificateId).stream().findFirst();
    }

    public void saveIssue(IssueRecord issue) {
        jdbc.update("""
                        INSERT INTO certificate_issues
                            (certificate_id, patient_id, issued_at, issued_by, dose_count, snapshot_hash)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                issue.certificateId(), issue.patientId(), Timestamp.valueOf(issue.issuedAt()),
                issue.issuedBy(), issue.doseCount(), issue.snapshotHash());
    }

    public Optional<Long> findUserId(String username) {
        return jdbc.query("SELECT user_id FROM users WHERE username = ?",
                (rs, i) -> rs.getLong(1), username).stream().findFirst();
    }

    public boolean patientBelongsToUser(long patientId, long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM patients WHERE patient_id = ? AND user_id = ?",
                Integer.class, patientId, userId);
        return count != null && count > 0;
    }
}
