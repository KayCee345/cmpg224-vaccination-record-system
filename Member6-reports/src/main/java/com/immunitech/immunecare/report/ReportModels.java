package com.immunitech.immunecare.report;

import java.time.LocalDate;
import java.util.List;

/** Plain data carriers for the reports module. */
public final class ReportModels {

    private ReportModels() {
    }

    /** One administered dose, as read from the database (only what the report needs). */
    public record AdministeredDose(String vaccineName, LocalDate dateOfBirth) {
    }

    /** One vaccine batch, as read from the database. */
    public record StockBatch(String vaccineName, String batchNumber, int quantity, String manufacturer,
                             LocalDate manufactureDate, LocalDate expiryDate) {
    }

    public record DailySummaryRow(String vaccineName, AgeGroup ageGroup, long total) {
    }

    public record DailySummaryReport(LocalDate date, long totalVaccinations, List<DailySummaryRow> rows) {
    }

    public enum BatchStatus { OK, EXPIRING_SOON, EXPIRED }

    public record InventoryReportRow(String vaccineName, String batchNumber, int quantity, String manufacturer,
                                     LocalDate manufactureDate, LocalDate expiryDate, BatchStatus status,
                                     boolean vaccineLowStock) {
    }
}
