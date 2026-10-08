package com.immunitech.immunecare.report;

import com.immunitech.immunecare.report.ReportModels.AdministeredDose;
import com.immunitech.immunecare.report.ReportModels.StockBatch;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries for reporting. Uses JdbcTemplate so the reports module does not depend on the
 * other members' JPA entity classes - only on the agreed table and column names.
 */
@Repository
public class ReportRepository {

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdministeredDose> findDosesAdministeredOn(LocalDate date) {
        String sql = """
                SELECT v.name AS vaccine_name, p.date_of_birth AS date_of_birth
                FROM vaccination_records vr
                JOIN vaccines v ON v.vaccine_id = vr.vaccine_id
                JOIN patients p ON p.patient_id = vr.patient_id
                WHERE vr.date_administered = ?
                """;
        return jdbc.query(sql,
                (rs, rowNum) -> new AdministeredDose(
                        rs.getString("vaccine_name"),
                        rs.getDate("date_of_birth").toLocalDate()),
                Date.valueOf(date));
    }

    public List<StockBatch> findAllBatches() {
        String sql = """
                SELECT v.name AS vaccine_name, b.batch_number, b.quantity, b.manufacturer,
                       b.manufacture_date, b.expiry_date
                FROM vaccine_batches b
                JOIN vaccines v ON v.vaccine_id = b.vaccine_id
                ORDER BY v.name, b.expiry_date, b.batch_number
                """;
        return jdbc.query(sql, (rs, rowNum) -> new StockBatch(
                rs.getString("vaccine_name"),
                rs.getString("batch_number"),
                rs.getInt("quantity"),
                rs.getString("manufacturer"),
                toLocalDate(rs.getDate("manufacture_date")),
                toLocalDate(rs.getDate("expiry_date"))));
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
