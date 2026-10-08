package com.immunitech.immunecare.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import com.immunitech.immunecare.certificate.CertificateModels.DoseLine;
import com.immunitech.immunecare.certificate.CertificateModels.IssueRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@Import(CertificateRepository.class)
@Sql({"/member6/test-schema.sql", "/member6/test-data.sql"})
class CertificateRepositoryTest {

    @Autowired
    CertificateRepository repository;

    @Test
    void dosesAreReturnedOldestFirst() {
        List<DoseLine> doses = repository.findDoses(11);
        assertThat(doses).extracting(DoseLine::dateAdministered)
                .containsExactly(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5));
        assertThat(doses.get(0).vaccineName()).isEqualTo("Polio");
    }

    @Test
    void unknownPatientHasNoProfile() {
        assertThat(repository.findPatient(999)).isEmpty();
    }

    @Test
    void patientOwnershipCheck() {
        assertThat(repository.patientBelongsToUser(10, 3)).isTrue();   // Thandi owns patient 10
        assertThat(repository.patientBelongsToUser(10, 4)).isFalse();  // Sipho does not
    }

    @Test
    void issueCanBeSavedAndFound() {
        IssueRecord issue = new IssueRecord("IC-20261006-AB12CD34", 10, LocalDateTime.of(2026, 10, 6, 9, 0),
                2, 1, "a".repeat(64));
        repository.saveIssue(issue);

        assertThat(repository.findIssue("IC-20261006-AB12CD34")).contains(issue);
        assertThat(repository.findLatestIssue(10)).contains(issue);
        assertThat(repository.findIssue("IC-20261006-00000000")).isEmpty();
    }
}
