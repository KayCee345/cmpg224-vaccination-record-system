package com.immunitech.immunecare.report;

import com.immunitech.immunecare.report.ReportModels.AdministeredDose;
import com.immunitech.immunecare.report.ReportModels.BatchStatus;
import com.immunitech.immunecare.report.ReportModels.DailySummaryReport;
import com.immunitech.immunecare.report.ReportModels.DailySummaryRow;
import com.immunitech.immunecare.report.ReportModels.InventoryReportRow;
import com.immunitech.immunecare.report.ReportModels.StockBatch;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Report logic: FR16 (daily summary), FR17 (CSV export) and the inventory report that FR17 also covers. */
@Service
public class ReportService {

    /** FR15: warn when total stock of a vaccine drops below this many units. */
    public static final int LOW_STOCK_THRESHOLD = 20;
    /** FR14: alert when a batch is this many days (or fewer) from expiry. */
    public static final int EXPIRY_WARNING_DAYS = 30;

    private final ReportRepository repository;
    private final Clock clock;

    public ReportService(ReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------- FR16: daily summary ----------

    private record GroupKey(String vaccineName, AgeGroup ageGroup) {
    }

    public DailySummaryReport dailySummary(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("A report date is required.");
        }
        if (date.isAfter(today())) {
            throw new IllegalArgumentException("The report date cannot be in the future.");
        }

        List<AdministeredDose> doses = repository.findDosesAdministeredOn(date);

        Map<GroupKey, Long> counts = doses.stream().collect(Collectors.groupingBy(
                d -> new GroupKey(d.vaccineName(), AgeGroup.forDates(d.dateOfBirth(), date)),
                Collectors.counting()));

        List<DailySummaryRow> rows = counts.entrySet().stream()
                .map(e -> new DailySummaryRow(e.getKey().vaccineName(), e.getKey().ageGroup(), e.getValue()))
                .sorted(Comparator.comparing(DailySummaryRow::vaccineName)
                        .thenComparing(DailySummaryRow::ageGroup))
                .toList();

        return new DailySummaryReport(date, doses.size(), rows);
    }

    // ---------- FR14 / FR15: inventory status (also exported by FR17) ----------

    public List<InventoryReportRow> inventoryReport() {
        LocalDate today = today();
        List<StockBatch> batches = repository.findAllBatches();

        // Expired stock cannot be used, so it does not count towards the low-stock total.
        Map<String, Integer> usableStock = batches.stream()
                .filter(b -> !b.expiryDate().isBefore(today))
                .collect(Collectors.groupingBy(StockBatch::vaccineName,
                        Collectors.summingInt(StockBatch::quantity)));

        Function<StockBatch, Boolean> lowStock =
                b -> usableStock.getOrDefault(b.vaccineName(), 0) < LOW_STOCK_THRESHOLD;

        return batches.stream()
                .map(b -> new InventoryReportRow(
                        b.vaccineName(), b.batchNumber(), b.quantity(), b.manufacturer(),
                        b.manufactureDate(), b.expiryDate(), statusOf(b.expiryDate(), today), lowStock.apply(b)))
                .toList();
    }

    static BatchStatus statusOf(LocalDate expiryDate, LocalDate today) {
        if (expiryDate.isBefore(today)) {
            return BatchStatus.EXPIRED;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, expiryDate);
        return daysLeft <= EXPIRY_WARNING_DAYS ? BatchStatus.EXPIRING_SOON : BatchStatus.OK;
    }

    // ---------- FR17: CSV export ----------

    public byte[] dailySummaryCsv(LocalDate date) {
        DailySummaryReport report = dailySummary(date);
        List<List<String>> rows = report.rows().stream()
                .map(r -> List.of(report.date().toString(), r.vaccineName(), r.ageGroup().label(),
                        String.valueOf(r.total())))
                .toList();
        return CsvExporter.toBytes(List.of("Date", "Vaccine", "Age group", "Doses administered"), rows);
    }

    public byte[] inventoryCsv() {
        List<List<String>> rows = inventoryReport().stream()
                .map(r -> List.of(
                        r.vaccineName(), r.batchNumber(), String.valueOf(r.quantity()), r.manufacturer(),
                        r.manufactureDate() == null ? "" : r.manufactureDate().toString(),
                        r.expiryDate().toString(), r.status().name(), r.vaccineLowStock() ? "YES" : "NO"))
                .toList();
        return CsvExporter.toBytes(
                List.of("Vaccine", "Batch number", "Quantity", "Manufacturer", "Manufacture date",
                        "Expiry date", "Batch status", "Vaccine low stock"),
                rows);
    }
}
