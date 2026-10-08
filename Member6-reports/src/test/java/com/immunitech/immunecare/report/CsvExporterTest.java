package com.immunitech.immunecare.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExporterTest {

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void writesBomHeaderAndRowsWithCrlf() {
        String csv = text(CsvExporter.toBytes(List.of("A", "B"), List.of(List.of("1", "2"))));
        assertThat(csv).isEqualTo("\uFEFFA,B\r\n1,2\r\n");
    }

    @Test
    void headerOnlyWhenThereAreNoRows() {
        String csv = text(CsvExporter.toBytes(List.of("A", "B"), List.of()));
        assertThat(csv).isEqualTo("\uFEFFA,B\r\n");
    }

    @Test
    void quotesFieldsWithCommasQuotesAndNewlines() {
        assertThat(CsvExporter.escape("Smith, John")).isEqualTo("\"Smith, John\"");
        assertThat(CsvExporter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvExporter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void nullBecomesEmptyField() {
        assertThat(CsvExporter.escape(null)).isEmpty();
        String csv = text(CsvExporter.toBytes(List.of("A", "B"), List.of(Arrays.asList("x", null))));
        assertThat(csv).endsWith("x,\r\n");
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(CsvExporter.escape("=SUM(A1:A9)")).isEqualTo("'=SUM(A1:A9)");
        assertThat(CsvExporter.escape("+1+1")).isEqualTo("'+1+1");
        assertThat(CsvExporter.escape("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvExporter.escape("-2+3")).isEqualTo("'-2+3");
    }

    @Test
    void leavesNormalTextAndDatesUntouched() {
        assertThat(CsvExporter.escape("MMR")).isEqualTo("MMR");
        assertThat(CsvExporter.escape("2026-10-06")).isEqualTo("2026-10-06");
    }
}
