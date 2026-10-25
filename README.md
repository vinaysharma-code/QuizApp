> Current account flow: website signup supports quiz creators and students. No bootstrap admin email/password is required. Creator signup uses accountType=CREATOR; omitted accountType defaults to STUDENT for API compatibility. Existing student accounts are not automatically promoted. See SIMPLE_WORKFLOW.md.

# Quiz Platform — create, share, attempt

The default demo now uses a simple email/password flow with no required email verification or SMTP account. Admins publish quizzes and copy a shareable link; students open that link, register/sign in, attempt and submit. Read [SIMPLE_WORKFLOW.md](SIMPLE_WORKFLOW.md) for the current flow and safe upgrade instructions.

Java 17 · Spring Boot 4.1.1 · Spring Cloud 2025.1.3 · Spring Security · JJWT 0.12.7 · PostgreSQL · JPA · Flyway · Eureka · OpenFeign · Gateway MVC

## Start the complete demo

Install JDK 17+ (for the Java setup helper) and Docker Desktop with Linux containers and Compose. Open a terminal inside this extracted project:

```powershell
java scripts/SetupEnv.java
docker compose up --build -d
docker compose logs -f
```

The setup script creates `.env` with independently generated database passwords, an RSA-3072 signing/verification key pair for token security. It does not create website login credentials and refuses to overwrite an existing `.env`. Keep these infrastructure secrets private. You can also fill `.env.example` manually and save it as `.env`; never use the test-only keys in `src/test/resources` for deployment.

Wait for the four Java applications and Eureka discovery to become ready. A health endpoint becoming UP does not mean every downstream route is ready. Registration/cache propagation can take another 30–90 seconds.

- Demo interface: http://localhost:8765/demo.html
- Unified Swagger: http://localhost:8765/swagger-ui/index.html
- Gateway health: http://localhost:8765/actuator/health
- Local Eureka dashboard: http://localhost:8761

Demo steps:

1. Sign in as the generated administrator.
2. Add bank questions, select exact questions, and save a draft. Set duration, opening/closing times, maximum attempts, passing percentage and result release policy. Preview and publish it.
3. Sign out, share the published quiz link, register a student and sign in. Email verification is optional and disabled by default.
4. Start an attempt, select answers, submit and open the recorded result/history.
5. Sign in as the owning administrator, open Reports, filter participants/results, inspect statistics and download CSV. For MANUAL results, use Release results.
6. Another student cannot open the first student's attempt; another administrator cannot manage this administrator's quiz or reports.

The demo uses ordinary HTML/CSS/JavaScript served by Gateway. Access JWTs stay in browser memory. A rotating HttpOnly, SameSite=Strict cookie restores the session after reload; the last attempt resumes with server-saved answers. Catalog search and student history have page controls. A visible countdown follows the server deadline. The browser saves answer changes automatically, warns about unsaved work, and detects conflicting edits from another tab. Access/refresh tokens are never stored in localStorage or sessionStorage; pending answer drafts are stored in per-account sessionStorage.

`docker compose down` stops containers but keeps the named database volumes. `docker compose down -v` deletes database data. Changing passwords in `.env` does not change PostgreSQL users in an existing volume: update the database role credentials deliberately rather than expecting POSTGRES_PASSWORD to reset an initialized database.

## Same security style as JobSearch

See `INTERVIEW_GUIDE.md` for the direct class-by-class mapping to the supplied JobSearch reference.

- `JwtService`: Base64 DER RSA keys → RS256 signing → email/userId/role claims → signature/issuer/expiry validation.
- `JwtAuthenticationFilter`: `OncePerRequestFilter`, Bearer header, `UsernamePasswordAuthenticationToken`, `SecurityContextHolder`.
- `CustomUserDetailsService`: loads the current Quiz-Service user and authorities from PostgreSQL.
- `SecurityConfig`: stateless `SecurityFilterChain`, role rules, BCrypt, `AuthenticationManager`.
- `UserService`: register/login with separate request and response DTOs. Password hashes never appear in responses.
- Lombok DTOs and JUnit 5/Mockito `@ExtendWith`, `@Mock`, `@InjectMocks`, Arrange–Act–Assert and `verify` follow the reference's style.

Quiz-Service owns authentication/accounts. Gateway and Question-Service verify signed claims without sharing the users database. All three enforce their own security boundaries, so bypassing Gateway does not open the question bank. Only Quiz-Service receives JWT_PRIVATE_KEY and can mint tokens. All three receive JWT_PUBLIC_KEY and verify RS256, issuer, audience and expiry. The familiar filter/service structure is retained; HMAC is restricted to explicit test fixtures. Registry needs no JWT key and must remain private.

Public registration accepts accountType CREATOR or STUDENT. CREATOR receives the owner-scoped ADMIN role; omitted accountType defaults to STUDENT. A raw role field is not used. The optional admin bootstrap runs only from server environment configuration; an email belonging to a student is never silently promoted. Bootstrap does not reset an existing admin password on restart.

## Environment variables

| Service | Variables |
|---|---|
| Question-Service | `QUESTION_DB_URL`, `QUESTION_DB_USERNAME`, `QUESTION_DB_PASSWORD`, `JWT_PUBLIC_KEY` |
| Quiz-Service | `QUIZ_DB_URL`, `QUIZ_DB_USERNAME`, `QUIZ_DB_PASSWORD`, `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY` |
| Gateway | `JWT_PUBLIC_KEY` |
| Gateway / Question / Quiz | `EUREKA_URL` (default `http://localhost:8761/eureka/`) |
| Quiz admin bootstrap | `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` (optional together; password >=12 characters, <=72 UTF-8 bytes) |
| Each application | `PORT` (defaults: Registry 8761, Question 8081, Quiz 8082, Gateway 8765) |
| JWT validators | `JWT_EXPIRATION_MS` (default 900000 = 15 minutes; allowed 1000..3600000) |
| Discovery clients | `EUREKA_PREFER_IP` (default true), `SERVICE_HOSTNAME` (default localhost), `EUREKA_ENABLED` (default true) |

Database URL example: `jdbc:postgresql://localhost:5432/questions`. Supply username/password separately. A provider URL beginning `postgresql://user:password@...` must be converted into JDBC URL and credentials; it is not accepted verbatim. Configure the provider's required SSL parameters/CA when applicable.

Docker Compose creates separate PostgreSQL instances/databases and supplies their JDBC URLs. For direct Java execution, create `questions` and `quizzes` databases with separate owner users and schema-creation permissions. `.env` is loaded by Compose, NOT automatically by Spring Boot/Maven: put variables in IntelliJ Run Configurations or the launching shell.

Build in Windows PowerShell:

```powershell
.\mvnw.cmd -DskipTests package
```

On Bash use `./mvnw -DskipTests package`. Start each JAR in its own terminal, Registry → Question → Quiz → Gateway:

```powershell
java -jar Service-Registry/target/Service-Registry-0.0.1-SNAPSHOT.jar
java -jar Question-Service/target/Question-Service-0.0.1-SNAPSHOT.jar
java -jar Quiz-Service/target/Quiz-Service-0.0.1-SNAPSHOT.jar
java -jar Api-Gateway/target/Api-Gateway-0.0.1-SNAPSHOT.jar
```

## API workflow

1. `POST /auth/register` with `{"userName":"Vinay","email":"vinay@example.test","password":"A-long-password123!"}`. Response contains profile/role, not a token or password.
2. Use `POST /auth/login` with email/password. Copy `token` into `Authorization: Bearer <token>`. In Swagger click **Authorize** and paste the token.
3. As ADMIN, `POST /question/addQuestions`:

```json
{"questionTitle":"Which runtime executes Java bytecode?","optionA":"JVM","optionB":"CSS","optionC":"HTML","optionD":"SQL","rightAnswer":"JVM","difficultyLevel":"easy","category":"java"}
```

4. As ADMIN, `POST /quiz/create` with `{"categoryName":"java","noQ":1,"title":"Java basics","durationSeconds":600}`. The response is the published quiz ID. Publishing copies the full question/option/answer snapshot; it is not a mutable live reference to the bank.
5. As STUDENT, `GET /quiz/published?page=0&size=20` lists metadata without answer keys or question content.
6. `POST /quiz/{quizId}/attempts` starts a new attempt and returns its ID, server deadline and student-safe question DTOs.
7. `POST /attempts/{attemptId}/submit` with `[{"id":"question-id-from-attempt","response":"JVM"}]` saves and returns the result.
8. `GET /attempts/{attemptId}` reopens an owned attempt. `GET /attempts?page=0&size=20` returns only the current student's history.

Answers use exact option TEXT, not letters A/B/C/D. Partial submission is allowed; omitted questions score zero. Empty submissions, duplicate question IDs, non-member IDs and values outside the options are rejected before finalization. Starting while an attempt is active resumes it (HTTP 200); a new attempt returns HTTP 201. An optional Idempotency-Key header (8–100 letters/digits/underscore/hyphen) makes start retries return the original attempt, even after completion. Use a new key for a retake, up to maxAttempts (default 3). Every started attempt counts, including abandoned/expired attempts. A per-student database row lock serializes starts before checking the limit, and the database uniquely constrains quiz/student/attempt number.

Before the deadline, `PUT /attempts/{id}/answers` accepts `{"answers":[{"id":"question-id","response":"JVM"}],"version":0}` and returns the new version without exposing marks. An empty answer list is allowed for autosave. Stale versions return 409 without overwriting newer answers. At the deadline, the server scores only previously autosaved answers; a late request cannot add or change them. Attempts never autosaved become EXPIRED without a score (late submission returns 410). A scheduled worker and lazy access checks finalize due attempts; student history also finalizes due rows before reading them. A completed attempt is immutable: repeating submission returns its original recorded result, even if a later payload differs. Use a new attempt for a retake.

Concurrency is protected by PostgreSQL `PESSIMISTIC_WRITE` row locking inside a transaction, plus an optimistic `@Version` field and unique `(attempt_id,question_id)` response keys. Concurrent submissions serialize; only the first valid one finalizes the attempt.

Administrative bank routes (including legacy `getAllQuestion`, `getByCategory`, `getQuestions`, `getScore`, `generate` and new `snapshot`) require ADMIN at both Gateway and Question-Service. `PUT /question/{id}` changes the bank but never changes published snapshots. `GET /quiz/getQuiz/{id}` is ADMIN-only preview and still returns student-safe DTOs. The old stateless `/quiz/submit/{id}` scoring path is removed; it cannot bypass attempts.

## Drafts, scheduling and reports

Use these owner-only ADMIN routes through Gateway. Public creator signup grants this owner role; each creator can access only their own quizzes and questions.

| Method and path | Purpose |
|---|---|
| POST /admin/quizzes | Save a draft with selected question IDs |
| GET /admin/quizzes | Paginated quizzes owned by this admin |
| GET /admin/quizzes/{id} | Draft details and selected IDs |
| PUT /admin/quizzes/{id} | Edit an unpublished draft |
| GET /admin/quizzes/{id}/preview | Preview selected questions without answer keys |
| POST /admin/quizzes/{id}/publish | Copy immutable question/answer snapshots and publish |
| POST /admin/quizzes/{id}/release-results | Release a published MANUAL-policy quiz's marks |
| GET /admin/quizzes/{id}/attempts | Paginated participant and attempt results |
| GET /admin/quizzes/{id}/statistics | Quiz-wide aggregates, independent of report filters |
| GET /admin/quizzes/{id}/results.csv | Export the filtered report, up to 10,000 attempts |

Example draft request:

```json
{
  "title": "Java assessment", "durationSeconds": 600,
  "questionIds": ["question-uuid-1", "question-uuid-2"],
  "maxAttempts": 2, "passingPercent": 60,
  "opensAt": "2027-01-10T09:00:00Z", "closesAt": "2027-01-10T10:00:00Z",
  "resultReleasePolicy": "AFTER_CLOSE"
}
```

Questions preserve request order. Saving a draft does not need the question service; preview/publication validate its IDs against the bank. Published content and rules are immutable; create a new draft to revise them. Opening is inclusive and closing is exclusive; the attempt deadline is the earlier of its duration deadline and quiz closing time. Dates use ISO 8601 UTC instants; the demo converts local date/time input to UTC.

Release policies: IMMEDIATE (after valid submission), AFTER_CLOSE (requires closesAt), AT_TIME (requires resultsReleaseAt), MANUAL (owner releases explicitly). Before release, student submit/detail/history responses return score=null, passed=null and resultReleased=false. Admins may inspect marks before student release. Releasing never exposes answer keys.

Each question is worth one mark, no negative marking. Passing requires score × 100 >= passingPercent × total questions. Reports include student name/email, attempt number/status, marks, pass/fail, timestamps and completion duration. Expired/in-progress attempts have no score and are excluded from score averages.

Report filters: status=IN_PROGRESS|SUBMITTED|EXPIRED, search (literal case-insensitive name/email substring), passed=true|false, from (inclusive startedAt), to (exclusive startedAt), page and size (1..100). Statistics count unique student IDs separately from all attempts; average/highest/passed figures describe submitted attempts. CSV honors filters, quotes fields and neutralizes spreadsheet formula prefixes. Narrow filters if more than 10,000 rows would be exported. Each ADMIN has a private question bank. List, selection, preview, scoring and update routes enforce owner email; other administrators cannot read or modify those questions. Published snapshots remain independent of later bank changes.

## Persistence, resilience and operations

- Question V2 adds private bank ownership; Quiz V4 adds rotating sessions, account tokens, throttles and saved-answer metadata. Existing migrations are preserved.
- Question-Service Flyway V1 creates questions; V2 adds question ownership. Quiz-Service Flyway V1–V3 establish accounts, snapshots, attempts, quiz ownership/rules and reporting; V4 adds email/session tokens, throttles, auth versions and autosave/start metadata. Hibernate validates the resulting schema.
- V3 leaves legacy quiz owner_id NULL. A database operator must explicitly assign each legacy quiz to the intended existing ADMIN after reviewing ownership; unassigned quizzes cannot be managed through owner routes. No administrator automatically inherits other people's quizzes.
- Existing V1 quizzes are left unpublished because they have no reliable answer snapshot. Publish new quizzes from the question bank. No existing rows are silently deleted or made public.
- MongoDB records and JobSearch users are not imported automatically.
- Quiz-Service calls Question-Service when previewing an unpublished draft or publishing. A timeout/unavailable bank returns a controlled 503 and does not save a partial quiz. Feign timeouts are 2s connection / 5s read, with automatic retries disabled.
- Existing published attempts are started/read/scored using local snapshots, including during a Question-Service outage.
- Health is public with details hidden. Metrics/info require ADMIN. `http.server.requests` is available through Actuator after requests have been served.
- Request IDs propagate through Gateway and Feign; logs include `requestId`. Passwords, signing secrets and token contents are not logged intentionally.
- No fabricated outage scores, extra authentication microservice, Kafka or Redis are required for this flow.

## Tests and CI

Use dedicated EMPTY test PostgreSQL databases; set the six DB environment variables to them and run `./mvnw verify` (Windows: `.\mvnw.cmd verify`). Integration tests create Flyway schemas and roll back test rows; never point them at a real user's database. The `test` profile supplies an explicit TEST-ONLY signing key. CI provisions isolated PostgreSQL 17 containers in `.github/workflows/verify.yml`.

Build the CLI once with `./mvnw -pl Project-Tools package` (Windows: `.\mvnw.cmd -pl Project-Tools package`). No database is needed to build this module. For a running stack, set `DEMO_ADMIN_EMAIL` and `DEMO_ADMIN_PASSWORD`, then:

```powershell
java -jar Project-Tools/target/project-tools.jar smoke
```

Run the legacy Java smoke suite only against a disposable test stack with `ACCOUNT_REQUIRE_VERIFICATION=false`; verification is optional and requires configured email delivery. Verification/recovery are covered separately by integration tests. The smoke test adds uniquely categorized demo questions, quizzes and students; it checks roles, token tampering, snapshot stability, ownership, invalid submissions, concurrency and result replay. Set `RUN_EXPIRY_TEST=1` to include a real closing-time/deferred-release check. Optional DEMO_OTHER_ADMIN_EMAIL/PASSWORD enable cross-admin ownership checks. Optional `DIRECT_QUESTION_URL` / `DIRECT_QUIZ_URL` test direct-service protection.

See `TEST_REPORT.md` for executed checks and limits. See `DEPLOYMENT_AND_REVIEW.md` for hosted deployment and remaining production work.

For the interactive browser regression run, start a disposable stack with `ACCOUNT_REQUIRE_VERIFICATION=false`, set `BASE_URL`, `DEMO_ADMIN_EMAIL` and `DEMO_ADMIN_PASSWORD`, then run `npm ci`, `npx playwright install chromium` and `npm test` from `browser-tests/`. It creates its own questions, quizzes and student; use only a test database.

## Repeatable performance check

After building Project-Tools as above, against your own running stack, set DEMO_EMAIL and DEMO_PASSWORD for a student, then run:

```bash
java -jar Project-Tools/target/project-tools.jar load --requests 200 --concurrency 10 --output load-result.json
```

This read-only script measures GET /quiz/published through Gateway after warm-up. It reports latency percentiles, throughput and status counts without printing credentials. It is a small local check, not a production capacity promise; compare only runs with similar data, infrastructure and settings. See TEST_REPORT.md for the measured run.

## Frontend and Java tooling

The frontend is in Api-Gateway/src/main/resources/static/: demo.html supplies structure, demo.css styling, and demo.js browser interactions and API calls. Open http://localhost:8765/demo.html after starting the stack. It covers registration/login, bank questions, drafts, preview/publication, rules, participation, results, reports and CSV. This is a basic functional frontend served by Gateway. Swagger remains available separately for API exploration.

No Python files or Python runtime are required by this edition. scripts/SetupEnv.java runs directly with JDK 17+, uses SecureRandom and refuses to overwrite an existing .env. Project-Tools is a command-line Maven module using Java HttpClient, Jackson and ExecutorService for live API verification and load checks. It starts no server and is not a fifth microservice. Unit/integration tests remain JUnit/Mockito.

Build with `.\mvnw.cmd -pl Project-Tools package` on Windows or `./mvnw -pl Project-Tools package` on Bash. Run `java -jar Project-Tools/target/project-tools.jar` for usage. Run commands from the root containing pom.xml and compose.yaml.

Updating an existing checkout: copy this edition's contents into your repository root while preserving .git and your private .env. Delete the previous scripts/setup_env.py, scripts/smoke_test.py, scripts/load_test.py and any scripts/__pycache__ directory. Copying files alone does not remove old scripts. Keep README.md and .github/workflows directly at the repository root. Review git status before committing. This edition applies new Flyway migrations; read the upgrade section in DEPLOYMENT_AND_REVIEW.md first. Never reset existing volumes to upgrade.

## Sessions and account recovery

Browser login uses POST /auth/session; POST /auth/refresh rotates the cookie; POST /auth/logout revokes its session family. These three endpoints require `X-Quiz-Client: quiz-browser`. The session has a fixed eight-hour lifetime; access tokens default to 15 minutes. A reused refresh token revokes its family. The UI serializes refresh requests across tabs with Web Locks where available. Use a modern browser supporting Web Locks for multiple tabs.

POST /auth/forgot-password and /auth/request-verification accept an email and return generic messages. POST /auth/reset-password accepts token/password; POST /auth/verify-email accepts token. Links are single-use and stored hashed; reset links expire after 30 minutes and verification links after 24 hours. Reset revokes all refresh sessions and increments the account's auth version. Quiz-Service rejects old-version access tokens immediately. Gateway/Question-Service validate offline, so previously issued access tokens remain valid there until expiry. Logout revokes refresh capability, not already issued bearer tokens.

Quiz-Service uses SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH, SMTP_STARTTLS, MAIL_FROM, PUBLIC_APP_URL and COOKIE_SECURE. Email sending is disabled by default. Configure an external SMTP provider only if enabling email features. Hosted deployments use PUBLIC_APP_URL=https://your-domain and COOKIE_SECURE=true; a real SMTP provider is required only when enabling email features. Verification is disabled by default. SMTP failure does not fail overall health; monitor mail separately. Delivery is synchronous with generic failure logging, without a durable retry outbox.

Login throttling uses PostgreSQL: ten attempts per account per 15 minutes; successful login resets that account counter. Recovery requests have corresponding account buckets. Additional servlet-address limits are 120 auth requests/minute (30 registrations/minute). Behind Gateway that address is shared, so tune this aggregate ceiling and add trusted edge per-client limits for larger traffic. No Redis dependency is required.
