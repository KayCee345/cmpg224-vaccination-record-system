package com.immunitech.immunecare.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.immunitech.immunecare.report.ReportModels.DailySummaryReport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@Import(ReportControllerTest.TestSecurity.class)
class ReportControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).formLogin(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    MockMvc mvc;
    @MockBean
    ReportService reportService;
    @MockBean
    ReportAuditWriter audit;

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/reports/daily/export.csv").param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "pat", roles = "PATIENT")
    void patientCannotSeeReportsOrExport() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().isForbidden());
        mvc.perform(get("/reports/daily/export.csv").param("date", "2026-10-05")).andExpect(status().isForbidden());
        mvc.perform(get("/reports/inventory/export.csv")).andExpect(status().isForbidden());
        verify(audit, never()).record(any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminSeesReportsPage() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(reportService.today()).thenReturn(day);
        when(reportService.dailySummary(day)).thenReturn(new DailySummaryReport(day, 0, List.of()));

        mvc.perform(get("/reports")).andExpect(status().isOk()).andExpect(view().name("reports"));
    }

    @Test
    @WithMockUser(username = "nurse", roles = "WORKER")
    void workerCanDownloadDailyCsvAndExportIsAudited() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(reportService.dailySummaryCsv(day)).thenReturn("a,b\r\n".getBytes());

        mvc.perform(get("/reports/daily/export.csv").param("date", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"vaccination-daily-summary-2026-10-05.csv\""))
                .andExpect(content().bytes("a,b\r\n".getBytes()));

        verify(audit).record("nurse", "EXPORT_DAILY_SUMMARY_CSV 2026-10-05");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void futureDateGivesBadRequest() throws Exception {
        when(reportService.dailySummaryCsv(LocalDate.of(2099, 1, 1)))
                .thenThrow(new IllegalArgumentException("The report date cannot be in the future."));

        mvc.perform(get("/reports/daily/export.csv").param("date", "2099-01-01"))
                .andExpect(status().isBadRequest());
    }
}
