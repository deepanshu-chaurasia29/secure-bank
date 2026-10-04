# SecureBank — Sprint 1

**What this sprint delivers:**
- Project setup (Spring Boot 3, Java 21, Maven, JdbcTemplate)
- `schema.sql` — all 4 tables from the design (users, accounts, transactions, audit_log)
- Register (creates a user + one account, BCrypt password, JWT returned)
- Login (checks password, 3-attempt lockout for 15 minutes, JWT returned)
- Global error handling in one consistent JSON shape
- A tiny test HTML page to try register/login in a browser

**Not yet built (later sprints):** deposit/withdraw, transfer, history, admin panel, Swagger fully wired, Docker, deployment.

## How to run locally

1. **Create the database** (or let `schema.sql` do it — it has `CREATE DATABASE IF NOT EXISTS`):
   Make sure MySQL is running locally.

2. **Set your local DB password.** Either:
   - Edit `src/main/resources/application.properties` directly, or
   - Set an environment variable before running:
     ```
     set DB_PASSWORD=yourpassword   (Windows)
     export DB_PASSWORD=yourpassword (Mac/Linux)
     ```

3. **Run the app:**
   ```
   mvn spring-boot:run
   ```
   On first run, `schema.sql` creates the database and tables automatically.

4. **Test it:**
   - Open `http://localhost:8080` in your browser — a simple Register/Login form.
   - Or use Postman:
     - `POST http://localhost:8080/api/v1/auth/register`
       ```json
       {
         "fullName": "Deepu Test",
         "email": "deepu@test.com",
         "phone": "9876543210",
         "dateOfBirth": "2003-05-10",
         "password": "Test1234",
         "accountType": "SAVINGS"
       }
       ```
     - `POST http://localhost:8080/api/v1/auth/login`
       ```json
       { "email": "deepu@test.com", "password": "Test1234" }
       ```
   - Both return a JWT token, your account number, name, and role.

## Notes / things to double check
- `application.properties` has `spring.sql.init.mode=always` so schema.sql re-runs every start (safe because of `CREATE TABLE IF NOT EXISTS`). Turn this off once you don't want it running every time.
- The default JWT secret in `application.properties` is a placeholder — fine for local testing, but **must** be replaced with a real secret via the `JWT_SECRET` environment variable before deployment.
- `.gitignore` should include `target/` and any local `.env` file — **never commit real DB passwords or the JWT secret.**

## Next: Sprint 2
Deposit, withdraw, balance, and paginated transaction history.
