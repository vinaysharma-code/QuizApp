> Current account flow: website signup supports quiz creators and students. No bootstrap admin email/password is required. Creator signup uses accountType=CREATOR; omitted accountType defaults to STUDENT for API compatibility. Existing student accounts are not automatically promoted. See SIMPLE_WORKFLOW.md.

# Current simple demo configuration

See SIMPLE_WORKFLOW.md first. ACCOUNT_REQUIRE_VERIFICATION=false and ACCOUNT_MAIL_ENABLED=false are the default: email/password login, quiz sharing and participation require no SMTP provider. The deployment guidance below still applies to the four services; SMTP configuration is optional unless you enable verification/recovery. No email inbox container is included.

# Deployment and current scope

## Implemented improvements

1. JWT authentication, BCrypt and separate ADMIN/STUDENT authorization.
2. Persisted owned attempts, selected answers, timestamps and results.
3. Server deadlines, membership validation, replay semantics and row-locked finalization.
4. Immutable publication snapshots preserving text/options/answer keys.
5. Security/business/SQL tests, live concurrency checks, controlled downstream failures, metrics and request correlation.
6. Local demo interface, generated credentials, Docker packaging, CI and deployment instructions.
7. Owner-controlled drafts, exact question selection, preview/publication and immutable rules.
8. Opening/closing windows, maximum attempts, passing thresholds and four result release policies.
9. Owner reports with participants, marks, filters, pagination, CSV and quiz-wide statistics.
10. Reproducible HTTP load script, runtime health checks and a Compose smoke-test CI job.

These changes make a more complete assessment application. Portfolio value still depends on your ability to explain, run and demonstrate the system. Tests are evidence of checked behavior, not proof that every possible bug is absent.

## Single-server Docker deployment

Docker supports Compose deployment to one server: https://docs.docker.com/compose/how-tos/production/

1. Install Docker Engine/Compose on a Linux server with sufficient memory for four JVMs plus databases. Measure resource use; a small demo may start around a 4 GB server, but that is an estimate, not a benchmark.
2. Copy/clone this project, generate `.env` with `java scripts/SetupEnv.java`, and store the generated credentials privately.
3. Set PUBLIC_APP_URL=https://your-domain and COOKIE_SECURE=true in .env. Configure SMTP only if enabling email features. Then run `docker compose up --build -d`; inspect service logs and wait for discovery.
4. Point your domain at the server and configure a host reverse proxy with HTTPS to `127.0.0.1:8765`. For example, a host-installed Caddy:

```caddyfile
quiz.example.com {
    reverse_proxy 127.0.0.1:8765
}
```

Use your actual domain and configure DNS/firewall/certificate validation. Caddy instructions: https://caddyserver.com/docs/quick-starts/https

Only the gateway should be externally reachable. The supplied Compose binds Registry/direct API ports to host loopback; database ports are not published. For a deployed environment you can remove direct API host bindings entirely. Keep PostgreSQL private, configure durable storage plus automated backups and practice restore. Named volumes alone are not a backup.

The two databases can share one PostgreSQL server with separate databases/owners; two physical servers are not required. The demo Compose uses two PostgreSQL containers for isolation and simple setup.

Sign in at `https://your-domain/demo.html` or use Swagger at `/swagger-ui/index.html`. Admins and students use different accounts. Replace demo email details with your own operator account before using it with real people.

## Render with separate Docker services

Use a public Gateway web service, private Registry/Question/Quiz services and two owned databases on a plan supporting the required private network. Keep services in the same reachable workspace/region. This is not a claim that all resources are free.

References: https://render.com/docs/web-services · https://render.com/docs/private-services · https://render.com/docs/private-network

For each application use its own directory as Docker build context/root with its Dockerfile. Every application is a separate deployment/process even when all use the same repository/provider.

- Registry: set its listening PORT consistently and keep it private.
- Question: set its three QUESTION_DB_* variables, JWT_PUBLIC_KEY, EUREKA_URL, EUREKA_PREFER_IP=false and SERVICE_HOSTNAME to Question's actual internal hostname.
- Quiz: set the three QUIZ_DB_* variables, JWT_PUBLIC_KEY and JWT_PRIVATE_KEY, EUREKA_URL, EUREKA_PREFER_IP=false and Quiz's internal SERVICE_HOSTNAME. BOOTSTRAP_ADMIN_* values are optional legacy provisioning; normal users create accounts on the website.
- Gateway: set JWT_PUBLIC_KEY, EUREKA_URL and platform PORT.
- EUREKA_URL must be `http://actual-registry-internal-host:actual-port/eureka/`, not localhost on separate deployments.
- Registered instance hostnames/ports must be reachable over the internal network. SERVICE_HOSTNAME contains a hostname only, without scheme/port.
- Use the provider's JDBC-compatible connection URL and required TLS settings for each database. Keep signing/database credentials in platform secrets.
- Use `/actuator/health` for health checks; test a complete login → start → submit → history flow before handing out the URL.

Sleeping free services can still cause cold starts and delayed discovery. JWT and PostgreSQL do not remove that hosting limitation. These Java server JARs need a Java/Docker runtime; Vercel's normal frontend deployment is not the direct target.

## Upgrading the PostgreSQL-only edition

Back up both databases first, then test on copies. Keep all applied Flyway SQL immutable. Question-Service V2 adds nullable owner_email and changes uniqueness to per-owner/category/title. Existing questions have no reliable administrator mapping and are hidden until an operator reviews and assigns each to the correct ADMIN email. Quiz-Service V4 adds session/account token, throttle and saved-answer columns/tables; Quiz-Service V1–V3 remain unchanged. Existing admins are verified by migration; existing students must request verification. Existing published snapshots remain usable. Never reset volumes to apply a migration. API clients should move from the legacy stateless submit endpoint to the owned attempt workflow in README.

Reuse the existing database passwords when reusing existing Compose volumes. Generate RSA keys with `java scripts/SetupEnv.java --keys-only`, save both JWT_PUBLIC_KEY and JWT_PRIVATE_KEY privately in the existing `.env`, and keep chosen bootstrap admin values; the setup helper intentionally refuses to overwrite it. Never run `down -v` as an upgrade step if you want to preserve data.

## Further production work

Account verification/recovery, rotating refresh sessions, login throttles, private banks, RSA separation and autosave are implemented. Remaining operational work includes real SMTP delivery verification, TLS, monitored backups, signing-key rotation, alerts, retention rules and high availability. Existing bearer tokens at offline validators remain usable until expiry after logout/reset. Mail failures have no durable retry outbox; delivery needs separate monitoring. The shared-address gateway rate limit is deliberately conservative and needs traffic-specific tuning. There is no MFA or automatic horizontal scaling configuration.

The test report records what was actually executed, including any Docker/browser/deployment limitations. There are no invented usage or throughput numbers.

## CI and dependency startup

The verify workflow runs Maven checks with isolated PostgreSQL databases. A separate live-compose job generates ephemeral environment secrets, builds the Compose stack, runs the API and Playwright browser regression suites, performs a backup/restore drill, and always tears down containers/volumes. It will execute after this source is pushed to a GitHub repository with Actions enabled; no remote CI run is claimed here.

Runtime containers run as a non-root user with heap/memory limits and HTTP health checks. Startup dependencies wait for healthy services. Quiz/Gateway startup does not require a healthy Question-Service, preserving access to published snapshots during a bank outage. JVM/service registration and discovery still take time; smoke tests retry readiness. No online deployment or hosting account has been created by this package.

## Upgrade from the previous edition

Keep the existing .env and database volumes. Back up both databases and test migrations on copies. Do not edit old Flyway SQL files. Question V2 adds nullable owner_email; legacy questions are hidden until a database operator assigns each to the correct existing ADMIN's normalized email. Review ownership first; never blanket-assign another person's bank. Quiz V4 adds account/session/answer metadata. Existing ADMIN accounts are marked verified; existing STUDENT accounts must request a verification link. Existing published snapshots continue to work.

RSA keys replace runtime JWT_SECRET. Use `java scripts/SetupEnv.java --keys-only`; give only the public key to Gateway/Question and both keys to Quiz. New keys invalidate previous tokens. Set PUBLIC_APP_URL and SMTP settings before requiring real users to verify. On public HTTPS set COOKIE_SECURE=true. Rebuild and restart all services after configuring the new keys.

## Backup and restore drill

From the project root, with Compose databases running:

```bash
java scripts/DatabaseBackup.java backup backups
java scripts/DatabaseBackup.java verify backups/ACTUAL-TIMESTAMP-FOLDER
```

The first command creates custom-format dumps of both databases in a new timestamped directory. The second restores each dump into a unique temporary database, checks Flyway history and drops only that temporary database. It never replaces the running databases. Backups contain account data: store them privately, encrypt off-server copies, schedule retention, and monitor failures. A successful schema restore check is not a full application disaster-recovery exercise. Practice application-level recovery separately.

The supplied CI includes this drill. Docker and hosted-provider execution have not been verified in this workspace; confirm the workflow passes in your repository. Real SMTP delivery, domain/TLS, provider networking, resource sizing and off-server backup storage require your deployment configuration. No hosting resources have been created.
