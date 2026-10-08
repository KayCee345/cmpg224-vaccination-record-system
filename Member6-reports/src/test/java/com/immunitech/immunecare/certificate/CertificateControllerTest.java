package com.immunitech.immunecare.certificate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.immunitech.immunecare.certificate.CertificateModels.IssuedCertificate;
import com.immunitech.immunecare.certificate.CertificateModels.VerificationResult;
import com.immunitech.immunecare.certificate.CertificateModels.VerificationStatus;
import com.immunitech.immunecare.report.ReportAuditWriter;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CertificateController.class)
@Import(CertificateControllerTest.TestSecurity.class)
class CertificateControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a
                            .requestMatchers("/verify/**").permitAll()
                            .anyRequest().authenticated())
                    .formLogin(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    MockMvc mvc;
    @MockBean
    CertificateService service;
    @MockBean
    ReportAuditWriter audit;

    @Test
    void anonymousCannotDownloadCertificate() throws Exception {
        mvc.perform(get("/certificates/10")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "nurse", roles = "WORKER")
    void downloadReturnsPdfAttachment() throws Exception {
        byte[] pdf = "%PDF-fake".getBytes();
        when(service.issueFor(eq(10L), any())).thenReturn(new IssuedCertificate("IC-20261006-AB12CD34", pdf));

        mvc.perform(get("/certificates/10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"IC-20261006-AB12CD34.pdf\""))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @WithMockUser(username = "sipho", roles = "PATIENT")
    void patientGetsForbiddenForSomeoneElsesCertificate() throws Exception {
        when(service.issueFor(eq(10L), any())).thenThrow(new AccessDeniedException("no"));
        mvc.perform(get("/certificates/10")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "nurse", roles = "WORKER")
    void unknownPatientGives404AndPatientWithoutDosesGives422() throws Exception {
        when(service.issueFor(eq(999L), any())).thenThrow(new NoSuchElementException("Patient 999 was not found."));
        when(service.issueFor(eq(11L), any())).thenThrow(new IllegalStateException("no records"));

        mvc.perform(get("/certificates/999")).andExpect(status().isNotFound());
        mvc.perform(get("/certificates/11")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void verifyPageIsPublic() throws Exception {
        when(service.verify("IC-20261006-AB12CD34")).thenReturn(
                new VerificationResult(VerificationStatus.VALID, "T**** M******", LocalDate.of(2026, 10, 6), 1));

        mvc.perform(get("/verify/IC-20261006-AB12CD34"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify"));
    }
}
