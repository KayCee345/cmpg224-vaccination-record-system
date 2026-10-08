package com.immunitech.immunecare.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.immunitech.immunecare.certificate.CertificateModels.DoseLine;
import com.immunitech.immunecare.certificate.CertificateModels.IssueRecord;
import com.immunitech.immunecare.certificate.CertificateModels.IssuedCertificate;
import com.immunitech.immunecare.certificate.CertificateModels.PatientIdentity;
import com.immunitech.immunecare.certificate.CertificateModels.VerificationResult;
import com.immunitech.immunecare.certificate.CertificateModels.VerificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CertificateServiceTest {

    private static final long PATIENT_ID = 10;

    private CertificateRepository repository;
    private CertificateService service;

    private final PatientIdentity patient =
            new PatientIdentity(PATIENT_ID, "Thandi Mokoena", "2601105000081", LocalDate.of(2026, 1, 10), "Female");
    private final List<DoseLine> doses =
            List.of(new DoseLine(100, "MMR", 1, LocalDate.of(2026, 10, 5), "B-MMR-1", 2));

    private static UsernamePasswordAuthenticationToken user(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "n/a", List.of(new SimpleGrantedAuthority(role)));
    }

    @BeforeEach
    void setUp() {
        repository = mock(CertificateRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T08:00:00Z"), ZoneOffset.UTC);
        service = new CertificateService(repository, new CertificateGenerator(), clock,
                "ImmuneCare Clinic", "http://localhost:8080/");
        when(repository.findPatient(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(repository.findDoses(PATIENT_ID)).thenReturn(doses);
        when(repository.findLatestIssue(PATIENT_ID)).thenReturn(Optional.empty());
        when(repository.findUserId("nurse")).thenReturn(Optional.of(2L));
        when(repository.findUserId("thandi")).thenReturn(Optional.of(3L));
        when(repository.findUserId("sipho")).thenReturn(Optional.of(4L));
        when(repository.patientBelongsToUser(PATIENT_ID, 3L)).thenReturn(true);
    }

    // ---------- issuing ----------

    @Test
    void workerCanIssueAndIssueIsStored() {
        IssuedCertificate cert = service.issueFor(PATIENT_ID, user("nurse", "ROLE_WORKER"));

        assertThat(cert.certificateId()).matches("IC-20261006-[0-9A-F]{8}");
        assertThat(new String(cert.pdf(), 0, 5)).isEqualTo("%PDF-");
        verify(repository).saveIssue(any(IssueRecord.class));
    }

    @Test
    void patientCanIssueOwnCertificate() {
        assertThat(service.issueFor(PATIENT_ID, user("thandi", "ROLE_PATIENT")).pdf()).isNotEmpty();
    }

    @Test
    void patientCannotIssueSomeoneElsesCertificate() {
        assertThatThrownBy(() -> service.issueFor(PATIENT_ID, user("sipho", "ROLE_PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).saveIssue(any());
    }

    @Test
    void unknownRoleIsDenied() {
        assertThatThrownBy(() -> service.issueFor(PATIENT_ID, user("x", "ROLE_GUEST")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownPatientIsNotFound() {
        when(repository.findPatient(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.issueFor(999, user("nurse", "ROLE_WORKER")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void patientWithNoDosesCannotGetCertificate() {
        when(repository.findDoses(PATIENT_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.issueFor(PATIENT_ID, user("nurse", "ROLE_WORKER")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no vaccination records");
    }

    @Test
    void sameRecordsReuseExistingCertificateId() {
        String hash = CertificateService.snapshotHash(PATIENT_ID, doses);
        when(repository.findLatestIssue(PATIENT_ID)).thenReturn(Optional.of(
                new IssueRecord("IC-20261001-AAAAAAAA", PATIENT_ID, LocalDateTime.of(2026, 10, 1, 9, 0), 2, 1, hash)));

        IssuedCertificate cert = service.issueFor(PATIENT_ID, user("nurse", "ROLE_WORKER"));

        assertThat(cert.certificateId()).isEqualTo("IC-20261001-AAAAAAAA");
        verify(repository, never()).saveIssue(any());
    }

    @Test
    void changedRecordsGetANewCertificateId() {
        when(repository.findLatestIssue(PATIENT_ID)).thenReturn(Optional.of(
                new IssueRecord("IC-20261001-AAAAAAAA", PATIENT_ID, LocalDateTime.of(2026, 10, 1, 9, 0), 2, 1,
                        "0".repeat(64))));

        IssuedCertificate cert = service.issueFor(PATIENT_ID, user("nurse", "ROLE_WORKER"));

        assertThat(cert.certificateId()).isNotEqualTo("IC-20261001-AAAAAAAA");
        verify(repository).saveIssue(any(IssueRecord.class));
    }

    // ---------- verifying ----------

    private IssueRecord issueWithHash(String hash) {
        return new IssueRecord("IC-20261006-AB12CD34", PATIENT_ID, LocalDateTime.of(2026, 10, 6, 9, 0), 2, 1, hash);
    }

    @Test
    void verifyValidCertificateShowsOnlyMaskedName() {
        when(repository.findIssue("IC-20261006-AB12CD34"))
                .thenReturn(Optional.of(issueWithHash(CertificateService.snapshotHash(PATIENT_ID, doses))));

        VerificationResult result = service.verify("IC-20261006-AB12CD34");

        assertThat(result.status()).isEqualTo(VerificationStatus.VALID);
        assertThat(result.maskedName()).isEqualTo("T**** M******");
        assertThat(result.doseCount()).isEqualTo(1);
    }

    @Test
    void verifyReportsSupersededWhenRecordsChanged() {
        when(repository.findIssue("IC-20261006-AB12CD34")).thenReturn(Optional.of(issueWithHash("0".repeat(64))));
        assertThat(service.verify("IC-20261006-AB12CD34").status()).isEqualTo(VerificationStatus.SUPERSEDED);
    }

    @Test
    void verifyUnknownOrMalformedIdIsNotFoundWithoutTouchingTheDatabase() {
        when(repository.findIssue("IC-20261006-00000000")).thenReturn(Optional.empty());
        assertThat(service.verify("IC-20261006-00000000").status()).isEqualTo(VerificationStatus.NOT_FOUND);
        assertThat(service.verify("'; DROP TABLE users;--").status()).isEqualTo(VerificationStatus.NOT_FOUND);
        assertThat(service.verify(null).status()).isEqualTo(VerificationStatus.NOT_FOUND);
        verify(repository, never()).findIssue("'; DROP TABLE users;--");
    }

    @Test
    void snapshotHashChangesWhenAnyDoseDetailChanges() {
        String original = CertificateService.snapshotHash(PATIENT_ID, doses);
        List<DoseLine> edited = List.of(new DoseLine(100, "MMR", 1, LocalDate.of(2026, 10, 5), "B-OTHER", 2));
        assertThat(CertificateService.snapshotHash(PATIENT_ID, edited)).isNotEqualTo(original);
        assertThat(CertificateService.snapshotHash(PATIENT_ID, doses)).isEqualTo(original);
    }
}
