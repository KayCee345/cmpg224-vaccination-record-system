package com.immunitech.immunecare.certificate;

import com.immunitech.immunecare.certificate.CertificateModels.IssuedCertificate;
import com.immunitech.immunecare.report.ReportAuditWriter;
import java.util.NoSuchElementException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class CertificateController {

    private final CertificateService service;
    private final ReportAuditWriter audit;

    public CertificateController(CertificateService service, ReportAuditWriter audit) {
        this.service = service;
        this.audit = audit;
    }

    /** Download the PDF. Ownership/role rules are enforced inside the service. */
    @GetMapping("/certificates/{patientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable long patientId, Authentication authentication) {
        IssuedCertificate cert = service.issueFor(patientId, authentication);
        audit.record(authentication.getName(), "ISSUE_CERTIFICATE " + cert.certificateId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(cert.certificateId() + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(cert.pdf());
    }

    /** Public page (no login): anyone holding a certificate can check it. Must be permitAll in SecurityConfig. */
    @GetMapping("/verify/{certificateId}")
    public String verify(@PathVariable String certificateId, Model model) {
        model.addAttribute("certificateId", certificateId);
        model.addAttribute("result", service.verify(certificateId));
        return "verify";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> unprocessable(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }
}
