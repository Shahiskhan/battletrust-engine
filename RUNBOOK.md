# BattleTrust Engine local operations

## Operator-only Admin and reviewed top-3 releases (current policy)

Admin is an operator, not a playable role. Both `/dashboard` and `/organizer` render the Admin shell exclusively for `ROLE_ADMIN`. The Admin identity response omits gamer statistics and avatar; personal wallet, withdrawal, avatar, registration/claim, squad-seat and history APIs reject Admin. Creating tournaments is organizer-only. Historical wallets/ledger references are retained for auditing rather than deleted or reassigned.

Admin organizer controls: `GET /api/v1/admin/organizers`, `GET /api/v1/admin/organizers/{id}`, and `PUT /api/v1/admin/organizers/{id}/block` with `{blocked,reason}`. Block/unblock is organizer-only account management. Blocking revokes all sessions and is enforced by login, refresh, Google session issuance and JWT validation; unblocking never revives old tokens. A fresh login is required. Profiles include organizer-created rooms, gross entry fees and current undistributed escrow. Dashboard registered-user counts exclude guest identities/test accounts, and active-organizer counts exclude blocked accounts and operator-owned legacy rooms.

The required CSV alias is `GET /api/v1/admin/tournaments/{id}/export-csv`; it shares the authenticated UTF-8, formula-safe, bounded export implementation with the operations endpoint. Personal operator wallet access is unrelated to platform treasury/financial inspection, which remains Admin-only.

Organizers declare top `min(3, funded-team-count)` winners in contiguous published ranking positions. Declared shares must be positive and total 100% of the winner pool, after the room's platform/organizer shares. All configured rounds must be recorded. New declarations use `POST /api/v1/organizer/tournaments/{id}/payout-requests`; the UI recommends 60/30/10 for three teams, 70/30 for two, or 100 for one. Declaration does not release funds. Pending declarations are immutable except exact idempotent retries. Admin can reject a pending/scheduled declaration with `POST /api/v1/admin/payout-requests/{id}/reject` and `{reason}`; the organizer can then resubmit.

Admin reviews and explicitly releases through `/api/v1/admin/payout-requests/{id}/approve`, or explicitly schedules that saved declaration after review. The historical direct `/api/v1/admin/tournaments/{id}/settle` route also requires an existing declaration and an identical winner payload. Current standings and all financial safeguards are revalidated at release. Rank, percentage, review and replay checks cannot be bypassed by an organizer override reason. Score dispute resolution only corrects/reviews scores; it never disburses funds. Unapproved automatic first-place settlement has been retired, including its scheduler. Old finance `automaticPayoutsEnabled` settings remain compatibility data and do not authorize unreviewed releases.

Apply `V20261025_07__organizer_account_controls.sql` after `_06` before production schema validation. For the latest Admin/Organizer refinements, verification is intentionally limited to frontend type/build checks, backend production/test-source compilation (`-DskipTests test`), and `scripts/operator-basic-smoke.mjs` with local SMTP test transport. This short smoke creates two test accounts and an empty room, checks isolation/block/CSV/declaration gates, and never deposits or releases money. Exhaustive financial/manual QA is deferred. Older broad smoke scripts predate this explicit top-3 approval policy and need their payout expectations updated before reuse.

## Multi-match tournament operations (current)

New organizer forms configure 1–20 named maps/rounds through `roundMaps`, such as Erangel, Miramar, Sanhok. Existing tournaments remain `plannedRounds=0` and retain their original financial/ranking contract. Round plans lock after the first registration or scheduled tournament start. Organizer commission is the room's snapshotted share (normally 10%); it compensates room administration, credentials, sequential execution, accurate scoring/evidence and dispute cooperation, and is paid only at tournament settlement.

Organizer API controls:

- `GET/PUT /api/v1/organizer/tournaments/{id}/rounds`: inspect the plan or set `{maps:[...]}` before registration.
- `PUT /api/v1/organizer/tournaments/{id}/registration`: `{open:true|false}` closes/reopens new registrations before the start time. Reopening after start is forbidden.
- `PUT /api/v1/organizer/tournaments/{id}/rounds/{number}/credentials`: publish that round's `{roomId,roomPassword}`.
- `POST /api/v1/organizer/tournaments/{id}/rounds/{number}/start`: start a configured round after tournament start time, only when earlier rounds are scored and no other round is live. It locks registration and publishes the current lobby to paid participants.
- `POST /api/v1/organizer/winner-portal/submit`: multipart result/evidence for the current live round. Every paid team must be recorded once with unique placement ranks. Include `placementPoints` and `playerKills` keyed by the account IDs exposed in the protected roster's `players` field; team `kills` must equal the individual sum. Complete squad rosters are required.
- `PUT /api/v1/organizer/winner-portal/{resultId}`: multipart `result`, `proofImages`, and `reason` for an organizer correction while the result is unverified, its window is open, and no dispute is pending. Match/room identity cannot change. Corrections increment the revision, preserve an append-only snapshot, and restart the two-hour dispute window. Reviewed/disputed results require admin adjudication.

Scores are provisional immediately after submission. Team total = placement points + team kills, at one point per kill. Overall standings sum every submitted round. Ties use total kills, total placement points, latest round placement, then captain ID. Player round points use the same team placement points plus that player's own kills; these individual points are not summed again into the team placement total. Access standings at `GET /api/v1/public/tournaments/{id}/standings` and safe round metadata at `/rounds`, with paid membership/private entry capability or management authority.

Submitting intermediate rounds keeps the tournament live. The last configured round moves it to UNDER_REVIEW. Admin resolution of a planned-round dispute corrects/audits that match without immediately settling or completing the tournament. Automatic and manual settlement require every planned result, closed/past review deadlines, green verification and no pending disputes. The automatic winner is #1 in cumulative standings, not the last match winner. Planned-tournament payout ranks must match cumulative standings even when an override reason is supplied. Existing monetary splitting, per-member rounding, organizer stake release, treasury and immutable ledger safeguards remain in force.

`GET /api/v1/me/tournament-history` exposes only the signed-in player's tournaments, team, seat, confirmed slot, match-wise map/room IDs, placement, individual/team kills and recorded winnings. No room passwords or other members' claim capabilities are exposed. New planned tournaments update profile/category match counts per round and credit kills to individual players at settlement; historical tournaments keep their original stats behavior.

Admin operations:

- `GET /api/v1/admin/operations`: active room/organizer counts and organizer-level room, gross escrow and commission totals, excluding archived fixtures.
- `GET /api/v1/admin/operations/tournaments/{id}/ledger?page=0&size=50`: paginated immutable records (maximum page size 200).
- `GET /api/v1/admin/operations/tournaments/{id}/ledger.csv`: authenticated UTF-8 attachment with tournament, sender/receiver, type, amount, reference and UTC timestamp. Export uses bounded keyset batches and captures an upper ledger ID, so later records do not shift an in-progress export. Quotes/newlines are escaped and formula-like cells are neutralized.
- `GET /api/v1/admin/operations/match-results/{id}/audit`: score revisions, editor, reason, timestamp and snapshots.

Apply `V20261025_06__multi_match_operations.sql` once after `_05` through the reviewed migration process before production schema validation. Development Hibernate adds the fields/tables.

`node scripts/multi-match-smoke.mjs` uses local SMTP/media and labelled TEST records to exercise three maps, QR team assembly, round order, individual kills, score revision/admin dispute correction, cumulative payouts, 10% organizer commission, member history and protected CSV export. It temporarily sets/restores finance policy and advances only its own TEST tournament's stored review deadlines via the local MySQL CLI, never changing the application's two-hour policy. Run only against an isolated local development database.

## Squad invites, privacy, and organizer moderation (current)

The dedicated Organizer Command Dashboard is `/organizer`. Rooms are ordered by creation date descending; history, roster, credentials, banner upload, privacy/chat controls, cancellation, and Winner Portal are available there.

For a four-player room the captain submits only their own IGN/UID. Registration reserves all four player places and generates one voucher for all four entry fees plus a private squad link/QR at `GET /api/v1/public/registrations/{id}/squad-invite` (captain ownership/private registration ticket required). Existing complete historical rosters retain their contract and remain supported by the API.

`/squads/join/{token}` shows the team and available seats without exposing personal details. Members sign in to a verified account, enter their own IGN/UID, and call `POST /api/v1/squads/invites/{token}/join`. Tournament/registration locks serialize seat assignment. Retries by an existing member return the same seat; overfill, duplicate UID, duplicate room membership, expired/cancelled entries, and joins after match start are rejected. The invite capability never reveals the captain's ticket or member claim codes.

The captain can pay before the remaining members arrive. If the squad invoice is already paid, each joining member immediately gains lobby and chat access through their own account. Before payment, a seat is reserved but confidential lobby/chat access stays locked. Complete the roster before the match starts; incomplete paid invite squads cannot publish results or settle. Cancel/refund through the established financial controls if a team cannot fill its seats. Invoice, wallet debit, escrow hold, and cancellation refund all use the full reserved squad size, preventing captain-only underpayment.

Create/update room visibility as `PUBLIC` or `PRIVATE`. Private rooms are omitted from public discovery and require the direct room invitation, an authorized manager, or paid membership to view details. Protected organizer `GET/PUT /api/v1/organizer/tournaments/{id}/controls` returns the direct `/rooms/{id}?invite=...` URL and QR. Browser clients retain the room capability in session storage and send it as `X-Room-Invite`; registration also includes `roomInviteToken`. The invite reveals room information, never lobby credentials without paid entry.

`roomChatEnabled=false` mutes participant sending but preserves authorized read/history/SSE. The owner organizer and admins retain moderation access and can send announcements while muted. This supersedes the earlier no-organizer-chat-bypass rule. Player lobby credentials continue to require a paid entry; managers edit credentials through protected organizer controls.

Automatic dummy tournament/fund seeding has been removed. `POST /api/v1/admin/maintenance/archive-demo-rooms` archives known `TEST ` rooms and the three former seed titles, and marks known TEST/example.com fixture accounts to exclude them from public statistics/leaderboards. It preserves all wallets and ledger records. Archiving removes rooms from discovery/organizer lists; archived rooms are not automatically settled. No financial history is deleted. Real rooms and account balances are preserved.

Room banners come from API `tournamentLogoUrl`, support absolute Cloudinary URLs, and fall back to `frontend/public/room-placeholder.svg` when absent or broken. No stock room imagery or hardcoded tournament data backs the cards.

For production apply `V20261025_05__squad_invites_room_controls.sql` once after `_04`, using the reviewed migration process; development Hibernate adds fields. `node scripts/squad-invite-smoke.mjs` requires local-only SMTP capture, local media, and staking disabled; it tests concurrent seats, payment reserves, private-room capability, chat moderation and cleanup. It creates and archives labelled local fixtures while retaining their audit history.

## Current role and Winner Portal rules

Registration accepts optional `role` as `ROLE_PLAYER` (default) or `ROLE_ORGANIZER`; admin, leader and guest roles cannot be self-assigned. Email verification remains mandatory for either public role. Google signup retains the player default. Organizer access is available immediately after verification/login without a separate admin promotion.

Room creation and management aliases require `ROLE_ORGANIZER` or `ROLE_ADMIN`, including the legacy `/api/v1/leader` routes. `ROLE_LEADER` no longer grants organizer controls; administrators must explicitly migrate any intended legacy organizers. Players receive HTTP 403 when attempting room creation. New and edited room entry fees must be at least Rs. 100 per player. Existing room contracts and ledger records are not rewritten.

Player-facing confidential lobby, chat history, sending, and SSE subscription require a paid entry in that specific room or its private paid guest capability. Organizers/admins retain chat moderation access; player-facing lobby credentials remain paid-entry-only. Organizers still manage their own credentials and participants through protected management endpoints; admins manage any room. Public listings remain available without registration.

The Organizer Hub contains the Winner Portal. `POST /api/v1/organizer/winner-portal/submit` accepts the existing multipart `result` and `proofImages` contract. It requires a live owned room (or admin authority), paid captain rankings, exactly one rank-1 winner, and evidence. Submission atomically closes play into `UNDER_REVIEW` and opens the two-hour dispute window; it does not bypass review by setting `COMPLETED` or immediately releasing money. Published standings are available through managed `GET /api/v1/organizer/tournaments/{id}/winner-portal`. Existing settlement controls complete the room after dispute/review safeguards. Paid captains and linked paid squad members can file evidence-backed disputes for admin review.

`node scripts/rbac-winner-smoke.mjs` exercises these rules with local-only SMTP capture and media settings. It requires staking disabled beforehand, never changes that policy itself, creates labelled TEST accounts/rooms and evidence, waits for the ordinary room-start scheduler, and leaves audit fixtures in the local development database. Do not run it against production or with real SMTP test recipients.

## Administrator password management

The configured `ADMIN_EMAIL` is created as a verified administrator when that email does not exist, including when other administrators already exist. An existing account is never reset or promoted by startup. Bootstrap passwords must have at least 12 characters and at most 72 UTF-8 bytes; keep credentials in the ignored backend `.env` only.

Admin Command Center includes **Change admin password** under Account Security. `PUT /api/v1/admin/account/password` accepts `currentPassword` and `newPassword` for the authenticated administrator only. It verifies the current password, enforces the bootstrap length/byte limits, rejects password reuse, hashes the replacement, and revokes every session transactionally. Sign in again after changing it. The stored database password is authoritative; changing it in the UI does not rewrite the bootstrap `.env` password, and later restarts do not restore the bootstrap value. Never delete the administrator to reset its password.

## Requirements and startup

- Java 25, Node.js 20.9+, and a running MySQL 8 service/database named `bte`.
- Backend: `backend/BattleTrust-Engine`; frontend: `frontend`.
- Backend `.env` is imported by Spring using `optional:file:.env[.properties]`; launch from the backend directory. Keep values in Java properties format (`KEY=value`, without shell quotes or `export`).
- Required database values: `DB_USERNAME`, `DB_PASSWORD`; optional `DB_URL`. Development defaults the URL to localhost:3306/bte. Production requires all three explicitly.
- Admin bootstrap: `ADMIN_EMAIL` / `ADMIN_PASSWORD`, with `ADMIN_DEFAULT_EMAIL` / `ADMIN_DEFAULT_PASSWORD` aliases. Password must contain at least 12 characters and at most 72 UTF-8 bytes. A pre-existing admin is never reset. A bootstrap email belonging to a non-admin fails startup instead of silently escalating it.
- This workspace has a generated local bootstrap password in the ignored backend `.env`. Read it there to sign in; do not copy it into tracked documentation or frontend environment files.
- Automatic demo tournament and wallet funding seeds have been removed. `DEV_SEED_DATA` is retained only as an unused compatibility setting.
- Google values: `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in backend `.env`; the public client ID also goes into frontend `.env.local` as `NEXT_PUBLIC_GOOGLE_CLIENT_ID`.

Run `./scripts/start-dev.ps1` from the root on Windows. It reuses healthy existing services; new processes launch hidden with logs in ignored `runtime-tmp/`. Alternatively run `./scripts/run-backend.ps1` and `npm run dev` in separate terminals. `run-backend.ps1` uses a short workspace-local Java socket directory to avoid the Windows Java 25 AF_UNIX loopback failure observed on this machine.

Frontend: http://localhost:3000
Backend: http://localhost:8080
API docs: http://localhost:8080/openapi/swagger-ui.html

Spring Docker Compose auto-start is explicitly disabled in both base and dev configuration. The local MySQL Windows service is used directly. No database passwords remain hardcoded in profile YAML.

## Roles and funds

`ROLE_ORGANIZER` coexists with legacy `ROLE_LEADER`. Admins may enable organizer access through the admin UI. Role changes invalidate the old access-token role; refreshing or signing in issues a token for the new role. Organizers may manage only their own tournaments; admins can manage all.

Tournament edits lock after the first entry or match start. Prize shares total 100%, with a mandatory 5% platform fee; settlement uses each tournament's configured split. Registration locks against the actual start time even before the minute scheduler transitions status to LIVE.

Guest entries receive a private ticket and an isolated, non-login guest escrow identity. A payment reference never proves receipt. Admin verification credits escrow once. Registered users may instead pay from available wallet funds. Wallet payment, admin verification, claims, and settlement lock affected records. Transactions re-read post-lock registration state to prevent stale MySQL REPEATABLE_READ entities from causing replay failures.

Guest winners can receive wallet funds before signup. Claiming with the private ticket transfers available funds, accumulated performance/category stats and historical ledger visibility to the authenticated profile, once. Nonzero transfers produce an immutable ACCOUNT_TRANSFER ledger record. Original financial ledger entries are preserved. A claimed entry cannot be reassigned to another account.

Every tournament has a logical isolated SSE chat channel. Read history, stream subscription, send, result access, and lobby credentials all require a verified paid entry/private ticket or organizer/admin ownership. Guest sender names come from their ticket identity, not submitted display text. Organizers cannot read private registration tickets from their roster endpoint. Solo and 1–4 member squad registrations are supported.

Results open a two-hour dispute window. Admins can compare images, override rankings and resolve/reject claims. Payout approval can be immediate (subject to verification) or scheduled by an admin to execute only after every result is green/closed and no dispute is pending. The scheduled task rechecks admin authorization and uses the same transactional settlement service; duplicate disbursal is rejected. Explicit admin overrides are recorded by the original settlement API.

Withdrawals reserve available funds. Rejection returns them; approval records the external payout and immutable ledger entry. There is no automatic bank transfer or payment-provider top-up integration in this repository.

## Validation

Backend: from its directory, with Java 25 configured, run `./mvnw.cmd test`. Windows hosts with the socket issue can set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<absolute short workspace directory>` first.

Frontend: run `npm run typecheck` and `npm run build`.

Full stack: after both services are ready, run `node scripts/smoke.mjs` from the root. It reads local admin credentials without printing them. It creates TEST Integration / TEST Wallet fixtures and simulated payment references in the development database, including actual internal escrow and wallet transactions. It never contacts a bank. Its report is written to ignored `runtime-tmp/smoke-results.json`. The test waits up to a minute for normal match lifecycle scheduling. Run only against a local development database.

A development compatibility repair supplies a default for the old required `chat_messages.guest` column when present. Existing rows are preserved. Current mapping uses `is_guest`. Production schema changes must be reviewed and applied before running with `ddl-auto=validate`.

## Deployment boundary

The implemented workflows are locally exercised; that alone is not a production certification. Before public deployment, supply real HTTPS origins, a unique JWT secret, least-privilege database credentials and reviewed versioned schema migrations. Use `prod` (secure refresh cookies, explicit secrets, no demo seeds), configure backup/restore and monitoring, and run concurrency/load tests against production-like infrastructure. Add payment-provider signature/webhook verification and reconciliation before enabling real online deposits/payouts. Configure SMTP and a durable email outbox. Google identity requires provider credentials and an authorized origin.

Rate limits protect auth, guest reservation, and chat writes per server instance; use a shared gateway limiter for multiple replicas. SSE room subscriptions are process-local and bounded; multiple replicas require shared pub/sub. Evidence currently uses the existing UUID media URLs; move sensitive proof behind signed or authorized media delivery for a public deployment. Public/admin listings and ledger views need full pagination/retention policies for large datasets. Pending guest reservations currently hold capacity until administratively handled; add expiry/refund policies before opening high-volume public registration.

Tests intentionally leave clearly labeled TEST records for inspection. Development fixtures and balances must not be used as real money. Local mail sending and mail health checks default to disabled; set `MAIL_ENABLED=true` only after configuring SMTP. The production profile keeps mail monitoring enabled.

## Organizer staking and simulated 1Bill (2026-10-24)

Dynamic staking settings are persisted in `system_settings` under `organizer.staking`. The public read endpoint exposes only the creation policy. Admins use `GET/PUT /api/v1/admin/settings/staking` with `{enabled, amount, mode, multiplier}`; mode defaults to FIXED and multiplier to 1 if omitted. Fixed deposits and `ENTRY_FEE_MULTIPLIER` are supported. Settings start disabled with a Rs 500 fixed amount, persist over restarts, and apply to new rooms only. Creation optionally accepts `stakingVersion` so a stale modal cannot silently accept a changed deposit requirement.

`POST /api/v1/tournaments` is the canonical organizer creation endpoint. Existing organizer and legacy leader creation routes use the same stake-enforcing service. New rooms default to four players per squad; specify `squadSize: 1` for solo or `2` for duo. Capacity must be a multiple of squad size and provide at most 25 slots. Existing rooms retain their original squad size. Organizer stakes are reserved in `wallet.organizerStakeBalance`, separately from withdrawal `lockedBalance` and tournament prize-pool vaults. The room snapshots its stake amount and status `ESCROW_ORGANIZER_STAKE`.

`DELETE /api/v1/tournaments/{id}?reason=...&waiveForfeiture=false` performs an audited soft cancellation; financial records are never hard-deleted. Unjoined-room stakes are refunded. After any registration, cancellation forfeits a held stake to the platform treasury, even if the entry is still pending. Only an admin can approve `waiveForfeiture=true` with a recorded reason. Paid player entry fees are refunded to their account or private guest wallet. Replay cannot refund twice. Existing financial-edit restrictions remain after registration to protect player terms.

Successful settlement returns the original stake in the same transaction as organizer commission, winner payouts and platform fees. Returning principal does not increase total earnings. New platform fees and forfeited deposits accumulate in `platform_treasury`; earlier historical platform revenue remains visible in the existing ledger/vault analytics. Treasury is readable by admins at `/api/v1/admin/treasury`.

Each new squad registration receives one 10-digit simulated 1Bill voucher, a combined amount, an expiry (15 minutes or match start, whichever comes first), and a server-generated QR. Retrieve it at `GET /api/v1/public/registrations/{registrationId}/voucher` using entry ownership or the `X-Registration-Ticket` header. The QR carries a BTE-SIMULATED-1BILL payload and never contains the private ticket; it is not an official bank-compatible 1Bill code. Legacy entries are not retroactively billed.

`POST /api/v1/payments/simulate-pay/{voucherId}` requires private entry access. It exists only under the `dev` profile with `app.payments.simulation-enabled=true` (`PAYMENT_SIMULATION_ENABLED`, development default true). Profile tests prove the endpoint bean is absent in production even if the flag is true. UI simulation buttons are hidden when unavailable. No bank, JazzCash or Easypaisa service is contacted; button names identify test payment flows only.

Simulation, wallet entry payment and admin payment verification share one funding/slot-allocation service. A squad becomes `slotStatus: CONFIRMED` with a stable slot number between 1 and 25. Registration and tournament row locks, post-lock reads and a unique `(tournament_id, slot_number)` constraint serialize confirmation. Repeating payment returns the same confirmation without another deposit, escrow hold, or slot. Expired/cancelled vouchers cannot be simulated. Zero-fee entries are confirmed immediately.

New audit types include DEPOSIT, STAKE_LOCK, STAKE_RELEASE, STAKE_REFUND, STAKE_FORFEIT, ESCROW_HOLD, PLATFORM_FEE, ORGANIZER_COMMISSION, WINNING_PAYOUT and REFUND. Older ledger enum values remain supported to preserve historical rows. All entries retain tournament IDs and unique type/reference keys. Stakes never enlarge the player prize pool.

UI: Organizer hub has a creation modal with live policy/balance checks, separate held-stake wallet totals and cancellation controls. Player registration collects captain IGN/UID and each member IGN/UID. The voucher panel shows amount, expiry, QR, simulation methods and confirmed slot. Admin command includes the staking policy switch and treasury total.

Additional validation: run `node scripts/staking-smoke.mjs`. It exercises policy authorization, free/staked creation, insufficient funds, quote versions, refunds, forfeiture, admin waivers, expired vouchers, five concurrent payment retries, slot assignment, twenty separately funded rooms, settlement and replay protection. It restores the original staking policy in `finally` and leaves clearly named TEST records for inspection. Results are stored in ignored `runtime-tmp/staking-results.json`. Run against local simulated funds only. The existing `scripts/smoke.mjs` also remains applicable, with explicit solo fixtures and historical/new payout-type compatibility.

## Creation-time inventory and individual team payouts

Room creation now prepares voucher inventory before registration: a 100-player room with four-player squads has 25 invoices, each with a unique 10-digit ID and an available QR. Organizer endpoints: `GET /api/v1/organizer/tournaments/{id}/vouchers` and `GET /api/v1/organizer/tournaments/{id}/vouchers/{voucherId}/qr`. Inventory positions are not confirmed match slots. Registration privately binds one available invoice; payment confirms the next free slot. Unassigned vouchers cannot be paid. Edits before registration refresh unissued invoice amounts/capacity. Expired, unpaid reservations are cancelled and replaced with a fresh voucher ID; old IDs are never reused. Submitted external bank references remain held for admin review. Pending/unissued vouchers expire when a match starts.

New squad registrations create separate non-login guest wallet identities for each teammate. The captain’s profile owns position zero; positions 1–3 have independent private member claim capabilities. The captain can obtain these codes only through `GET /api/v1/public/registrations/{registrationId}/squad-wallets`, authorized by ownership or the private registration ticket. Give each teammate only their own code. `POST /api/v1/me/squad-members/claim` links that member’s funds, stats, and immutable ledger visibility to an authenticated profile exactly once. `GET /api/v1/me/squad-memberships` exposes safe room membership data without captain secrets. Verified paid members gain room/chat access. An account cannot claim multiple active player positions in a room. IGN/UID/email matching alone never transfers ownership.

Settlement first rounds/deducts the snapshotted platform fee, then the room’s organizer commission, then divides each winning team’s prize allocation equally among its individual member wallets. Cent remainders are assigned deterministically in roster order; no rounding step can create money. Each member receives a separate WINNING_PAYOUT ledger record keyed by tournament, registration and member position. Unclaimed members retain winnings in private guest wallets until linking. Historical entries without member-wallet metadata preserve their original captain payout contract; historical transactions are never rewritten.

Platform commission and automatic settlement are controlled through `GET/PUT /api/v1/admin/settings/finance` with `{platformFeePercent, automaticPayoutsEnabled}`. Public policy is available at `/api/v1/public/settings/finance`. New rooms snapshot the configured fee and whether automatic settlement is enabled. Existing room fees do not change when the global fee is edited. The established organizer/winner split remains configurable per room; choose organizer share zero to distribute the entire post-platform-fee pool to winners. The admin global automation switch can pause automatic execution across rooms.

For newly enabled rooms, the automatic job selects the first-place team from the final verified match result and allocates 100% of the winners pool to that team. It requires all results green, every review window closed and past its deadline, and no pending dispute. It uses the existing locked, replay-protected settlement service and records a system settlement reason. Scheduled/manual admin payouts remain available for custom multi-team shares. Unpaid registrations are excluded from the funded prize pool and cannot block legitimate confirmed-team settlement.

Wallet responses now include `escrowReservedBalance` for confirmed captain-owned entry funds that remain in an undistributed tournament vault. This is distinct from pending withdrawal reserves and organizer security stake balances. Member winnings become available wallet funds immediately upon settlement; external cashout requests still follow the configured minimum/maximum and administrator review. No bank transfer is performed by the simulator.

Production upgrade SQL from the preceding schema is provided in `backend/BattleTrust-Engine/db/migrations/V20261024_03__team_finance.sql`. Run it once with a migration runner before the `prod` profile; it is not an automatically installed Flyway runner. Development Hibernate adds fields, while the development initializer makes the old voucher registration foreign key nullable and links historical vouchers to their tournaments without changing ledger rows.

Run `node scripts/team-finance-smoke.mjs` for creation-time inventory, QR authorization, exact four-member payouts, private wallet linking, twenty independently funded rooms, review-window/global-pause gating and automatic settlement. It restores both original financial policies in `finally`. For its own labelled TEST result only, it backdates the stored review deadline with a local MySQL CLI command; this uses UTC_TIMESTAMP to match JDBC timestamp storage. It does not change the application’s two-hour review policy. Results are saved to ignored `runtime-tmp/team-finance-results.json`. Unit tests also cover penny rounding and expired-voucher replacement.
# Cloudinary and mandatory email verification

Configure `backend/BattleTrust-Engine/.env` using the placeholders in `.env.example`, then restart the backend. Never put Cloudinary secrets or SMTP passwords in frontend environment variables.

- Set `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET`, then `MEDIA_PROVIDER=cloudinary`. Development defaults to local storage; production defaults to Cloudinary and rejects missing/placeholder credentials. The backend signs uploads using the [Cloudinary Java SDK](https://cloudinary.com/documentation/java_integration).
- Set `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD` (an SMTP app password). Authentication and STARTTLS default to enabled. Verification delivery is mandatory even when optional notification mail (`MAIL_ENABLED`) is disabled.
- `POST /api/v1/auth/register` returns HTTP 201 with `verificationRequired`, email, and a message. It no longer returns JWTs or refresh cookies. Open the emailed `/verify-email?token=...` link and explicitly confirm it, or use the token field in the registration dialog. Verification links expire after 30 minutes and work once. Resending replaces the token with a 60-second cooldown and a generic response.
- `POST /api/v1/auth/verify-email` accepts `{ "token": "..." }`; `POST /api/v1/auth/resend-verification` accepts `{ "email": "..." }`. Only token hashes are stored. Missing SMTP setup or delivery failure rejects signup without committing the account. Mail is sent synchronously; an external mail delivery cannot be rolled back if a later database commit fails, so resend is available.
- Existing non-admin accounts require verification through the resend screen. The configured bootstrap administrator is trusted by the startup initializer. Google sign-in requires the provider's verified-email claim. Login, refresh, and authenticated JWT access reject unverified accounts.
- `POST /api/v1/me/avatar` and `POST /api/v1/organizer/tournaments/{id}/banner` accept multipart `file`; banner changes require ownership or admin rights. Existing match/dispute proof uploads use the same storage abstraction. Images must decode as JPEG, PNG, or GIF, be at most 5 MB and 40 megapixels. Public URLs are returned, so screenshots must not contain secrets.
- The home page displays at most two live/upcoming rooms. `/tournaments` contains all rooms with search, game and status filters, and a single-column layout on mobile.

## Local email integration checks

`node scripts/smtp-capture.mjs` starts a local-only SMTP sink on `127.0.0.1:1025`. For a temporary backend test process, override `SPRING_MAIL_HOST=127.0.0.1`, `SPRING_MAIL_PORT=1025`, `SPRING_MAIL_USERNAME=test@battletrust.local`, `SPRING_MAIL_SMTP_AUTH=false`, and `SPRING_MAIL_STARTTLS=false`. Do not use these overrides in production. Captured private messages stay under ignored `runtime-tmp/smtp-capture/`.

Run `node scripts/email-media-smoke.mjs` to check verification delivery, blocked login, token replay, and validated avatar upload. The existing financial smoke scripts also verify their example.com fixture accounts using this local capture. Restart normally after testing to restore the real SMTP configuration. Cloudinary transport tests mock the SDK; live Cloudinary delivery requires actual account credentials.

