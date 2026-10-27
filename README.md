# StayTrack — Dormitory Management System (Java / Spring Boot backend)

A complete **Java** backend for the StayTrack resident portal, built with **Spring Boot 3**.
It includes **login / signup with email OTP verification (Gmail)** and an **admin console**
to manage requests, payments and tenant accounts.

The front-end is plain **HTML + CSS + JavaScript** (in `src/main/resources/static/`), served
directly by the backend — no separate front-end build step.

---

## 1. Tech stack

| Layer     | Technology                                                   |
| --------- | ------------------------------------------------------------ |
| Language  | Java 17                                                      |
| Framework | Spring Boot 3.2 (Web, Data JPA, Security, Validation, Mail)  |
| Database  | PostgreSQL                                                   |
| Auth      | JWT (jjwt) + BCrypt password hashing                         |
| Email     | Spring Mail → Gmail SMTP (App Password)                      |
| Build     | Maven (wrapper included — no Maven install needed)           |
| Tests     | JUnit 5 + MockMvc on an in-memory H2 database                |

---

## 2. Project structure

```
staytrack-java/
├── pom.xml
├── mvnw / mvnw.cmd                  ← Maven wrapper (Windows uses mvnw.cmd)
├── src/main/resources/
│   ├── application.properties       ← all configuration lives here
│   ├── schema.sql                   ← the PostgreSQL database file (auto-runs)
│   └── static/                      ← the front-end (HTML/CSS/JS)
│       ├── index.html   login.html   signup.html
│       ├── reset-password.html       admin.html
│       ├── css/app.css
│       └── js/api.js  resident.js  admin.js
├── src/main/java/com/staytrack/
│   ├── StayTrackApplication.java    ← main() entry point
│   ├── config/                      ← Security, CORS, uploads, seeding
│   ├── security/                    ← JWT service + auth filter
│   ├── domain/                      ← JPA entities
│   ├── repository/                  ← Spring Data repositories
│   ├── dto/                         ← request bodies + JSON mapping
│   ├── service/                     ← business logic
│   ├── controller/                  ← REST controllers
│   └── exception/                   ← consistent JSON error handling
└── src/test/java/…                  ← integration tests
```

---

## 3. Prerequisites

- **JDK 17 or newer** — https://adoptium.net
- **PostgreSQL 14+** — https://www.postgresql.org/download (or hosted: Neon / Supabase / Railway)
- **VS Code** with the **Extension Pack for Java** (and optionally *Spring Boot Extension Pack*)

---

## 4. Quick start (VS Code)

Open the `staytrack-java` folder in VS Code, then open a terminal (`Ctrl + ` `):

```bash
# 1. Create the database (choose one)
psql -U postgres -c "CREATE DATABASE staytrack;"
#    …or in pgAdmin: right-click Databases → Create → name it "staytrack"

# 2. Tell the app how to connect — edit src/main/resources/application.properties:
#    spring.datasource.username=postgres
#    spring.datasource.password=YOUR_DB_PASSWORD

# 3. Run it
./mvnw spring-boot:run          # macOS / Linux
mvnw.cmd spring-boot:run        # Windows PowerShell / CMD
```

> On macOS/Linux, if `./mvnw` reports "permission denied", run `chmod +x mvnw` once.
> If you already have Maven installed, `mvn spring-boot:run` works too.

Then open **http://localhost:8080** (Spring Boot's default port — the old Node version used 4000).

### Even simpler: run from VS Code
Open `src/main/java/com/staytrack/StayTrackApplication.java` and click the **▶ Run** button
above `main()`. Or use the **Spring Boot Dashboard** panel in the sidebar.

The tables are created automatically from `schema.sql` on first start, and the **admin
account** is created once. **Nothing else is seeded** — no demo tenant, no demo rooms,
no sample payments and no sample service requests, so a fresh run starts with just your
admin login.

### Removing demo data that is still showing up

The seeder no longer creates demo data, but records created by an **earlier run are
still stored in PostgreSQL** — that is why they keep appearing. Delete them once with
the reset script:

**pgAdmin** — right-click the `staytrack` database → **Query Tool** → paste
`database/reset.sql` → press **F5**.

**Command line:**

```bash
psql -U postgres -d staytrack -f database/reset.sql
```

This deletes every record but keeps the tables. The demo data will not come back,
because the seeder no longer creates it.

### Accounts

The admin account comes from the `app.seed.admin-*` settings and is created **once** on
first start. After that the seeder never touches it again, so a password you change
inside the app survives restarts.

| Role  | Email                   | Password                            |
| ----- | ----------------------- | ----------------------------------- |
| Admin | `admin@staytrack.local` | as set in `app.seed.admin-password` |

> The login is **email based**, so `app.seed.admin-email` must be a valid email
> address (it needs an `@` and a domain). A bare username like `admin` will be
> rejected by the login form.

> For deployment, set the password via the `APP_SEED_ADMIN_PASSWORD` environment
> variable instead of keeping it in this file.

---

## 5. Sending OTPs through your Gmail (the part you asked about)

Gmail will not accept your normal account password from an application. You must create a
**Google App Password**. It takes about two minutes:

**Step 1 — Turn on 2-Step Verification**
1. Go to https://myaccount.google.com/security
2. Under "How you sign in to Google", enable **2-Step Verification**.
   *(App Passwords stay hidden until this is on.)*

**Step 2 — Create an App Password**
1. Go to https://myaccount.google.com/apppasswords
2. Name it `StayTrack` and click **Create**.
3. Google shows a **16-character password** (like `abcd efgh ijkl mnop`). Copy it.

**Step 3 — Put it in `application.properties`**
```properties
spring.mail.username=youremail@gmail.com
spring.mail.password=abcdefghijklmnop     # the 16-char App Password, spaces removed
app.mail.from-name=StayTrack
app.mail.from-email=youremail@gmail.com
```

**Step 4 — Restart.** You should see in the console:
```
[mail] SMTP connection verified — OTP emails are ready to send.
```
Now the 6-digit code is emailed to **whatever address the person typed at signup** (and for password reset, to the account's email) — it no longer appears in the terminal.

**Step 5 (optional but recommended) —** set `app.dev-show-otp=false` so codes are only ever emailed and never returned in the API response.

> If you restart and instead see the big `[mail] Gmail SMTP is NOT configured yet` warning,
> the username/password were blank or the password was wrong — re-check Step 3.

### Don't want to set up Gmail yet?
Leave `spring.mail.username` and `spring.mail.password` **empty** and keep
`app.dev-show-otp=true`. In development the app then:
- prints the code in the console, and
- returns it in the API response, so the signup page fills it in automatically.

The moment you add real Gmail credentials, genuine emails take over — no code change needed.

### Gmail troubleshooting
| Problem | Fix |
| --- | --- |
| `535-5.7.8 Username and Password not accepted` | You used your real password. Use the App Password. |
| `534-5.7.9` | 2-Step Verification isn't enabled, so no App Password exists yet. |
| App Passwords page returns 404 | A Workspace admin disabled them — use a personal Gmail, or any SMTP provider (same properties). |
| Nothing arrives | Check spam; `spring.mail.username` must be the account that created the App Password. |

---

## 6. How signup + OTP works

```
POST /api/auth/signup        → creates a PENDING user, emails a 6-digit code
POST /api/auth/verify-otp    → checks the code, activates the account, returns a JWT
POST /api/auth/resend-otp    → new code (60-second cooldown)
POST /api/auth/login         → rejects an unverified email with EMAIL_NOT_VERIFIED
```

Security details:
- Codes are stored **hashed with BCrypt** — the plain code is never saved.
- Codes **expire after 10 minutes** and allow **5 wrong attempts**.
- Resending invalidates all older codes.
- Wrong-attempt counters are committed even though the request fails, so brute-force
  attempts are actually counted.
- Login issues a **JWT** (default 7 days) sent as `Authorization: Bearer <token>`.

---

## 7. API reference

Base URL `http://localhost:8080/api`. Authenticated calls need the Bearer token.

### Auth (public)
| Method | Path | Body |
| --- | --- | --- |
| POST | `/auth/signup` | `{ firstName, lastName, email, phone, password }` — phone is required |
| POST | `/auth/verify-otp` | `{ email, code }` → `{ token, user }` |
| POST | `/auth/resend-otp` | `{ email, purpose? }` |
| POST | `/auth/login` | `{ email, password }` → `{ token, user }` |
| POST | `/auth/forgot-password` | `{ email }` |
| POST | `/auth/reset-password` | `{ email, code, password }` |

### Auth (signed in)
| Method | Path |
| --- | --- |
| GET | `/auth/me` |
| POST | `/auth/change-password` — `{ currentPassword, newPassword }` |

### Tenant
| Method | Path | Notes |
| --- | --- | --- |
| GET/PATCH | `/tenant/profile` | view / update name & phone |
| POST | `/tenant/avatar` | multipart `avatar` (image) — profile photo |
| GET | `/requests` | own requests |
| POST | `/requests` | multipart: `type, category, description, isAnonymous, photo?` |
| GET | `/requests/{id}` | one request |
| PATCH | `/requests/{id}/cancel` | cancel own request |
| DELETE | `/requests/{id}` | delete own request — allowed only once it is `resolved` |
| GET | `/payments` | own bills + `summary` + `nextDue` |
| GET | `/payments/{id}` | one bill |
| POST | `/payments/{id}/proof` | multipart: `proof`, `referenceNo` (required), `amountPaid?` |
| DELETE | `/payments/{id}` | delete own payment record — verified ones only |

### Admin (requires an ADMIN account)
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/admin/dashboard` | counts, outstanding & collected totals |
| GET | `/admin/tenants` | list / search (`?search=&status=`) |
| POST | `/admin/tenants` | create tenant (emails an OTP) |
| GET | `/admin/tenants/{id}` | detail + payments + requests |
| PATCH | `/admin/tenants/{id}` | `{ firstName?, lastName?, phone?, status?, roomId?, moveInDate? }` |
| DELETE | `/admin/tenants/{id}` | deactivate (soft, keeps history) |
| DELETE | `/admin/tenants/{id}/permanent` | permanently delete the tenant + all their records |
| GET/POST | `/admin/rooms`, PATCH/DELETE `/admin/rooms/{id}` | manage rooms; delete is refused while tenants are still assigned |
| GET | `/admin/requests` | `?status=&type=&search=` |
| PATCH | `/admin/requests/{id}` | `{ status, adminNotes? }` |
| DELETE | `/admin/requests/{id}` | delete a request — resolved ones only |
| GET | `/admin/payments` | `?status=&search=&period=` |
| POST | `/admin/payments` | create one bill |
| POST | `/admin/payments/generate` | monthly bills for all active tenants |
| PATCH | `/admin/payments/{id}/verify` | `{ amountPaid?, remarks? }` |
| PATCH | `/admin/payments/{id}/reject` | `{ remarks? }` |
| POST | `/admin/payments/{id}/remind` | email that tenant a rent-due reminder |
| DELETE | `/admin/payments/{id}` | delete an approved (verified) payment record |
| GET | `/admin/audit-logs` | recent admin actions |

**Billing a tenant:** in the admin console → **Payments** tab, click **+ Add bill** (or click **Add bill** on a row in **Tenant Accounts**). Choose the billing period, due date, rent, utilities and other charges — the rent can be left blank to use the room's monthly rent. The new due appears immediately on the tenant's dashboard and Pay page. The separate **Generate monthly bills** button instead creates one bill for *every* active tenant that has a room assigned.

**Payment states:** a bill counts as *owed* while it is `unpaid`, `submitted` (proof awaiting review), `overdue`, or `rejected` — **only `verified` clears it**. So a tenant who has uploaded proof still sees the bill and is never shown as "all caught up" until an admin verifies it. Any note the admin adds to a payment — whether while **rejecting** it (the reason) or while **verifying** it — is displayed to the tenant on that bill's card in their **My Payments** list. Admin notes are deliberately **not** shown on the Home dashboard, so an older note can never keep lingering there after a payment is approved and a new bill is added.

The tenant's **Pay page** ends with a **My Payments** list — one card per bill showing the period, amounts, reference number, status badge and the admin's note at the bottom of each card (mirroring the My Requests list). Verified records show a **Delete record** button that asks the tenant to type their StayTrack email address before deleting; unpaid or in-review records can never be deleted this way.

**Cleaning up payments:** approved (verified) rows show a **Delete** button that opens a confirmation dialog listing the tenant, period, amount and reference before anything is removed. Unpaid or awaiting-verification bills can never be deleted this way, so an outstanding debt or an unreviewed proof cannot be lost by mistake.

**Rent reminders:** the admin console's Payments table has a **Remind** button on each unpaid/overdue/rejected bill, which emails that tenant a due-date reminder through the same Gmail setup. The table also shows the **reference number** the tenant entered when uploading their payment proof.

**Deleting requests:** a resolved request shows a **Delete** button on both sides — the tenant's My Requests list and the admin console's Requests tab. Both open a confirmation dialog first, and the API refuses anything that is not yet `resolved`, so an open complaint can never be removed by accident.

**Request notes:** when an admin updates a request (status and/or a note) in the admin console's **Requests** tab, that note is shown to the tenant under **My Requests** on their Requests page, and on the dashboard's Active Request card.

Errors are always JSON: `{ "error": "message", "details": { "field": "…" } }`.

---

## 8. Database

The schema file is **`src/main/resources/schema.sql`**. It runs automatically on every start
(`spring.sql.init.mode=always`) and is safe to re-run (`CREATE TABLE IF NOT EXISTS`, etc.).

| Table | Purpose |
| --- | --- |
| `users` | tenants + admins, role, status, password hash, email-verified flag |
| `rooms` | room number, building, rent, capacity, status |
| `tenant_profiles` | links a tenant to a room + emergency contact |
| `otp_codes` | hashed OTPs with expiry, attempts, purpose |
| `service_requests` | maintenance & neighbor reports with status workflow |
| `payments` | monthly bills, `total_amount`, proof, verification |
| `notifications` | in-app messages for tenants |
| `audit_logs` | who did what (admin actions) |
| `v_tenant_overview` | view joining tenants + room + outstanding balance |

Status flows:
- **Request:** `PENDING → IN_PROGRESS → RESOLVED` (or `REJECTED` / `CANCELLED`)
- **Payment:** `UNPAID → SUBMITTED → VERIFIED` (or `REJECTED`), plus `OVERDUE`

> If your database user cannot create extensions and startup fails on
> `CREATE EXTENSION IF NOT EXISTS pgcrypto;`, simply delete that line —
> the application generates all UUIDs itself.

---

## 9. Configuration cheat-sheet (`application.properties`)

| Key | Meaning |
| --- | --- |
| `server.port` | HTTP port (default `8080`) |
| `spring.datasource.url` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/staytrack` |
| `spring.datasource.username` / `password` | database credentials |
| `spring.mail.username` / `password` | Gmail address + App Password |
| `app.dev-show-otp` | return/print OTP when SMTP is unset (keep `true` in dev) |
| `app.jwt.secret` | must be ≥ 32 characters |
| `app.otp.ttl-minutes` / `max-attempts` / `resend-cooldown-seconds` | OTP policy |
| `app.upload-dir` / `app.max-upload-mb` | uploaded proof/photo settings |
| `app.cors-origins` | extra origins allowed to call the API |
| `app.seed.enabled` | create the admin account on start (set `false` to skip it) |
| `app.seed.admin-email` / `admin-password` | the admin login (the email must be a valid address) |

You can also override any setting with an environment variable, e.g.
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_PASSWORD`, `APP_JWT_SECRET`.

Generate a strong JWT secret:
```bash
openssl rand -hex 32
```

---

## 10. Running the tests

```bash
./mvnw test        # macOS / Linux
mvnw.cmd test      # Windows
```

The integration tests run the full Spring context on an **in-memory H2** database and verify:
signup → OTP verification → login → `/me`, a tenant submitting a request, and the admin
dashboard / billing / role-based access rules.

---

## 11. Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Connection to localhost:5432 refused` | PostgreSQL isn't running, or the URL/port is wrong. |
| `FATAL: password authentication failed` | Wrong `spring.datasource.password`. |
| `FATAL: database "staytrack" does not exist` | Create it: `psql -U postgres -c "CREATE DATABASE staytrack;"` |
| `app.jwt.secret must be at least 32 characters` | Put a longer value in `application.properties`. |
| Port 8080 already in use | Set `server.port=8081` in `application.properties`. |
| OTP not emailed | SMTP not configured yet — use the console/dev OTP, or fill in Gmail details (§5). |
| CORS error in the browser | The front-end is served by this app, so this only matters if you use a separate dev server — add its origin to `app.cors-origins`. |

---

Enjoy StayTrack! 🏠
