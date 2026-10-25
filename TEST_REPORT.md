> Current account flow: website signup supports quiz creators and students. No bootstrap admin email/password is required. Creator signup uses accountType=CREATOR; omitted accountType defaults to STUDENT for API compatibility. Existing student accounts are not automatically promoted. See SIMPLE_WORKFLOW.md.

# Validation report — 25 October 2026

Runtime: Java 17.0.20, Maven 3.9.9, Spring Boot 4.1.1, Spring Cloud 2025.1.3, JJWT 0.12.7, real PostgreSQL 16.10.

## Automated suite

The five-module Maven `verify` run passed **53 tests, zero failures, zero errors, zero skipped**:

| Test class | Count | Checked behavior |
|---|---:|---|
| ServiceRegistryApplicationTests | 1 | Registry context |
| QuestionServiceApplicationTests | 4 | PostgreSQL random query, UUIDs, ordering, constraints, scoring, ownership isolation |
| Question-Service SecurityBoundaryTest | 1 | Real filters deny anonymous, student and malformed tokens at bank boundary |
| QuizServiceApplicationTests | 1 | Relational collection ordering and deletion after flush/clear/reload |
| UserServiceTest | 3 | BCrypt encoding invocation, forced STUDENT, authenticate-before-JWT, failed login |
| AttemptServiceTest | 11 | Replay, ownership, autosave revisions, saved-answer deadline scoring, exact expiry, timestamp precision and release policies |
| SessionRecoveryIntegrationTest | 6 | Verification, refresh rotation/replay, reset revocation, expiry/purpose, throttling and logout with PostgreSQL |
| AttemptStartRulesTest | 5 | Opening/closing boundaries, active-attempt resume, exhausted attempts, closing-capped deadline |
| QuizOwnershipTest | 4 | Owner denial, immutable publication, invalid windows, duplicate selection |
| CsvExportTest | 1 | CSV quoting and spreadsheet formula neutralization |
| JwtServiceTest | 3 | Valid token, signature tampering/wrong key, expiry, required role claim |
| RsaBoundaryTest | 3 | Valid issuer token, public-key-only verifier cannot mint, missing audience rejected |
| QuizServiceTest | 4 | Complete snapshot copied, incomplete bank response never persisted |
| ApiGatewayApplicationTests | 1 | Gateway/security context |
| Gateway SecurityBoundaryTest | 1 | Real JWT filters enforce bank boundary |
| Project-Tools ToolRegressionTest | 4 | Quoted/multiline CSV, malformed CSV rejection, always-active checks, HTTP failures omit response secrets |

Unit tests use the supplied JobSearch project's familiar MockitoExtension/@Mock/@InjectMocks/Arrange–Act–Assert approach. PostgreSQL integration tests and real-filter MockMvc checks add coverage beyond mocked services. MockMvc tests use signed tokens rather than bypassing the JWT filter with @WithMockUser.

## Live four-service HTTP checks — passed

Started all four Java applications against fresh real PostgreSQL databases with random runtime signing/admin secrets. For the isolated test network, advertised localhost with EUREKA_PREFER_IP=false and SERVICE_HOSTNAME=localhost.

Passed checks:

- Controlled administrator bootstrap and real BCrypt password login.
- Wrong-password login returns 401.
- Student registration cannot elevate itself by supplying role=ADMIN.
- Missing credentials return 401; authenticated students receive 403 on administrative bank/publication routes.
- Changing a JWT's role claim without a valid signature returns 401.
- Direct Question-Service and direct Quiz-Service enforce roles independently of Gateway.
- Students cannot read bank questions, answer-key snapshot endpoint or legacy arbitrary-ID scoring endpoint.
- Publish a quiz, edit its original bank question and answer, then take the published quiz: original text and correct-answer snapshot remain stable.
- A different student cannot read another student's attempt.
- Foreign question IDs and duplicate answers return 400 without finalizing the attempt.
- Two simultaneous HTTP submissions return the same stored result.
- A later changed-answer replay returns the original score and submission time.
- PostgreSQL confirms exactly one finalization version (version=1) and one response row per answered question.
- History is scoped to the authenticated student and contains no password or rightAnswer fields.
- A real 30-second attempt is submitted after waiting 31 seconds: HTTP 410, EXPIRED, no score. The expiration state is persisted.
- Terminate Question-Service: an already published attempt still scores using Quiz-Service's local snapshot, while a new publication returns controlled 503.
- Restart Quiz-Service: accounts, snapshots, attempts and recorded scores survive, and an existing valid JWT remains usable.
- Gateway Swagger configuration and both service OpenAPI documents include the Bearer scheme.
- HTTP 200 for demo HTML/JS/CSS, Swagger UI/configuration and quiz documentation.
- Metrics are available to ADMIN and denied to STUDENT.
- Exact manual question selection preserves order through preview, publication and attempts; drafts are absent from the student catalog and cannot be started.
- Published draft content/rules reject edits; other administrators cannot read/edit/preview/publish/release/report/export an owned quiz, including direct Quiz-Service checks.
- Two simultaneous start requests with maxAttempts=1 yield exactly one successful attempt and one conflict; abandoned/expired attempts consume allowance.
- MANUAL result score/pass are withheld consistently on submission, detail and history, then visible after owner release.
- Three participants with scores 0, 1 and 2 produce the correct pass/fail report, average/highest marks and distinct participant count.
- Pass/fail/search/date filters, report pagination, invalid status/page size and CSV formula neutralization work over HTTP.
- Retaking produces two attempts for one unique participant.
- Future opening and exact closing reject starts; closing caps the deadline; AFTER_CLOSE releases submitted marks and rejects late unfinished submissions.
- Failed publication during a real bank outage leaves the saved draft unpublished.

New tests cover six PostgreSQL-backed recovery/session flows (email verification, refresh rotation/replay revocation, reset/auth-version revocation, token expiry/purpose, throttling and logout) and three RSA public-key verification boundaries. The session integration tests use a mocked email sender; real SMTP delivery is not established by those tests.

The first live replay comparison exposed Java nanosecond vs PostgreSQL microsecond timestamp differences. Persisted application timestamps now use microsecond precision, with a regression test. The complete live suite passed after that correction.

## Interactive browser regression

Headless Chromium drove the Gateway-served frontend against the real four-service/PostgreSQL stack. Login, cookie-backed reload/resume, autosave, access-token renewal, two-tab stale-write conflict resolution, final submission, countdown/deadline finalization from saved answers, mobile-width layout and logout passed with no JavaScript page errors. Email verification and password reset are covered by PostgreSQL integration tests using a mocked mail sender; this does not verify SMTP delivery or those two forms in Chromium.

## Measured local HTTP check

Executed the included Java load command after the live suite, with four Java processes (each -Xmx256m), PostgreSQL 16.10 and an authenticated student. GET /quiz/published traversed Gateway and Quiz-Service. Dataset: four published quizzes, two questions each; three warm-up requests. Question-Service was stopped, showing that catalog reads use local data.

| Metric | Observed |
|---|---:|
| Requests / concurrency | 200 / 10 |
| HTTP 200 responses | 200 |
| Elapsed time | 1.391 s |
| Throughput | 143.78 requests/s |
| Mean / p50 latency | 68.16 / 66.27 ms |
| p95 / p99 latency | 99.02 / 111.30 ms |

Raw results: verification/local-load-result.json. This is a brief warm local run on a small dataset in a shared development environment, not a production capacity claim or a measurement of writes, reporting at scale, external internet latency or Docker performance.

## Packaging and setup checks

- All four executable application JARs and the Java CLI JAR built successfully and passed ZIP/JAR integrity checks.
- Maven XML and Compose/CI YAML parsed.
- Demo JavaScript passed Node syntax validation.
- Environment generator produced matching RSA-3072 keys, private .env permissions and refused to overwrite an existing .env without changing it.
- Source package excludes target directories, IDE metadata, actual .env files, database files and reference JobSearch content.

## Limits

Docker is not installed here. Docker image builds, Compose networking and the configured CI backup/restore drill were not executed; verification used separate Java processes plus real PostgreSQL instead. Compose/CI use PostgreSQL 17; live database tests used 16.10. Interactive Chromium testing is included in CI and was exercised against the packaged gateway; the flows that passed are listed below. Hosted deployment, external TLS/DNS, production load capacity, SMTP delivery, signing-key rotation, off-site backups and high availability were not validated.

This is concrete evidence for the listed behaviors, not a guarantee that no undiscovered bugs exist.

## Java tooling replacement

The three Python helpers were removed from the delivered source. SetupEnv.java now generates configuration directly with JDK 17+, while Project-Tools provides Java smoke/load commands. The CI workflow and documentation call those Java tools. This edition adds PostgreSQL migrations and the account/session/autosave features described in README.md.

The Java setup helper was executed in an isolated temporary directory: matching RSA-3072 keys, POSIX 0600 permissions and unchanged .env after a rejected second invocation were verified. The project includes a Java-only smoke/load CLI and uses JavaScript/Playwright for browser testing.

The replacement Java CLI was executed against all four live applications on fresh PostgreSQL databases. Its complete suite passed with both optional second-admin and real-deadline groups enabled. A subsequent real bank outage preserved scoring and left failed publication as a draft; Quiz-Service restart preserved results. Frontend HTML/CSS/JavaScript and Swagger URLs returned HTTP 200. The Java load command completed 200/200 HTTP 200 requests at concurrency 10; the measured table is a single small local run and not a capacity claim.


## V4 reliability changes

- RSA-3072/RS256 signing with the private key held only by Quiz-Service; key generator and migration instructions checked locally.
- Eight-hour rotating refresh sessions, replay-family revocation, email verify/reset one-time links, auth-version updates and database-backed throttles. Recovery tests use real PostgreSQL plus mocked SMTP.
- Versioned autosave, cross-tab conflict response, start idempotency/resume and server deadline scoring from previously saved answers.
- Per-admin question-bank ownership, searchable/paginated quiz catalog and student history.
- Java backup/restore drill source compiles through the JDK source launcher; actual Docker commands require a Compose environment.
- No Python source/runtime is present in the delivered project. The browser regression runner is JavaScript/Playwright and creates isolated fixture data.


## Simplified sharing workflow — 25 October 2026

The current Maven verification passed 53 tests with zero failures/errors/skips, including published-link metadata and draft denial. Email verification and sending now default to false. Registration skips verification mail in this mode; recovery controls are optional. /auth/options tells the UI whether email features are enabled without exposing secrets. GET /quiz/published/{id} returns the existing student-safe QuizSummary for a published quiz and requires an authenticated role at both Gateway and Quiz-Service.

The frontend adds copyable published quiz URLs, preserves the quiz query through login/registration/reload, gives linked students a focused quiz panel and hides the student catalog from admins. Optional quiz rules are collapsed. No local email inbox container is included. Existing ownership, JWT, answer-key isolation, attempts and deadlines remain in place. No database migration is needed for this update.

The interactive Chromium regression passed for the simplified flow: anonymous shared-link landing, UI student registration without verification, login returning to that quiz, metadata title, focused catalog visibility, URL preservation and resume after reload, autosave, expired-token renewal, two-tab conflict handling, submission/scoring, timed finalization, mobile width, logout and admin share-link controls. No JavaScript page errors were recorded. The fixture setup retries publication only on startup 503 while discovery warms up. Hosted remote access and SMTP remain unverified and optional.

The local email inbox service has been removed from Compose and its example configuration. Static checks confirm that the former inbox service, optional profile and port mapping are absent; the six default services are Registry, Gateway, Question, Quiz and the two databases. No Java or database schema changes were required for this removal.


## Public creator signup validation

The latest Maven verify passed 55 tests with zero failures/errors/skips. Added coverage confirms creator registration maps to the owner role, missing accountType remains a student for API compatibility, and null accountType is rejected. Existing ownership isolation tests passed.

The live four-service Chromium suite passed public creator signup, automatic session creation, an empty private bank for the new creator, shared-link student signup, autosave/reload/resume, access renewal, cross-tab conflict protection, submission, deadline finalization, mobile width, logout, and share control, with no JavaScript page errors. No hosted deployment or Windows Docker execution was performed here.
