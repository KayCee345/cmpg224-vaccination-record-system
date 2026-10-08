package com.immunitech.immunecare.certificate;

import com.immunitech.immunecare.certificate.CertificateModels.CertificateData;
import com.immunitech.immunecare.certificate.CertificateModels.DoseLine;
import com.immunitech.immunecare.certificate.CertificateModels.IssueRecord;
import com.immunitech.immunecare.certificate.CertificateModels.IssuedCertificate;
import com.immunitech.immunecare.certificate.CertificateModels.PatientIdentity;
import com.immunitech.immunecare.certificate.CertificateModels.VerificationResult;
import com.immunitech.immunecare.certificate.CertificateModels.VerificationStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues (FR18) and verifies vaccination certificates. */
@Service
public class CertificateService {

    static final Pattern ID_PATTERN = Pattern.compile("^IC-\\d{8}-[0-9A-F]{8}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository repository;
    private final CertificateGenerator generator;
    private final Clock clock;
    private final String clinicName;
    private final String publicBaseUrl;

    public CertificateService(CertificateRepository repository, CertificateGenerator generator, Clock clock,
                              @Value("${immunecare.clinic-name:ImmuneCare Clinic}") String clinicName,
                              @Value("${immunecare.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.repository = repository;
        this.generator = generator;
        this.clock = clock;
        this.clinicName = clinicName;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Transactional
    public IssuedCertificate issueFor(long patientId, Authentication auth) {
        authorise(patientId, auth);

        PatientIdentity patient = repository.findPatient(patientId)
                .orElseThrow(() -> new NoSuchElementException("Patient " + patientId + " was not found."));
        List<DoseLine> doses = repository.findDoses(patientId);
        if (doses.isEmpty()) {
            throw new IllegalStateException(
                    "This patient has no vaccination records yet, so there is nothing to certify.");
        }

        String hash = snapshotHash(patientId, doses);
        // Same records as the last certificate -> reuse it, so re-downloading does not create clutter.
        Optional<IssueRecord> reusable = repository.findLatestIssue(patientId)
                .filter(i -> i.snapshotHash().equals(hash));
        IssueRecord issue = reusable.orElseGet(() -> createIssue(patientId, doses.size(), hash, auth));

        CertificateData data = new CertificateData(issue.certificateId(), clinicName,
                publicBaseUrl + "/verify/" + issue.certificateId(), issue.issuedAt().toLocalDate(), patient, doses);
        return new IssuedCertificate(issue.certificateId(), generator.generate(data));
    }

    /** Public check used by the /verify page. Returns only masked, minimal information (POPIA). */
    public VerificationResult verify(String certificateId) {
        if (certificateId == null || !ID_PATTERN.matcher(certificateId).matches()) {
            return VerificationResult.notFound();
        }
        Optional<IssueRecord> issue = repository.findIssue(certificateId);
        if (issue.isEmpty()) {
            return VerificationResult.notFound();
        }
        Optional<PatientIdentity> patient = repository.findPatient(issue.get().patientId());
        if (patient.isEmpty()) {
            return VerificationResult.notFound();
        }
        List<DoseLine> doses = repository.findDoses(issue.get().patientId());
        boolean unchanged = snapshotHash(issue.get().patientId(), doses).equals(issue.get().snapshotHash());
        return new VerificationResult(
                unchanged ? VerificationStatus.VALID : VerificationStatus.SUPERSEDED,
                maskName(patient.get().fullName()),
                issue.get().issuedAt().toLocalDate(),
                issue.get().doseCount());
    }

    // ---------- helpers ----------

    private IssueRecord createIssue(long patientId, int doseCount, String hash, Authentication auth) {
        long issuedBy = repository.findUserId(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found: " + auth.getName()));
        LocalDateTime now = LocalDateTime.now(clock);
        String id = "IC-" + now.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + randomHex(4);
        IssueRecord issue = new IssueRecord(id, patientId, now, issuedBy, doseCount, hash);
        repository.saveIssue(issue);
        return issue;
    }

    /** Admins and healthcare workers may issue for anyone; a patient only for their own profile. */
    private void authorise(long patientId, Authentication auth) {
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_WORKER")) {
            return;
        }
        if (roles.contains("ROLE_PATIENT")) {
            Optional<Long> userId = repository.findUserId(auth.getName());
            if (userId.isPresent() && repository.patientBelongsToUser(patientId, userId.get())) {
                return;
            }
        }
        throw new AccessDeniedException("You can only download your own vaccination certificate.");
    }

    static String snapshotHash(long patientId, List<DoseLine> doses) {
        StringBuilder sb = new StringBuilder("patient:").append(patientId);
        doses.stream()
                .sorted(java.util.Comparator.comparingLong(DoseLine::recordId))
                .forEach(d -> sb.append('\n')
                        .append(d.recordId()).append('|').append(d.vaccineName()).append('|')
                        .append(d.doseNumber()).append('|').append(d.dateAdministered()).append('|')
                        .append(d.batchNumber()).append('|').append(d.workerId()));
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(fullName.trim().split("\\s+"))
                .map(part -> part.charAt(0) + "*".repeat(Math.max(part.length() - 1, 0)))
                .collect(Collectors.joining(" "));
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().withUpperCase().formatHex(b);
    }
}
