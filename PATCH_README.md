# SecureBank UI Refresh Patch (Aiven MySQL)

This ZIP is a **patch for the existing SecureBank repository**, not a complete project. It keeps the current Spring Boot application and Aiven MySQL database. No PostgreSQL configuration is included, and the patch does not change the database schema.

## Why the first page was still old

Spring serves `src/main/resources/static/index.html` for the root URL (`/`). That file still had the title `SecureBank - Sprint 1` and the heading `Sprint 1: Register / Login`. Earlier UI work did not replace this home page. This patch replaces `index.html` and redesigns the customer and admin pages to match it.

## Apply this patch

1. **Back up your existing project folder** before copying files.
2. Extract this ZIP into a temporary folder.
3. Copy the extracted contents into the **root of your existing SecureBank repository**, preserving the folder structure. Allow the listed files to be replaced. Do not copy a new `.git` folder from anywhere, and do not replace your whole repository with this patch.
4. Keep your existing `schema.sql` and Aiven MySQL data. This patch does not ask you to recreate the database.
5. Open the deployment guide at `docs/DEPLOYMENT_GUIDE.md` and follow its security and deployment checks before pushing.

## File-by-file changes

### Replace these existing files

| File in this ZIP | What it changes |
|---|---|
| `src/main/resources/static/index.html` | Replaces the Sprint 1 registration page with the new SecureBank entry page. Sign in is the default tab; customer registration is available from the second tab. |
| `src/main/resources/static/dashboard.html` | Banking-style customer workspace with account summary, money actions, transaction history and password settings. |
| `src/main/resources/static/admin.html` | Matching admin console for account search, status controls and transaction history. |
| `src/main/resources/static/css/style.css` | Shared visual design, mobile layout, forms, tables, status tags and transfer review dialog. |
| `src/main/resources/static/js/app.js` | Keeps existing register/login endpoints and role redirection, adds tab and password-visibility controls, and stops printing the successful API response (which includes the JWT token). Adds readable network/API errors. |
| `src/main/resources/static/js/dashboard.js` | Keeps current API routes; improves response/error handling, empty/loading messages, date display and transfer confirmation. |
| `src/main/resources/static/js/admin.js` | Keeps current admin API routes; improves loading/error states, account tables and action buttons. |
| `src/main/resources/application.properties` | Removes the hardcoded database-password fallback and makes schema bootstrap mode configurable with `DB_SCHEMA_INIT_MODE`. Still uses MySQL and the existing `DB_URL`, `DB_USER`, `DB_PASSWORD` variables. |
| `src/test/resources/application-test.properties` | Removes the hardcoded test password fallback and accepts `TEST_DB_PASSWORD` or `DB_PASSWORD`. |
| `docker-compose.yml` | Removes insecure default database/admin passwords from local Compose configuration and passes the schema bootstrap option. |
| `.env.example` | Replaces password-looking example values with placeholders and documents the environment values. |
| `.gitignore` | Adds ignores for local `.env` values, IDE files and build outputs. |
| `pom.xml` | Updates only the old Sprint 1 project description. Dependencies and Java version are unchanged. |
| `render.yaml` | Keeps Docker + Aiven MySQL and sets `DB_SCHEMA_INIT_MODE=never` for the existing production database. |
| `src/main/java/com/deepu/securebank/security/SecurityConfig.java` | Serializes security error JSON with Jackson instead of hand-building JSON, so quotes/control characters in a request path cannot break the JSON response. |

### New file in this ZIP

- `docs/DEPLOYMENT_GUIDE.md`: MySQL-only steps for applying, testing and deploying this patch on Render with Aiven MySQL.

## Very important: credential exposure check

The ZIP you supplied contains `.idea/workspace.xml` with an Aiven JDBC connection string that appears to include credentials. The file is tracked in the Git metadata included in that ZIP and has a commit history. **Treat the database password as exposed.** Do not copy `.idea/workspace.xml` into any new commit or share it in screenshots.

Before making the repository public or restarting the service:

1. Rotate the Aiven database password in the Aiven console.
2. Update `DB_PASSWORD` in Render with the new password. Keep the same host, port, database, and JDBC SSL option unless Aiven shows they changed.
3. In your existing local repository, remove tracked IDE workspace/project files from Git's index (the files remain on your PC):

   ```bash
   git rm --cached -r .idea
   git rm --cached securebank.iml src/main/main.iml src/test/test.iml
   git status
   ```

   If one of those project-file paths does not exist in your checkout, omit that path from the command. `.gitignore` does not stop Git tracking files it already tracks.
4. Understand that a normal commit does **not** delete past copies from Git history. If the repo is public, consider temporarily making it private and cleaning the history with a suitable history-rewrite process. Rotate the password regardless of whether history is rewritten.

Do not put real Aiven passwords, admin passwords or JWT secrets in this patch, README, screenshots or GitHub.

## After copying the patch

From the existing repository root:

```bash
git status
```

Review the file list. Confirm that no `.env`, `.idea/workspace.xml`, database password or JWT secret is staged. Check the MySQL configuration as described in `docs/DEPLOYMENT_GUIDE.md`.

If Maven and a separate local test database are configured, run:

```bash
mvn clean test
```

Integration tests can delete rows from the database named `securebank_test`; **never point `TEST_DB_URL` at the production Aiven database**. If you do not have a separate test database, at least run a build with `mvn -DskipTests package`, then verify the service in Render logs after deployment.

Once the checks and credential steps are complete:

```bash
git add .
git commit -m "Refresh SecureBank banking UI"
git push origin main
```

Use your actual working branch instead of `main` if different. Review `git status` one final time before pushing.

## How to confirm the first-page fix

After Render deploys the commit, open the root URL of your service, not `/dashboard.html`. The root page should have the new SecureBank branding, “Your everyday banking, made simpler.” and the Sign in / Open an account tabs. The old “Sprint 1: Register / Login” heading should be gone. If the old style is cached, hard-refresh with `Ctrl+Shift+R`.

This patch has not been deployed to your Render service from here. Complete the steps in the deployment guide and use Render's logs to verify the actual running build.
