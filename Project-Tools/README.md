# Java project tools

This module is a command-line utility, not a microservice. It starts no web server and owns no database. Java 17+ is required. Run the commands below from the repository root.

## Generate configuration

```cmd
java scripts/SetupEnv.java
```

This standalone Java source needs no Maven build. It creates .env with independent random passwords and a 64-byte signing key, prints the local admin login and refuses to overwrite existing configuration. Keep .env private. Compose loads it; ordinary Java/Maven commands do not automatically load it.

## Build the HTTP verification tools

Windows Command Prompt:

```cmd
mvnw.cmd -pl Project-Tools package
```

PowerShell uses `.\mvnw.cmd`; Bash uses `./mvnw`. This module's build does not require PostgreSQL. The full root `verify` includes the service database integration tests and needs dedicated test databases.

## Check a running application

Windows Command Prompt:

```cmd
set DEMO_ADMIN_EMAIL=admin@example.test
set DEMO_ADMIN_PASSWORD=YOUR_GENERATED_ADMIN_PASSWORD
set RUN_EXPIRY_TEST=1
java -jar Project-Tools/target/project-tools.jar smoke
```

Use your actual generated password, not the placeholder. PowerShell uses `$env:DEMO_ADMIN_EMAIL="admin@example.test"` and analogous assignments. Bash uses `export DEMO_ADMIN_EMAIL=admin@example.test` and analogous assignments.

An optional final argument selects another gateway: `smoke https://your-own-quiz-host.example`. The suite creates uniquely named demo users/questions/quizzes/attempts. Use a test deployment; it intentionally does not delete results. Java checks remain active without `-ea`, and failures exit nonzero. Response bodies and tokens are omitted from HTTP failure messages.

Optional variables:

- RUN_EXPIRY_TEST=1 includes a real 31-second wait to verify closing and result release.
- DEMO_OTHER_ADMIN_EMAIL and DEMO_OTHER_ADMIN_PASSWORD enable cross-admin ownership checks. Provision that second administrator deliberately; public signup always creates STUDENT.
- DIRECT_QUESTION_URL and DIRECT_QUIZ_URL enable checks against direct service URLs.

Skipped optional groups are printed explicitly. The suite waits for discovery before making writes; it does not blindly retry failed writes.

## Measure read latency

```cmd
set DEMO_EMAIL=YOUR_STUDENT_EMAIL
set DEMO_PASSWORD=YOUR_STUDENT_PASSWORD
java -jar Project-Tools/target/project-tools.jar load --requests 200 --concurrency 10 --output load-result.json
```

Alternatively supply LOAD_TEST_TOKEN. The default route is /quiz/published through http://localhost:8765; --url and --path override it. The tool warms up with three requests, prints response counts, throughput and latency percentiles, and exits nonzero when responses fail. It uses Java HttpClient and ExecutorService. A short small-dataset run does not establish production capacity.
