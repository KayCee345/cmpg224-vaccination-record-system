package com.immunitech.immunecare.report;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/**
 * Writes to audit_log (FR05) when someone exports patient-derived data or issues a certificate.
 * If the group's AuditService already exists, replace the body of record() with a call to it.
 */
@Component
public class ReportAuditWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReportAuditWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(String username, String action) {
        Long userId = jdbc.query("SELECT user_id FROM users WHERE username = ?",
                (ResultSetExtractor<Long>) rs -> rs.next() ? rs.getLong(1) : null, username);
        if (userId == null) {
            throw new IllegalStateException("Cannot write audit entry: unknown user '" + username + "'");
        }
        String trimmed = action.length() > 255 ? action.substring(0, 255) : action;
        jdbc.update("INSERT INTO audit_log (user_id, action, action_date) VALUES (?, ?, ?)",
                userId, trimmed, Timestamp.valueOf(LocalDateTime.now(clock)));
    }
}
