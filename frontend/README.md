# BattleTrust frontend

Next.js App Router + TypeScript + Tailwind CSS v4 + Framer Motion + Axios + TanStack Query + Zustand.

## Start

Run `npm install` once, then `npm run dev` from this folder. Node 20.9+ is required. From the repository root, `./scripts/start-dev.ps1` launches both services on Windows using the installed Java 25 runtime. Detailed backend configuration and verification are in `../RUNBOOK.md`.

Environment values live in `.env.local`:

- `NEXT_PUBLIC_API_URL=http://localhost:8080`
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID` enables Google Identity Services. Only the public client ID belongs here; never place the Google client secret in frontend environment variables.

## Screens

- `/`: live tournament discovery and statistics, game/status/entry/search filters, public leaderboard, login/register and optional Google sign-in.
- Tournament dialog: solo or squad guest registration, private ticket recovery, transaction-reference submission, account linking, wallet entry payment, start countdown, protected lobby credentials, isolated SSE chat, match evidence and captain disputes.
- `/dashboard`: player statistics and registered squads, wallet balances and immutable ledger, withdrawal requests.
- Organizer hub (organizer/legacy leader/admin): create/edit tournaments, configure splits, publish lobby credentials, roster, screenshot result submissions, payout approval requests.
- Admin command: actual platform revenue and pending disputes, Actuator health/memory, payment verification, manual or scheduled payouts, side-by-side evidence and ranking overrides, withdrawal decisions, player/organizer role management.

## Session and privacy

Access tokens remain in memory; the backend rotates an HttpOnly refresh cookie. Concurrent 401 requests share a refresh operation, with one request retry. Session restoration runs on reload. Account changes clear React Query caches. Guest tickets are saved in browser sessionStorage and linked by possession after login. Tickets are bearer secrets: save them privately before closing the browser session. Email matching alone never claims guest entries. Backend checks remain authoritative for every room, action, and role.

SSE uses streaming fetch so Authorization and private-ticket headers never appear in URLs. It reconnects after the server's 25-second stream lifetime. The server checks current room access and active authenticated sessions during broadcasts. This implementation uses one process; horizontal deployment needs shared pub/sub and gateway limits.

## API contracts and validation

The live backend's schema is exported to `openapi.json`. Canonical docs: `http://localhost:8080/openapi/swagger-ui.html` and `/openapi/v3/api-docs`. Interfaces live in `src/lib/types.ts` and `src/lib/platform.ts`. Tailwind v4 tokens are in `src/app/globals.css`, with platform module styling in `platform.css`.

Run `npm run typecheck` and `npm run build`. The root integration script `node scripts/smoke.mjs` checks real MySQL-backed auth, CORS, organizer controls, guest entry/payment/access, private SSE, screenshots, escrow, guest migration, concurrent wallet payment, withdrawals, and logout. It creates TEST fixtures and simulated payment references; it never transmits money to an external payment provider.

No fabricated fallback tournaments or rankings remain. Backend errors and empty states are displayed explicitly. Local seeded tournaments are development data. Google Fonts/Unsplash require network access; typography includes local fallbacks.

## External integrations

Direct online top-ups and cash payouts require a payment provider. A reference is recorded as PENDING until an administrator verifies receipt. Approving a withdrawal records a completed external payout; it does not itself transfer money. Development mail is disabled by default until SMTP is configured. Google login requires an authorized frontend origin and a real provider login; automated tests do not impersonate a Google user.

Staking/voucher UI: `src/components/staking-controls.tsx` contains live policy controls, the room creation dialog, cancellation form, and combined simulated 1Bill voucher/QR panel. Backend policy and transaction rules are documented in the root RUNBOOK. New rooms default to four-player squads and at most 25 confirmed slots. Online bank payments remain outside this simulator.

Creation-time voucher inventory, financial policy controls and private member-wallet claiming are in `src/components/finance-controls.tsx`. Wallets display funded entry escrow separately from withdrawal reserves and organizer stakes. Automatic winning-team payouts are split per member; the captain can privately distribute claim codes to guest teammates. Review windows and unresolved disputes block automatic execution.
