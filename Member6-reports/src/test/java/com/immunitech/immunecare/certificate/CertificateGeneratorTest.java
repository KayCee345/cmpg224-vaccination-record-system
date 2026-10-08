package com.immunitech.immunecare.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import com.immunitech.immunecare.certificate.CertificateModels.CertificateData;
import com.immunitech.immunecare.certificate.CertificateModels.DoseLine;
import com.immunitech.immunecare.certificate.CertificateModels.PatientIdentity;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificateGeneratorTest {

    private final CertificateGenerator generator = new CertificateGenerator();

    private static CertificateData sample(int doseCount) {
        List<DoseLine> doses = new java.util.ArrayList<>();
        for (int i = 1; i <= doseCount; i++) {
            doses.add(new DoseLine(i, "MMR", i, LocalDate.of(2026, 1, 1).plusDays(i), "B-" + i, 2));
        }
        return new CertificateData("IC-20261006-AB12CD34", "ImmuneCare Clinic",
                "http://localhost:8080/verify/IC-20261006-AB12CD34", LocalDate.of(2026, 10, 6),
                new PatientIdentity(10, "Thandi Mokoena", "2601105000081", LocalDate.of(2026, 1, 10), "Female"),
                doses);
    }

    @Test
    void producesAValidPdf() throws IOException {
        byte[] pdf = generator.generate(sample(2));
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void containsPatientSummaryCertificateIdAndWatermark() throws IOException {
        byte[] pdf = generator.generate(sample(2));
        try (PdfReader reader = new PdfReader(pdf)) {
            String page1 = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(page1).contains("Thandi Mokoena");
            assertThat(page1).contains("2601105000081");
            assertThat(page1).contains("IC-20261006-AB12CD34");
            assertThat(page1).contains("B-1").contains("B-2");
            assertThat(page1).containsIgnoringCase("IMMUNECARE CLINIC"); // watermark text
        }
    }

    @Test
    void longHistoryFlowsOntoMorePagesAndKeepsWatermarkOnEach() throws IOException {
        byte[] pdf = generator.generate(sample(60));
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                assertThat(new PdfTextExtractor(reader).getTextFromPage(p)).containsIgnoringCase("IMMUNECARE CLINIC");
            }
        }
    }
}
