# ImmuneCare – Member 6: Reports, Certificates, Testing and Integration

CMPG224 | Team 15 | ImmuniTech
Branch: `reports-testing`

## 1. Tech stack (from the SDD, sections 3.3, 5 and 8)

| Layer | Technology |
| --- | --- |
| Presentation | HTML5, CSS3, JavaScript, Tailwind CSS; server-rendered Thymeleaf pages (role-specific views) |
| Business logic | Java, Spring Boot (Spring MVC, Spring Security for role-based access) |
| Data access | Spring Data JPA (this module uses `JdbcTemplate` for read-only report queries) |
| Database | MySQL 8.0 |
| Architecture | Three-tier layered MVC: Controller -> Service -> Repository |
| Version control | Git / GitHub, one branch per member |
| Added by Member 6 | OpenPDF (PDF), ZXing (QR code), JUnit 5 + Mockito + AssertJ + Spring Security Test + H2 (tests) |

## 2. My scope (Development Plan, Member 6 and Phase 7-8)

| Requirement | What was built | Where |
| --- | --- | --- |
| FR16 Daily summary by vaccine type and age group | `ReportService.dailySummary()`, `AgeGroup` | `report/` |
| FR17 CSV export of vaccination and inventory reports | `CsvExporter`, `/reports/daily/export.csv`, `/reports/inventory/export.csv` | `report/` |
| FR18 Printable PDF certificate with verifiable summary and clinic watermark | `CertificateGenerator`, `CertificateService`, `/certificates/{patientId}`, `/verify/{id}` | `certificate/` |
| FR05 (partly) Audit of exports and certificate issuing | `ReportAuditWriter` | `report/` |
| FR14/FR15 status shown in the inventory export | `ReportService.inventoryReport()` (30-day expiry, stock below 20) | `report/` |
| Testing and integration | Automated tests in `src/test`, manual system tests in section 6, integration checklist in section 5 | |

## 3. Files and where they go

Copy `src/main/java/com/immunitech/immunecare/report` and `.../certificate` into the main project (keep the package names, or rename the base package consistently). Copy the two templates into `src/main/resources/templates`. Run `src/main/resources/db/member6-schema.sql` once on the MySQL database. Merge `pom-additions.xml` into `pom.xml`.

## 4. Things to agree with the other members BEFORE integrating

My queries rely on specific table and column names. The SDD table list and the SRS differ in a few places, so these must be confirmed (change the SQL in `ReportRepository` / `CertificateRepository` if the team decides otherwise).

| # | Assumption | Why it matters | Owner |
| --- | --- | --- | --- |
| 1 | `vaccination_records` has `vaccine_id` (FK to `vaccines`) and `batch_number` | The SDD table list shows only `patient_id` and `worker_id` as FKs, but FR07 needs vaccine type and batch. | Member 5 |
| 2 | `patients` has `full_name`, `national_id`, `gender`, `date_of_birth`, `user_id` | The SDD lists only `date_of_birth`, `contact_info`, `user_id`. FR06 and FR09 need name and national ID. | Member 5 |
| 3 | Stock table is `vaccine_batches` (`vaccine_id`, `batch_number`, `quantity`, `manufacturer`, `manufacture_date`, `expiry_date`) | The SDD traceability matrix uses `vaccine_batches`, but section 5.3 has no inventory table. | Member 5 |
| 4 | `users.username`, `audit_log(user_id, action, action_date)` | Used for ownership checks and audit entries. | Member 3 |
| 5 | Role authorities are `ROLE_ADMIN`, `ROLE_WORKER`, `ROLE_PATIENT` | The SRS FR02 says Administrator/Staff/Manager. The SDD enum is ADMIN/WORKER/PATIENT, so I followed the SDD. Reports are allowed for ADMIN and WORKER. | Member 3 |
| 6 | `/verify/**` is `permitAll()` in the security config | Verification must work without logging in (third parties check certificates). | Member 3 |
| 7 | `@EnableMethodSecurity` is on in the security config | Needed for `@PreAuthorize` in the controllers. | Member 3 |
| 8 | Optional properties: `immunecare.clinic-name`, `immunecare.public-base-url` | Watermark text and the URL inside the QR code. | Me |

## 5. Integration checklist (Phase 8)

1. `mvn clean test` passes on branch `reports-testing`.
2. Merge `main` into `reports-testing`, resolve conflicts, re-run tests.
3. Start the app against MySQL with Member 5's sample data.
4. Log in as each role and run the system tests in section 6.
5. Log every failure in the bug table (section 7) with the owner; retest after the fix.
6. Merge into `main` only when sections 5.1-5.4 pass.

## 6. System test cases (manual, run on the integrated system)

| ID | Req | Steps | Expected result | Result |
| --- | --- | --- | --- | --- |
| ST-01 | FR01 | Log in with valid admin credentials | Admin dashboard shown | |
| ST-02 | FR01 | Log in with a wrong password | Error shown, no session | |
| ST-03 | FR02 | Log in as each role | Each role reaches its own dashboard | |
| ST-04 | FR02/NFR02 | As a patient, open `/reports` by typing the address | 403 Forbidden | |
| ST-05 | FR02/NFR02 | As a patient, open `/certificates/<another patient's id>` | 403 Forbidden | |
| ST-06 | FR03 | Stay idle for 30 minutes, then click | Redirected to login | |
| ST-07 | FR06/FR09 | Register a patient, then register again with the same national ID | Second attempt rejected | |
| ST-08 | FR07 | Record a vaccination | Saved; appears in history | |
| ST-09 | FR13 | Record a vaccination for a batch with quantity 10 | Quantity becomes 9 | |
| ST-10 | FR16 | Record 3 doses today (different vaccines/ages), open `/reports` | Totals match what was recorded | |
| ST-11 | FR16 | Pick a date with no vaccinations | "No vaccinations were recorded" message | |
| ST-12 | FR16 | Pick a future date | Error: date cannot be in the future | |
| ST-13 | FR17 | Download the daily CSV, open in Excel | Opens, correct columns, totals match the screen | |
| ST-14 | FR17 | Download the inventory CSV | One row per batch, status and low-stock columns correct | |
| ST-15 | FR14 | Batch expiring in 30 days / 31 days | EXPIRING_SOON / OK | |
| ST-16 | FR15 | Vaccine with total stock 19 / 20 | Low stock YES / NO | |
| ST-17 | FR18 | As a worker, download a certificate for a vaccinated patient | PDF opens, patient details, all doses, watermark, QR code | |
| ST-18 | FR18 | Scan the QR code | Verify page says genuine and up to date, name masked | |
| ST-19 | FR18 | Record another dose, open the old certificate's verify link | Page says records have changed since issue | |
| ST-20 | FR18 | Request a certificate for a patient with no doses | Clear message, no PDF | |
| ST-21 | FR05 | After ST-13 and ST-17, check `audit_log` | Entries for the export and certificate | |
| ST-22 | NFR05 | Search/report with ~500 patient records loaded | Result within 3 seconds | |
| ST-23 | NFR04 | 20 users at once (e.g. JMeter or a simple script) | Pages stay responsive | |

## 7. Bug log template

| Bug ID | Found in test | Description | Module / owner | Severity | Status |
| --- | --- | --- | --- | --- | --- |
| B-01 | | | | | Open |

## 8. Automated tests included

| Test class | What it proves |
| --- | --- |
| `CsvExporterTest` | Quoting, null handling, CRLF, BOM, spreadsheet-formula neutralising |
| `AgeGroupTest` | Every age band boundary, birthday boundary |
| `ReportServiceTest` | Grouping and totals, empty day, future date rejected, expiry at exactly 30 days, low stock at 19 vs 20, expired stock excluded, CSV content |
| `ReportRepositoryTest` | The real SQL, against H2 |
| `ReportControllerTest` | Anonymous redirected, patient gets 403, admin/worker allowed, CSV headers, export audited |
| `CertificateGeneratorTest` | Valid PDF, patient details, certificate ID and watermark on every page, multi-page history |
| `CertificateServiceTest` | Role/ownership rules, no-records case, certificate reuse, verification (valid / superseded / not found), malformed IDs |
| `CertificateRepositoryTest` | Dose ordering, ownership query, saving and finding issues |
| `CertificateControllerTest` | PDF download headers, 403/404/422 mapping, public verify page |

Run: `mvn test`

## 9. Design decisions to mention in the write-up

- **Certificates are per patient, verified by a stored snapshot hash.** Each issued certificate gets an ID and a SHA-256 hash of the records it covers. The QR code opens `/verify/{id}`, which recomputes the hash. If a dose is added or changed later, the old certificate shows as "records have changed" instead of silently looking current.
- **Verify page shows only a masked name**, issue date and dose count (POPIA: minimal disclosure).
- **CSV export neutralises spreadsheet formulas** (a cell beginning with = + - @ is prefixed), because patient-supplied text ends up in exports.
- **Reports use `JdbcTemplate`** so the module depends on the agreed schema, not on other members' entity classes.
- **Exports and certificate issuing are audit-logged** (supports FR05 and POPIA accountability).
- **Role checks are enforced on the server** with `@PreAuthorize` (NFR02), consistent with the SDD design decision on route guards.
