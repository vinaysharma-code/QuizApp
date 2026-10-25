# Create, share, attempt

The normal app uses email/password sign-in with two roles. It does not require an email service.

1. Anyone can create an account on the website. Choose **Create and manage quizzes** to become a quiz creator, or **Take quizzes** to become a student. Signup logs you in automatically when email verification is disabled. Creator accounts use the existing ADMIN role internally and can manage only their own resources. No login credentials are required in .env.
2. Admin signs in at /demo.html, adds questions, selects them, saves a draft and publishes it. Optional timing/result settings are collapsed; marks default to immediate release.
3. Under My quizzes, admin clicks Copy quiz link. A published quiz link looks like https://YOUR-HOST/demo.html?quiz=QUIZ-ID.
4. Student opens the link, creates an account with **Take quizzes** selected if necessary, logs in and clicks Start / resume quiz. The link remains selected through registration, login and reload.
5. Answers save automatically. Student submits and sees marks (unless the admin deliberately chose a delayed release policy). Admin opens Participants & results.

On a quiz link, the student sees that quiz instead of the full catalog. The usual /demo.html still offers the catalog and personal attempt history. Admins see their own quizzes and private question bank, not the student catalog. A link is shareable, not an invitation-only access control: anyone with a student account and the link can attempt an open published quiz, subject to its rules.

## Update an existing local installation

Copy the updated source into your checkout while preserving .git, .env and database volumes. In your existing .env, replace or add exactly one line for each:

```dotenv
ACCOUNT_REQUIRE_VERIFICATION=false
ACCOUNT_MAIL_ENABLED=false
```

Then run from the project root:

```cmd
docker compose up --build -d
```

No new SQL migration or database reset is required for this update. Keep existing users, quizzes, password hashes and JWT keys. Do not run down -v.

Open http://localhost:8765/demo.html. A localhost link works only on your computer. To share with remote students, deploy the Gateway publicly and copy the quiz link from that hosted page. Local same-network testing requires a reachable host address and appropriate port/firewall configuration.

## Optional email features

Password recovery and email verification remain optional. Enable ACCOUNT_MAIL_ENABLED=true and configure SMTP to use them. To require verification as well, set ACCOUNT_REQUIRE_VERIFICATION=true. Email features require an external SMTP provider and a correct PUBLIC_APP_URL. Existing verified users remain valid when verification is disabled.

Swagger remains at /swagger-ui/index.html as a developer tool, with no link in the main user flow. The four applications remain separate microservices; authentication, authorization, answer-key isolation, quiz ownership, server deadlines and scoring still apply.
