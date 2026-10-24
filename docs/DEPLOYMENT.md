# BattleTrust Engine — production deployment guide

This is a release checklist and Dockerfile strategy, not a record of a completed cloud deployment. The application has local verification; live SMTP/Cloudinary credentials, a reviewed database baseline, infrastructure validation, and payment-provider integration remain deployment responsibilities.

## 1. Choose the topology

Deploy the Next.js project to Vercel with root directory `frontend`. Run the Spring Boot backend as a long-lived container on Railway, Render, or AWS ECS/Fargate behind an HTTPS load balancer. Use managed MySQL 8, such as RDS, with private network access and verified TLS.

Use custom domains such as `app.example.com` and `api.example.com`, both HTTPS. Refresh cookies currently use `SameSite=Strict`, `HttpOnly`, a host-scoped API cookie, and `Secure` in production. Unrelated default hosting domains are cross-site and can prevent refresh-cookie delivery even when CORS allows them. Do not assume a Vercel preview URL and a provider API URL form a working persistent-login deployment.

Start with one backend replica because SSE fan-out and rate-limit counters are process-local. Before adding replicas, implement shared room pub/sub, distributed rate limiting, and cross-instance/reconnect tests. A load balancer alone does not provide shared chat delivery.

## 2. Map environment variables

Inject secrets through the host's secret manager. Do not package `.env` into an image or upload secrets to frontend hosting settings.

### Backend runtime

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
APP_WEB_ORIGIN=https://app.example.com
DB_URL=jdbc:mysql://db.internal:3306/bte?sslMode=VERIFY_IDENTITY&serverTimezone=UTC
DB_USERNAME=bte_app
DB_PASSWORD=<secret>
JWT_SECRET=<base64-encoded-random-key-at-least-32-decoded-bytes>
JWT_ISSUER=battletrust-engine
JWT_ACCESS_EXPIRATION_MS=900000
ADMIN_EMAIL=<trusted-bootstrap-admin-email>
ADMIN_PASSWORD=<unique-strong-bootstrap-secret>
DEV_SEED_DATA=false
PAYMENT_SIMULATION_ENABLED=false
MEDIA_PROVIDER=cloudinary
CLOUDINARY_CLOUD_NAME=<cloud-name>
CLOUDINARY_API_KEY=<api-key>
CLOUDINARY_API_SECRET=<secret>
SPRING_MAIL_HOST=<smtp-host>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<smtp-username-or-sender-email>
SPRING_MAIL_PASSWORD=<smtp-secret>
GOOGLE_CLIENT_ID=<optional-provider-client-id>
GOOGLE_CLIENT_SECRET=<optional-provider-secret>
SPRING_JPA_OPEN_IN_VIEW=false
SERVER_FORWARD_HEADERS_STRATEGY=framework
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

`SERVER_PORT` uses Spring's standard property binding; align the host's configured container port with it. Do not assume a provider's generic `PORT` variable overrides this application's YAML. For example, AWS App Runner manages `PORT` itself; configure its service port rather than creating a user variable named `PORT`. See [AWS environment configuration](https://docs.aws.amazon.com/apprunner/latest/dg/env-variable-manage.html).

Generate JWT key material with a cryptographically secure tool, for example `openssl rand -base64 32`. Store it as a secret, never in a command log or commit. Stable keys preserve valid sessions across replicas; plan rotation and session invalidation explicitly.

The runtime database identity should have only necessary application privileges. Use a separate migration identity with DDL privileges. Trust the database provider's CA and use a hostname matching its certificate; do not copy development's `useSSL=false` or `allowPublicKeyRetrieval=true` into production.

`ADMIN_DEFAULT_EMAIL` / `ADMIN_DEFAULT_PASSWORD` are supported aliases, but use one naming convention. Bootstrap settings do not rotate an existing administrator's password. Establish a verified recovery/rotation procedure before launch.

Optional business settings include `ESCROW_WINNERS_PERCENT`, `ESCROW_LEADER_PERCENT`, `ESCROW_PLATFORM_PERCENT`, `WALLET_MINIMUM_WITHDRAWAL`, and `WALLET_MAXIMUM_WITHDRAWAL`. Review persisted admin finance/staking policy as well: changing startup defaults does not rewrite existing rooms' snapshots.

### Frontend build environment

```dotenv
NEXT_PUBLIC_API_URL=https://api.example.com
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<same-public-google-client-id>
```

Public Next.js variables are embedded during the build; changing the API URL requires rebuilding/redeploying the frontend. See [Next.js environment variable behavior](https://nextjs.org/docs/pages/guides/environment-variables) and [Vercel environment configuration](https://vercel.com/docs/environment-variables). Configure production and preview environments separately, with isolated databases and approved origins.

## 3. Establish and migrate the database

- [ ] Take an encrypted backup and demonstrate restoration to a separate instance before modifying a deployed database.
- [ ] Identify the source schema version. The repository contains `V20261024_03__team_finance.sql` and `V20261024_04__email_verification.sql`, and `V20261025_05__squad_invites_room_controls.sql`, `V20261025_06__multi_match_operations.sql`, and `V20261025_07__organizer_account_controls.sql`; they assume the preceding staking/voucher schema. They are not an empty-database bootstrap.
- [ ] For a new environment, produce a reviewed, versioned baseline covering all current entities, constraints, indexes, and required system rows. Generate candidate DDL only against a disposable database, inspect it, and test it from empty. Do not use `ddl-auto=update` against production as a substitute.
- [ ] Choose and configure an explicit migration runner/job with version history and checksums. Flyway/Liquibase automatic execution is not installed in the current app.
- [ ] For an existing compatible schema, run `_03` through `_07` in version order exactly once under that runner. Do not rerun these non-idempotent scripts or apply them after Hibernate already created equivalent columns.
- [ ] Test the migration against a restored staging copy, including row counts, voucher relationships, unique constraints, fee policy, balances, and ledger totals.
- [ ] Plan verification onboarding for legacy users. `_04` makes accounts unverified by default; only the configured trusted bootstrap administrator is verified by initialization. Do not bulk-mark player accounts verified.
- [ ] Run the backend with `prod` and confirm `ddl-auto=validate` succeeds. Establish an expand/contract rollout or maintenance window where schema changes are incompatible.

## 4. Backend Dockerfile strategy

Use a multi-stage Java 25 image: compile in a JDK builder, then copy only the repackaged Spring Boot JAR into a non-root runtime. Docker describes this separation in its [multi-stage build guide](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/).

The following is a **template to validate in CI**, with build context `backend/BattleTrust-Engine`. Select supported Java 25 images and pin approved digests before a release; this document does not assert a Docker build has been executed.

```dockerfile
# syntax=docker/dockerfile:1
ARG JAVA_BUILD_IMAGE=eclipse-temurin:25-jdk
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:25-jre
FROM ${JAVA_BUILD_IMAGE} AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
# Unit/integration tests run in the separate CI job with disposable MySQL.
RUN ./mvnw -B -DskipTests package

FROM ${JAVA_RUNTIME_IMAGE} AS runtime
WORKDIR /app
COPY --from=build /workspace/target/BattleTrust-Engine-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Verify the JAR filename when the Maven artifact version changes. The runtime must provide a writable temporary directory for Java/image handling. Cloudinary storage avoids dependence on container-local uploads; if local storage is intentionally selected, provision durable storage and ownership for UID 10001.

Include a backend `.dockerignore` before building:

```dockerignore
.env
.env.*
!.env.example
.git
target
uploads
*.log
```

Example commands, after adding and validating the Dockerfile:

```sh
docker build -t battletrust-api:release backend/BattleTrust-Engine
docker run --rm -p 8080:8080 --env-file /secure/path/backend.runtime.env battletrust-api:release
```

Never run the image without explicitly selecting `prod`: the repository's base profile defaults to `dev`. In CI, run tests first, scan the dependency/image inventory, generate an SBOM, and publish an immutable image digest. Configure memory/CPU limits, graceful termination, JVM container memory headroom, and a non-root writable temp directory.

Vercel does not need a frontend Dockerfile. If self-hosting Next.js instead, validate its documented standalone output configuration and build a separate Node runtime image; pass public API/client-ID values at build time. Do not reuse development `npm run dev` for production.

## 5. Configure HTTPS, cookies, CORS, and SSE

- [ ] Terminate TLS at the platform ingress/load balancer, redirect HTTP to HTTPS, and issue valid certificates for both custom domains.
- [ ] Set `APP_WEB_ORIGIN` to the exact frontend origin, with scheme and no trailing path. Keep credentialed CORS restricted to it; never use `*` for authenticated traffic.
- [ ] Confirm the browser sends the refresh cookie to the API after login and refresh on the same-site custom domains. Verify Secure/HttpOnly/Strict attributes, rotation, logout revocation, and Origin rejection with real browsers.
- [ ] Set Google authorized JavaScript origins to the deployed frontend origin. Validate the provider configuration against the actual ID-token flow; do not invent a redirect endpoint.
- [ ] Trust forwarded headers only through controlled infrastructure. Ensure the ingress replaces spoofable client headers and supports the application's origin/security behavior.
- [ ] For SSE paths, disable proxy response buffering, allow long-lived responses, and test idle timeouts, reconnect, room authorization, and connection limits through the actual ingress.
- [ ] Limit uploads consistently at ingress and application layers: 5 MB files and 6 MB multipart requests in the current profiles.
- [ ] Keep `/actuator/**` protected. Existing health endpoints require admin authority, so do not configure an anonymous HTTP health probe expecting 200. Use a TCP/startup probe initially, or implement a minimal authenticated/private readiness strategy without publishing metrics/secrets.
- [ ] Restrict or disable Swagger/OpenAPI publicly. Do not expose private tickets, verification URLs, JWTs, cookies, or SMTP credentials in logs, tracing, or error reporting.

## 6. Validate live integrations

- [ ] Replace all Cloudinary/SMTP placeholders and confirm startup with production configuration. Cloudinary setup fails fast on missing placeholders; signup fails closed when verification delivery is unavailable.
- [ ] Upload an avatar, banner, and proof with real credentials; verify HTTPS retrieval and rejection of invalid/oversized files. Define retention, deletion, and orphan-upload cleanup.
- [ ] Current evidence URLs are public delivery URLs. Implement authenticated or signed private delivery and review authorization before storing sensitive proof for a public launch.
- [ ] Verify email delivery, expiry, resend throttling, token replay rejection, and unverified login/refresh rejection. Configure sender-domain SPF/DKIM/DMARC where supported by the mail provider.
- [ ] Add a durable transactional outbox and retry/dead-letter handling before relying on email delivery at scale. Current verification delivery is synchronous and not atomically coupled to external SMTP acceptance.
- [ ] Confirm production has no simulation controller, test deposits, development seeds, or public fixture data. False simulation flags alone are not a substitute for checking the `prod` profile.
- [ ] Before accepting real deposits, integrate an authorized payment provider, verify signed webhook receipts, reconcile transactions, and define payout/failure/refund handling. The simulator supplies no external proof of funds.

## 7. Release, observe, and roll back

- [ ] Gate releases on backend tests, TypeScript checks, a frontend production build, migration validation, and staging browser/API checks.
- [ ] Exercise concurrent payment retries, capacity limits, cross-room authorization, exact payout sums, settlement replay, stake refund/forfeit, withdrawal reserves, and disputed-result gating against staging.
- [ ] Run production-like load tests before claiming throughput or scaling capacity. The local 20-room correctness scenarios are not load-test results.
- [ ] Establish ledger/vault/wallet reconciliation, database backups and restore drills, audit access controls, log retention, and immutable external audit exports as required. Application append-only ledger behavior does not prevent a privileged database operator from changing rows.
- [ ] Monitor database pool/lock waits, request latency/errors, SSE connections, SMTP failures, Cloudinary failures, outstanding disputes, stale vouchers, and pending payouts. Alert without recording private capabilities.
- [ ] Record the backend image digest, frontend deployment, schema version, and policy configuration for each release. Keep a known-good application release available.
- [ ] Roll back application images only when compatible with the current schema. Destructive migration recovery requires a tested forward repair or coordinated restore/reconciliation plan; do not blindly run reverse DDL over financial history.
- [ ] Smoke-test the deployed custom domains and recovery procedure before opening registrations.
