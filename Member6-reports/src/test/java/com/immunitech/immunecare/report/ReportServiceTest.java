package com.immunitech.immunecare.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.immunitech.immunecare.report.ReportModels.AdministeredDose;
import com.immunitech.immunecare.report.ReportModels.BatchStatus;
import com.immunitech.immunecare.report.ReportModels.DailySummaryReport;
import com.immunitech.immunecare.report.ReportModels.InventoryReportRow;
import com.immunitech.immunecare.report.ReportModels.StockBatch;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private ReportRepository repository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T08:00:00Z"), ZoneOffset.UTC);
        service = new ReportService(repository, clock);
    }

    // ---------- FR16 ----------

    @Test
    void dailySummaryGroupsByVaccineAndAgeGroupAndSorts() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(repository.findDosesAdministeredOn(day)).thenReturn(List.of(
                new AdministeredDose("MMR", LocalDate.of(2026, 1, 10)),   // under 1
                new AdministeredDose("MMR", LocalDate.of(2026, 3, 1)),    // under 1
                new AdministeredDose("MMR", LocalDate.of(1990, 3, 1)),    // 18-49
                new AdministeredDose("Polio", LocalDate.of(2022, 5, 5)))); // 1-4

        DailySummaryReport report = service.dailySummary(day);

        assertThat(report.totalVaccinations()).isEqualTo(4);
        assertThat(report.rows()).extracting("vaccineName", "ageGroup", "total").containsExactly(
                org.assertj.core.groups.Tuple.tuple("MMR", AgeGroup.UNDER_1, 2L),
                org.assertj.core.groups.Tuple.tuple("MMR", AgeGroup.AGE_18_49, 1L),
                org.assertj.core.groups.Tuple.tuple("Polio", AgeGroup.AGE_1_4, 1L));
    }

    @Test
    void dailySummaryTotalMatchesSumOfRows() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(repository.findDosesAdministeredOn(day)).thenReturn(List.of(
                new AdministeredDose("A", LocalDate.of(2000, 1, 1)),
                new AdministeredDose("B", LocalDate.of(2001, 1, 1)),
                new AdministeredDose("A", LocalDate.of(1950, 1, 1))));

        DailySummaryReport report = service.dailySummary(day);

        assertThat(report.rows().stream().mapToLong(r -> r.total()).sum()).isEqualTo(report.totalVaccinations());
    }

    @Test
    void emptyDayGivesEmptyReport() {
        when(repository.findDosesAdministeredOn(TODAY)).thenReturn(List.of());
        DailySummaryReport report = service.dailySummary(TODAY);
        assertThat(report.totalVaccinations()).isZero();
        assertThat(report.rows()).isEmpty();
    }

    @Test
    void futureOrMissingDateRejected() {
        assertThatThrownBy(() -> service.dailySummary(TODAY.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.dailySummary(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dailySummaryCsvHasHeaderAndLabelledAgeGroups() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(repository.findDosesAdministeredOn(day))
                .thenReturn(List.of(new AdministeredDose("MMR", LocalDate.of(2026, 1, 10))));

        String csv = new String(service.dailySummaryCsv(day), StandardCharsets.UTF_8);

        assertThat(csv).contains("Date,Vaccine,Age group,Doses administered\r\n");
        assertThat(csv).contains("2026-10-05,MMR,Under 1 year,1\r\n");
    }

    // ---------- FR14 / FR15 ----------

    private static StockBatch batch(String vaccine, String no, int qty, LocalDate expiry) {
        return new StockBatch(vaccine, no, qty, "Maker", LocalDate.of(2026, 1, 1), expiry);
    }

    @Test
    void expiryWarningStartsExactlyThirtyDaysOut() {
        when(repository.findAllBatches()).thenReturn(List.of(
                batch("MMR", "EXPIRED", 50, TODAY.minusDays(1)),
                batch("MMR", "TODAY", 50, TODAY),
                batch("MMR", "D30", 50, TODAY.plusDays(30)),
                batch("MMR", "D31", 50, TODAY.plusDays(31))));

        List<InventoryReportRow> rows = service.inventoryReport();

        assertThat(rows).extracting(InventoryReportRow::batchNumber, InventoryReportRow::status).containsExactly(
                org.assertj.core.groups.Tuple.tuple("EXPIRED", BatchStatus.EXPIRED),
                org.assertj.core.groups.Tuple.tuple("TODAY", BatchStatus.EXPIRING_SOON),
                org.assertj.core.groups.Tuple.tuple("D30", BatchStatus.EXPIRING_SOON),
                org.assertj.core.groups.Tuple.tuple("D31", BatchStatus.OK));
    }

    @Test
    void lowStockWhenTotalBelowTwenty() {
        when(repository.findAllBatches()).thenReturn(List.of(
                batch("Polio", "P1", 10, TODAY.plusDays(100)),
                batch("Polio", "P2", 9, TODAY.plusDays(200)),      // total 19 -> low
                batch("MMR", "M1", 12, TODAY.plusDays(100)),
                batch("MMR", "M2", 8, TODAY.plusDays(200))));      // total 20 -> not low

        List<InventoryReportRow> rows = service.inventoryReport();

        assertThat(rows).filteredOn(r -> r.vaccineName().equals("Polio")).allMatch(InventoryReportRow::vaccineLowStock);
        assertThat(rows).filteredOn(r -> r.vaccineName().equals("MMR")).noneMatch(InventoryReportRow::vaccineLowStock);
    }

    @Test
    void expiredStockDoesNotCountTowardsStockLevel() {
        when(repository.findAllBatches()).thenReturn(List.of(
                batch("MMR", "OLD", 100, TODAY.minusDays(5)),
                batch("MMR", "NEW", 5, TODAY.plusDays(90))));

        assertThat(service.inventoryReport()).allMatch(InventoryReportRow::vaccineLowStock);
    }

    @Test
    void inventoryCsvContainsStatusAndLowStockColumns() {
        when(repository.findAllBatches()).thenReturn(List.of(batch("MMR", "M1", 5, TODAY.plusDays(10))));

        String csv = new String(service.inventoryCsv(), StandardCharsets.UTF_8);

        assertThat(csv).contains("Vaccine,Batch number,Quantity,Manufacturer,Manufacture date,Expiry date,"
                + "Batch status,Vaccine low stock\r\n");
        assertThat(csv).contains("MMR,M1,5,Maker,2026-01-01,2026-10-16,EXPIRING_SOON,YES\r\n");
    }
}
