# Setup and upgrade

## Existing installation

1. Copy these source files into your original project folder, preserving `.env` and `.git`.
2. In your existing `.env`, set ACCOUNT_REQUIRE_VERIFICATION=false and ACCOUNT_MAIL_ENABLED=false. Keep existing database passwords and JWT keys.
3. Run `docker compose up --build -d --remove-orphans` from that original folder. Do not use `down -v`.
4. Open http://localhost:8765/demo.html and press Ctrl+F5.
5. Choose Create account → Create and manage quizzes. Use an email not already registered as a student. Signup logs you in automatically. Existing student accounts keep their original role; this release does not silently promote them.
6. Add questions, create and publish a quiz, then copy its link. Students opening it have Take quizzes preselected at signup.

Website email/password values belong in the signup form, not `.env`. Optional old BOOTSTRAP_ADMIN_* values can be removed; existing database accounts remain.

## New installation

Run `java scripts/SetupEnv.java` once from the project root to generate infrastructure secrets. Then run `docker compose up --build -d`. Create your account through the website. The script refuses to overwrite an existing `.env`.

A quiz creator is called ADMIN internally for compatibility. This is an owner role, not a platform-wide superadministrator. Other creators' private questions, drafts and participant reports remain protected by ownership checks. Student and creator are separate account types; this release does not add a role-switch feature.
