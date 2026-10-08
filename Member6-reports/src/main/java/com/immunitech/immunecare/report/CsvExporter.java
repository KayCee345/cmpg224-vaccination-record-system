package com.immunitech.immunecare.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds RFC 4180 CSV files (FR17).
 *
 * <ul>
 *   <li>Fields containing commas, quotes or line breaks are quoted; quotes are doubled.</li>
 *   <li>Text cells starting with = + - @ are prefixed with an apostrophe so Excel cannot run them as
 *       formulas (CSV injection). Do not pass negative numbers through this exporter.</li>
 *   <li>A UTF-8 byte-order mark is written so Excel shows names with accents correctly.</li>
 * </ul>
 */
public final class CsvExporter {

    private static final String NEWLINE = "\r\n";
    private static final String BOM = "\uFEFF";

    private CsvExporter() {
    }

    public static byte[] toBytes(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        appendRow(sb, headers);
        for (List<String> row : rows) {
            appendRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append(NEWLINE);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String s = value;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        boolean needsQuotes = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (needsQuotes) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
