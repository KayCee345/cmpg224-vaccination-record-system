package com.immunitech.immunecare.certificate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CertificateModels {

    private CertificateModels() {
    }

    public record PatientIdentity(long patientId, String fullName, String nationalId,
                                  LocalDate dateOfBirth, String gender) {
    }

    public record DoseLine(long recordId, String vaccineName, int doseNumber, LocalDate dateAdministered,
                           String batchNumber, long workerId) {
    }

    /** A row of certificate_issues. */
    public record IssueRecord(String certificateId, long patientId, LocalDateTime issuedAt, long issuedBy,
                              int doseCount, String snapshotHash) {
    }

    public record IssuedCertificate(String certificateId, byte[] pdf) {
    }

    /** Everything the PDF generator needs; it never touches the database. */
    public record CertificateData(String certificateId, String clinicName, String verifyUrl, LocalDate issuedOn,
                                  PatientIdentity patient, List<DoseLine> doses) {
    }

    public enum VerificationStatus {
        /** Certificate exists and the patient's records still match what was certified. */
        VALID,
        /** Certificate was genuinely issued, but the patient's records have changed since. */
        SUPERSEDED,
        NOT_FOUND
    }

    public record VerificationResult(VerificationStatus status, String maskedName, LocalDate issuedOn,
                                     int doseCount) {
        public static VerificationResult notFound() {
            return new VerificationResult(VerificationStatus.NOT_FOUND, null, null, 0);
        }
    }
}
