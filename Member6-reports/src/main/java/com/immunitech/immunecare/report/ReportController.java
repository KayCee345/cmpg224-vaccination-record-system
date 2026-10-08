package com.immunitech.immunecare.report;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Reports screen and CSV downloads. Role check is enforced here on the server (NFR02), not just by
 * hiding the menu link. Requires @EnableMethodSecurity somewhere in the group's security config.
 */
@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ADMIN','WORKER')")
public class ReportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reportService;
    private final ReportAuditWriter audit;

    public ReportController(ReportService reportService, ReportAuditWriter audit) {
        this.reportService = reportService;
        this.audit = audit;
    }

    @GetMapping
    public String reportsPage(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        LocalDate day = date != null ? date : reportService.today();
        model.addAttribute("date", day);
        try {
            model.addAttribute("report", reportService.dailySummary(day));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "reports";
    }

    @GetMapping("/daily/export.csv")
    public ResponseEntity<byte[]> exportDaily(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Principal principal) {
        byte[] csv = reportService.dailySummaryCsv(date);
        audit.record(principal.getName(), "EXPORT_DAILY_SUMMARY_CSV " + date);
        return csvResponse(csv, "vaccination-daily-summary-" + date + ".csv");
    }

    @GetMapping("/inventory/export.csv")
    public ResponseEntity<byte[]> exportInventory(Principal principal) {
        byte[] csv = reportService.inventoryCsv();
        audit.record(principal.getName(), "EXPORT_INVENTORY_CSV");
        return csvResponse(csv, "vaccine-inventory-" + reportService.today() + ".csv");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    private static ResponseEntity<byte[]> csvResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
