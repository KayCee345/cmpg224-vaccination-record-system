package com.immunitech.immunecare.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.immunitech.immunecare.report.ReportModels.AdministeredDose;
import com.immunitech.immunecare.report.ReportModels.StockBatch;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/** Runs the real SQL against an in-memory H2 database. */
@JdbcTest
@Import(ReportRepository.class)
@Sql({"/member6/test-schema.sql", "/member6/test-data.sql"})
class ReportRepositoryTest {

    @Autowired
    ReportRepository repository;

    @Test
    void findsOnlyDosesFromTheRequestedDay() {
        List<AdministeredDose> doses = repository.findDosesAdministeredOn(LocalDate.of(2026, 10, 5));
        assertThat(doses).extracting(AdministeredDose::vaccineName).containsExactlyInAnyOrder("MMR", "MMR");
        assertThat(doses).extracting(AdministeredDose::dateOfBirth)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 1, 10), LocalDate.of(1990, 3, 1));
    }

    @Test
    void dayWithNoVaccinationsReturnsEmptyList() {
        assertThat(repository.findDosesAdministeredOn(LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @Test
    void readsBatchesIncludingNullManufactureDate() {
        List<StockBatch> batches = repository.findAllBatches();
        assertThat(batches).hasSize(2);
        assertThat(batches).filteredOn(b -> b.vaccineName().equals("Polio")).singleElement()
                .satisfies(b -> {
                    assertThat(b.manufactureDate()).isNull();
                    assertThat(b.quantity()).isEqualTo(5);
                    assertThat(b.expiryDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                });
    }
}
