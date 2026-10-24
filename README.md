<div align="center">

# ⚡ BattleTrust Engine

### Compete with confidence. Settle with accountability.

An esports tournament platform combining squad registration, isolated tournament escrow, organizer security stakes, and auditable settlement workflows.

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-25-ED8B00)
![Next.js](https://img.shields.io/badge/Next.js-16-000000?logo=nextdotjs)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![Tailwind](https://img.shields.io/badge/Tailwind_CSS-4-06B6D4?logo=tailwindcss&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Validation](https://img.shields.io/badge/Local_backend_tests-67_passed-00AA77)

[Features](#core-features) · [Architecture](#architecture) · [Local setup](#local-setup) · [Deployment](docs/DEPLOYMENT.md) · [Operations](RUNBOOK.md)

</div>

## Built for the full tournament lifecycle

BattleTrust brings players, squad captains, organizers, and administrators into one competitive gaming workflow: discover a room, register a squad, fund its entry, unlock private lobby access, submit evidence, resolve disputes, and distribute winnings into individual wallets.

The interface uses a dark esports aesthetic with cyan accents, responsive tournament discovery, and dedicated player, organizer, and administrator panels.

**Scope:** the 1Bill engine is a self-managed simulator. It does not connect to official 1Bill, Easypaisa, JazzCash, or bank networks. Local verification is complete for the checks listed below; public deployment still requires the controls in the [deployment guide](docs/DEPLOYMENT.md).

## Core features

### Tournament and squad operations
- Use the dedicated `/organizer` command dashboard, private/public rooms, room invite QR codes, and organizer chat moderation.

- Create and manage rooms with configurable capacity, entry fees, prize shares, and confidential lobby credentials.
- Register as captain with your own IGN/UID, then share a squad invite link/QR so teammates claim seats themselves.
- Prepare unique 10-digit voucher inventory when rooms are created. A 100-player, four-player-squad room has 25 invoices.
- Bind one combined invoice to a squad and assign a stable slot only after payment confirmation.
- Enforce capacity, start-time registration locks, voucher expiry, and payment replay protection.
- Show at most two active/upcoming rooms on the home page; browse all rooms at `/tournaments` with search, game, and status filters.

### Escrow, staking, and settlement

- Partition player funds by tournament, keeping concurrent room pools separate from organizer stake principal.
- Switch organizer staking on/off administratively; reserve security deposits during room creation and refund, release, or forfeit them according to the cancellation/settlement rules.
- Snapshot financial policy per room and allocate platform fees, organizer commission, and winning-team payouts transactionally.
- Split team prizes among individual member wallets using deterministic cent allocation.
- Record append-only application ledger entries for deposits, escrow holds, stakes, commissions, refunds, winnings, and account transfers.
- Protect financial transitions with database transactions, pessimistic row locks, post-lock state checks, and unique references/constraints.
- Reserve funds for withdrawal requests; administrator approval records external payout completion. The app does not execute bank transfers.

### Identity and private access

- Enforce SMTP email verification before password login, refresh, or authenticated access.
- Use expiring, single-use verification links with hashed tokens and a resend cooldown.
- Support Google sign-in after validating the provider's identity and verified-email claim.
- Use JWT access tokens, rotating HttpOnly refresh cookies, server-side session revocation, and role/ownership authorization.
- Allow guest registration with private entry tickets; link funds, history, and statistics to a verified profile through explicit private capabilities.
- Gate private SSE chat, lobby secrets, and match access by paid membership or organizer/admin authority. Email matching alone cannot claim guest funds.

### Evidence and administration

- Upload player avatars, tournament banners, and match/dispute proof through a backend Cloudinary storage adapter.
- Validate file size, MIME type, decoded image format, and dimensions before storage; keep API secrets server-side.
- Review result evidence and dispute windows, then explicitly release or schedule organizer-declared top-3 payouts as an Admin operator.
- Monitor wallets, treasury, policy controls, users, and protected Actuator endpoints from the admin command center.

## Technology stack

- **Backend:** Java 25, Spring Boot 4.1.2, Spring MVC, Spring Security, Spring Data JPA/Hibernate, Jakarta Validation, JavaMailSender, JJWT, Springdoc OpenAPI, Actuator.
- **Frontend:** Next.js 16 App Router, React 19, TypeScript 5, Tailwind CSS 4, Framer Motion, Lucide icons.
- **Client state:** TanStack Query for server data, Zustand for session state, Axios for authenticated requests and refresh handling.
- **Persistence:** MySQL 8 with transactional financial state and tournament-scoped ledger records.
- **Media and communication:** Cloudinary Java SDK, SMTP verification, private Server-Sent Events chat.
- **Verification:** Maven/JUnit/Mockito tests, TypeScript checks, production frontend builds, and local API smoke scripts.

## Architecture

The Next.js application calls the Spring MVC REST API through a shared Axios client. Authentication issues short-lived access tokens and rotates refresh credentials through secure cookies. Domain services own tournament, registration, wallet, staking, evidence, and settlement rules; JPA repositories persist state in MySQL.

Payment confirmation locks the room and registration before reserving a slot and crediting that room's vault. Settlement locks the relevant financial state, applies the room's snapshotted fee policy, distributes winnings, releases eligible organizer stake, and records audit entries within the transaction. Repeated confirmations and settlements cannot legitimately apply the same financial transition twice.

Cloudinary receives server-authenticated uploads. SMTP delivers private verification links. SSE streams are authorized per room and currently fan out within one backend process; horizontal scaling requires shared messaging.

### Repository layout

```text
backend/BattleTrust-Engine/
  src/main/java/com/bte/       Controllers, domain services, entities, security
  src/main/resources/         Base, dev, and prod configuration
  src/test/                   Backend tests
  db/migrations/              Incremental MySQL upgrade scripts
frontend/
  src/app/                    Home, dashboard, tournaments, verification routes
  src/components/             Player, organizer, admin, payment, and media UI
  src/lib/                    API client and shared contracts
  src/stores/                 Session state
  openapi.json                Backend contract snapshot
scripts/                      Startup helpers and local integration checks
docs/                         Deployment guide and portfolio copy
RUNBOOK.md                    Detailed operational behavior
```

## Local setup

### 1. Install prerequisites

Install **JDK 25**, **Node.js 20.9+**, npm, and **MySQL 8**. Maven is provided through the backend wrapper. Create a development database and a database user with permissions on it:

```sql
CREATE DATABASE bte CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Spring Docker Compose auto-start is disabled. Start MySQL separately.

### 2. Configure the backend

From the repository root, copy `backend/BattleTrust-Engine/.env.example` to `backend/BattleTrust-Engine/.env`. Set:

```dotenv
DB_URL=jdbc:mysql://localhost:3306/bte?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=your_local_db_user
DB_PASSWORD=your_local_db_password
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=replace_with_a_unique_strong_password
DEV_SEED_DATA=false
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email_here
SPRING_MAIL_PASSWORD=your_app_password_here
MEDIA_PROVIDER=local
```

The administrator password must be at least 12 characters and at most 72 UTF-8 bytes. The initializer creates the configured admin when appropriate; it does not reset an existing admin password or silently promote a conflicting non-admin account. Automatic demo-room and test-fund seeding has been removed; new rooms come from organizer actions.

Values use Java properties format: `KEY=value`, without `export` or shell quotes. Launch from the backend directory so Spring can import `.env`.

**Email is mandatory for signup.** Replace the SMTP placeholders with working credentials. `MAIL_ENABLED=false` only disables optional notifications; it does not bypass verification. Unconfigured SMTP safely rejects registration. For isolated testing, use the local SMTP capture procedure in [RUNBOOK.md](RUNBOOK.md#local-email-integration-checks).

To activate Cloudinary, set these backend-only values and restart:

```dotenv
MEDIA_PROVIDER=cloudinary
CLOUDINARY_CLOUD_NAME=your_cloud_name_here
CLOUDINARY_API_KEY=your_api_key_here
CLOUDINARY_API_SECRET=your_api_secret_here
```

Replace all placeholders with real values. Local development can continue with local media storage. For Google sign-in, configure backend `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`; expose only the public client ID to the frontend.

### 3. Configure the frontend

Copy `frontend/.env.example` to `frontend/.env.local`:

```dotenv
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_GOOGLE_CLIENT_ID=
```

Only set the Google client ID if Google sign-in is configured. Never prefix database, JWT, SMTP, or Cloudinary secrets with `NEXT_PUBLIC_`.

### 4. Start both applications

Backend terminal:

```powershell
cd backend/BattleTrust-Engine
./mvnw.cmd spring-boot:run
```

On Linux/macOS, use `./mvnw spring-boot:run`. Frontend terminal:

```sh
cd frontend
npm ci
npm run dev
```

Windows convenience helpers are available at `scripts/start-dev.ps1` and `scripts/run-backend.ps1`; the latter handles the Java 25 Windows local socket path issue observed in this workspace.

- **App:** http://localhost:3000
- **Tournaments:** http://localhost:3000/tournaments
- **Backend:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/openapi/swagger-ui.html
- **OpenAPI:** http://localhost:8080/openapi/v3/api-docs

### 5. Exercise a room

1. Sign in using your configured administrator account or register, verify your email, and sign in as a player.
2. Choose Organizer during verified email signup (or have an admin grant organizer access), then configure staking/financial policy as an administrator.
3. Create a room and register a squad. Keep private guest/member claim codes confidential.
4. In development, simulate the voucher payment using the authorized payment panel. Confirm the slot and unlock the lobby/chat.
5. Submit results, complete review/dispute handling, and settle the room. Inspect individual wallets and ledger entries.

`POST /api/v1/payments/simulate-pay/{voucherId}` is available only in the `dev` profile with simulation enabled and requires entry access. Its controller is absent in `prod`.

## Validation
Latest Admin/Organizer refinements use basic compile and focused smoke validation only; extensive QA is deferred. Older financial smoke scripts require updated expectations for explicit top-3 Admin release. See RUNBOOK for current policy.


Backend, from its directory:

```powershell
./mvnw.cmd test
```

Frontend, from its directory:

```sh
npm run typecheck
npm run build
```

Local integration scripts, from the repository root:

```sh
node scripts/email-media-smoke.mjs
node scripts/smoke.mjs
node scripts/staking-smoke.mjs
node scripts/team-finance-smoke.mjs
```

These scripts create clearly labelled test records and simulated financial transactions. Run only against an isolated local database with the documented local SMTP capture configuration. The team-finance script also needs the local MySQL CLI for its labelled review-window fixture.

**Recorded local verification:** 67 backend tests, 26 general integration checks, verification/upload smoke checks, and a successful production frontend build. Dedicated financial scripts exercise concurrent payment retries and 20 isolated funded rooms; this is not a throughput or production load benchmark. Live Cloudinary/Gmail delivery and mobile visual QA were not verified with the placeholder credentials/browser available in this workspace.

## Deploying responsibly

See [the production checklist and Docker strategy](docs/DEPLOYMENT.md) for secrets, migrations, HTTPS, same-site cookies, CORS, SSE, rollback, and release gates. The SQL directory contains incremental upgrades rather than a complete fresh-install baseline. Real payment ingestion, durable email delivery, shared multi-instance messaging, and private evidence delivery require additional deployment work before a public financial launch.

## Portfolio

Ready-to-use engineering achievement bullets are available in [docs/PORTFOLIO.md](docs/PORTFOLIO.md).
# battletrust-engine
