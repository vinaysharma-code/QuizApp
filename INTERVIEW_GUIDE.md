> Current account flow: website signup supports quiz creators and students. No bootstrap admin email/password is required. Creator signup uses accountType=CREATOR; omitted accountType defaults to STUDENT for API compatibility. Existing student accounts are not automatically promoted. See SIMPLE_WORKFLOW.md.

# Interview guide: one familiar security flow

## Mapping to your JobSearch project

| JobSearch reference | Quiz implementation | Same concept |
|---|---|---|
| security/JwtService | Quiz-Service/security/JwtService; token-verification copies in Gateway/Question | JJWT 0.12.7, Base64 key, signed claims, expiry |
| security/JwtAuthenticationFilter | security/JwtAuthenticationFilter | OncePerRequestFilter, Bearer token, SecurityContext |
| security/CustomUserDetailsService | Quiz-Service/security/CustomUserDetailsService | UserDetailsService → UserRepo → roles/active flag |
| security/SecurityConfig | security/SecurityConfig | Stateless SecurityFilterChain, BCrypt, AuthenticationManager |
| controller/AuthController | Quiz-Service/controller/AuthController | POST /auth/register and /auth/login |
| service/UserService | Quiz-Service/service/UserService | Hash during registration, authenticate before issuing JWT |
| request/response DTO classes | dto/request and dto/response | Lombok, separate inputs and safe outputs |
| JobServiceTest | UserServiceTest, AttemptServiceTest, QuizServiceTest | MockitoExtension, @Mock, @InjectMocks, Arrange–Act–Assert, verify |

Mongo ObjectId conversion becomes a plain UUID String because persistence is PostgreSQL. The quiz uses its original Spring Boot 4 stack rather than changing framework versions to match JobSearch's Boot 3; the security concepts remain the same.

Quiz-Service looks up a user for every authenticated request, as JobSearch does. Gateway/Question cannot use Quiz's repository without sharing its database, so they validate the issuer-signed role/email claims. That is the essential microservices adaptation.

## What to explain in an interview

**Registration:** validate DTO → normalize email → validate accountType (CREATOR or STUDENT) → map creator to owner-scoped ADMIN → BCrypt hash → insert user with unique email. A raw role field is ignored; the explicit creator account type permits creating only owned resources.

**Login:** AuthenticationManager invokes the UserDetailsService/PasswordEncoder path → load authenticated user → sign access JWT with subject=email and userId/role claims. There is no password inside the JWT.

**Authenticated request:** filter verifies signature, issuer, token type and expiry → loads current user in Quiz (or trusts signed claims in the resource services) → creates UsernamePasswordAuthenticationToken → sets SecurityContext → authorization rule decides whether the role can call the endpoint.

**Why 401 vs 403:** missing/invalid/expired credentials produce 401. A valid STUDENT token requesting an ADMIN endpoint produces 403.

**Answer-key protection:** bank and snapshot endpoints are ADMIN-only in Gateway AND Question-Service. Student result/question DTOs omit rightAnswer. Scoring happens on the server using a saved private snapshot; the student does not submit the correct answer or final score.

**Published snapshot:** a quiz stores copied question text, option text and correct answers. A bank edit cannot change the meaning of an existing attempt. Snapshot keys remain private to the server/admin boundary.

**Concurrent submission:** transaction loads attempt with PESSIMISTIC_WRITE → checks ownership/state/deadline → validates answers → stores responses and final score/status → commits. A competing transaction waits, then returns the already stored result. Unique response keys and @Version add integrity protection. No external HTTP call occurs while scoring.

**Expiry:** server UTC Clock is authoritative. The exact deadline is expired. Returning an EXPIRED response rather than throwing an unchecked exception lets the expiration state commit in the same transaction. Unit tests inject a fixed Clock rather than sleeping.

**Microservice outage:** publishing needs Question-Service and fails 503 if unavailable. Existing quiz attempts need only Quiz-Service and its own database; they use snapshots and remain scoreable.

**Service boundaries:** Question owns its bank database; Quiz owns accounts, quiz versions and attempts. Snapshot question IDs are references across service boundaries and do not have a cross-database FK. Within Quiz's own database, attempts/users/quizzes have normal relational foreign keys.

**Tests:** unit tests isolate business rules with mocks. PostgreSQL integration tests prove actual SQL/migrations/constraints. MockMvc security tests traverse real filters with signed tokens. The live script exercises gateway discovery, Feign and concurrent HTTP requests. Each layer proves something different.

## Deliberate limits to acknowledge

Quiz-Service alone holds the RSA private signing key; Gateway and Question-Service hold only the public verification key. Access tokens default to 15 minutes, with rotating eight-hour browser sessions and replay-family revocation. Single-use email verification/reset links, database-backed login throttles, saved-answer revisions and idempotent starts are implemented. Quiz checks account auth versions; offline validators can accept an existing token until expiry. MFA, seamless signing-key rotation, durable mail retries and high availability remain future work. This is not an externally audited production identity system.

A student may start multiple attempts up to the published maximum; expired and abandoned starts also consume the allowance. Starts lock the user row before counting, so concurrent requests cannot bypass the limit. The lock is deliberately coarse per student, while submissions lock only their attempt. Once an attempt is submitted, replay returns its stored result rather than treating a different payload as a new submission. Set maxAttempts=1 for one attempt per quiz; the server enforces it.

The PostgreSQL row-lock design serializes submissions per attempt, not every request in the application. It is not evidence of benchmarked throughput. Use measured load tests before claiming a capacity figure.

## New product decisions to explain

- Quiz ownership supplements ADMIN role checks. Authorization happens before reading reports or calling the bank for another admin's draft.
- Drafts are editable; publication freezes both assessment rules and question/answer snapshots. A failed remote publication rolls back and leaves the draft unpublished.
- maxAttempts is checked inside a transaction after locking the student's user row; attempt numbers have a database uniqueness constraint as a second protection.
- Result release is a centralized DTO decision shared by submission, detail and history; hiding marks only in the UI would allow an API bypass.
- Participant count is DISTINCT student_id; total attempts counts retakes too. Score statistics exclude unfinished/expired attempts.
- Reports use SQL predicates and pagination, fetch the student relation with the page and limit CSV size. CSV values are untrusted spreadsheet input, so quoting alone is insufficient to prevent formula execution.
- A catalog question_count column avoids loading every quiz's question collection just to list metadata. This value is updated with draft/snapshot content and backfilled in V3.
- Demonstrate the included load script with the actual environment and dataset; never turn a small local result into a claim about production capacity.

## Explain the latest reliability decisions

Autosave is a versioned update, not final submission. A row lock serializes requests; a stale revision gets 409 so another tab cannot silently overwrite answers. Deadline finalization uses only answers received before the deadline. Published snapshots allow scoring while Question-Service is unavailable. Idempotency keys prevent retrying a start from consuming another attempt. Four independently runnable applications remain microservices despite sharing a repository; Project-Tools is only a Java CLI.

Refresh-token rotation stores hashes, locks rows and revokes the family on replay. A custom browser header plus same-origin requests and a Strict cookie protect cookie-authenticated endpoints. RSA separates signing authority from verification while retaining the JobSearch-style Spring Security classes. Server-side ownership applies to every bank/quiz/report route; hiding UI buttons alone is not authorization.


The default user journey is admin create/publish/copy link, student register/sign in/open linked quiz/submit, then admin participant results. Email verification and recovery are optional; disabling verification does not disable password authentication or ADMIN/STUDENT authorization. A share link identifies a published quiz; it is not an invitation-only secret.
