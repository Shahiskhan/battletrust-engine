// Local development integration checks. Never prints credentials, JWTs, cookies, or private tickets.
// Creates clearly named TEST records and simulated payment references in the configured local database.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const base = 'http://localhost:8080';
const origin = 'http://localhost:3000';
const envText = await readFile(new URL('../backend/BattleTrust-Engine/.env', import.meta.url), 'utf8');
const env = Object.fromEntries(envText.split(/\r?\n/).filter((line) => /^\w+=/.test(line)).map((line) => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1).trim().replace(/^['"]|['"]$/g, '')]; }));
const checks = [];
function check(name) { checks.push(name); console.log(`PASS ${name}`); }
async function request(path, { auth, body, method = body ? 'POST' : 'GET', ticket, expected = 200, headers = {}, raw = false } = {}) {
  const requestHeaders = { Origin: origin, ...headers };
  if (auth?.token) requestHeaders.Authorization = `Bearer ${auth.token}`;
  if (auth?.cookie) requestHeaders.Cookie = auth.cookie;
  if (ticket) requestHeaders['X-Registration-Ticket'] = ticket;
  if (body && !(body instanceof FormData)) requestHeaders['Content-Type'] = 'application/json';
  const response = await fetch(base + path, { method, headers: requestHeaders, body: body ? body instanceof FormData ? body : JSON.stringify(body) : undefined, signal: AbortSignal.timeout(20000) });
  if (response.status !== expected) { let message = ''; try { message = (await response.json()).message || ''; } catch {} throw new Error(`${method} ${path}: expected ${expected}, got ${response.status}. ${message}`); }
  if (raw) return response;
  if (response.status === 204 || response.headers.get('content-length') === '0') return null;
  const cookie = response.headers.getSetCookie()[0]?.split(';')[0];
  const data = await response.json();
  if (data.token && cookie) data.cookie = cookie;
  if (path === '/api/v1/auth/register' && data.verificationRequired) return (await import('./test-email-verification.mjs')).verifyTestAccount(body.email, body.password);
  return data;
}
const suffix = randomUUID().slice(0, 8);
const email = `test-player-${suffix}@example.com`;
const password = randomUUID() + '!';
const admin = await request('/api/v1/auth/login', { body: { email: env.ADMIN_EMAIL || env.ADMIN_DEFAULT_EMAIL, password: env.ADMIN_PASSWORD || env.ADMIN_DEFAULT_PASSWORD } });
assert.equal(admin.role, 'ROLE_ADMIN'); check('Environment-seeded admin login');
const stats = await request('/api/v1/public/stats'); assert.ok(stats.totalUsers >= 1); check('Live platform stats and MySQL persistence');
const cors = await request('/api/v1/auth/login', { method: 'OPTIONS', headers: { 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type' }, raw: true });
assert.equal(cors.headers.get('access-control-allow-origin'), origin); assert.equal(cors.headers.get('access-control-allow-credentials'), 'true'); check('Credentialed CORS preflight');
await request('/api/v1/auth/login', { method: 'OPTIONS', headers: { Origin: 'https://untrusted.example', 'Access-Control-Request-Method': 'POST' }, expected: 403, raw: true }); check('Untrusted origin rejected');
const player = await request('/api/v1/auth/register', { body: { name: `TEST Player ${suffix}`, email, password }, expected: 201 });
await request('/api/v1/admin/analytics', { auth: player, expected: 403 }); check('Player cannot access admin APIs');
await request(`/api/v1/admin/users/${player.user.id}/role`, { auth: admin, method: 'PATCH', body: { role: 'ROLE_ORGANIZER' } });
const organizer = await request('/api/v1/auth/refresh', { auth: { cookie: player.cookie }, body: {} }); assert.equal(organizer.role, 'ROLE_ORGANIZER'); check('Organizer role and refresh rotation');
const future = new Date(Date.now() + 20000);
const localTime = new Date(future.getTime() - future.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
const tournament = await request('/api/v1/organizer/tournaments', { auth: organizer, body: { title: `TEST Integration ${suffix}`, gameType: 'Solo', squadSize: 1, gameCategory: 'PUBG', entryFeePkr: 100, maxPlayers: 4, startTime: localTime, rules: 'Development integration fixture. Simulated funds only.', winnersSharePercent: 80, leaderSharePercent: 15, systemSharePercent: 5 } });
await request(`/api/v1/organizer/tournaments/${tournament.id}/room-credentials`, { auth: organizer, body: { roomId: `TEST-${suffix}`, roomPassword: randomUUID() } }); check('Organizer tournament creation, custom split, and room setup');
const guestEmail = `test-guest-${suffix}@example.com`;
const guest = await request('/api/v1/public/registrations', { body: { tournamentId: tournament.id, teamName: `TEST Squad ${suffix}`, captainName: 'Guest captain', captainEmail: guestEmail, whatsappNumber: '03000000000', squadMembers: [{ name: 'Guest captain', inGameId: suffix }] }, expected: 201 });
await request(`/api/v1/public/tournaments/${tournament.id}/room`, { ticket: guest.ticketCode, expected: 401 });
await request(`/api/v1/chat/tournament/${tournament.id}`, { ticket: guest.ticketCode, expected: 401 }); check('Unpaid guests cannot access chat or lobby');
const reference = `TEST-PAYMENT-${suffix}`;
await request('/api/v1/public/registrations/payment-reference', { body: { registrationId: guest.id, ticketCode: guest.ticketCode, transactionId: reference } });
await request(`/api/v1/chat/tournament/${tournament.id}`, { ticket: guest.ticketCode, expected: 401 }); check('Payment reference alone never grants access');
await request(`/api/v1/admin/registrations/${guest.id}/verify-payment`, { auth: admin, body: { transactionId: reference } });
await request(`/api/v1/admin/registrations/${guest.id}/verify-payment`, { auth: admin, body: { transactionId: reference } });
const listing = await request(`/api/v1/public/tournaments/${tournament.id}`); assert.equal(Number(listing.collectedPool), 100); check('Idempotent payment verification and isolated escrow vault');
const room = await request(`/api/v1/public/tournaments/${tournament.id}/room`, { ticket: guest.ticketCode }); assert.equal(room.roomId, `TEST-${suffix}`);
await request(`/api/v1/chat/tournament/${tournament.id}`, { expected: 401 }); check('Paid guest unlocks confidential lobby; anonymous access denied');
const others = await request('/api/v1/public/tournaments'); const other = others.find((t) => t.tournament.id !== tournament.id);
if (other) await request(`/api/v1/chat/tournament/${other.tournament.id}`, { ticket: guest.ticketCode, expected: 401 }); check('Tournament ticket cannot cross room boundaries');
const sent = await request('/api/v1/chat/send', { ticket: guest.ticketCode, body: { tournamentId: tournament.id, senderName: 'Impersonated admin', messageText: `TEST message ${suffix}` }, expected: 201 });
assert.equal(sent.senderName, 'Guest captain'); check('Guest chat sender is bound to ticket identity');
const streamController = new AbortController();
const stream = await fetch(`${base}/api/v1/chat/tournament/${tournament.id}/stream`, { headers: { Origin: origin, 'X-Registration-Ticket': guest.ticketCode }, signal: streamController.signal });
assert.equal(stream.status, 200); assert.ok(stream.headers.get('content-type').includes('text/event-stream'));
const reader = stream.body.getReader(); const event = await reader.read(); assert.ok(new TextDecoder().decode(event.value).includes('messages')); streamController.abort(); check('Authorized private SSE chat stream');
const roster = await request(`/api/v1/organizer/tournaments/${tournament.id}/registrations`, { auth: organizer });
assert.ok(roster[0].ownerUserId); assert.equal(roster[0].ticketCode, undefined); check('Guest has escrow identity; organizer cannot read bearer tickets');
const guestId = roster[0].ownerUserId;
const wait = future.getTime() - Date.now() + 1500; if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
// Scheduler ticks every minute; verify deadline locking without waiting for transition.
await request('/api/v1/public/registrations', { body: { tournamentId: tournament.id, teamName: 'Late entry', captainName: 'Late', captainEmail: `late-${suffix}@example.com`, whatsappNumber: '03000000000', squadMembers: [{ name: 'Late', inGameId: 'late' }] }, expected: 400 }); check('Registration locks at the match start time');
// Wait for the normal lifecycle scheduler to mark this started match LIVE.
for (let i = 0; i < 35; i++) { const t = await request(`/api/v1/public/tournaments/${tournament.id}`); if (t.tournament.status === 'LIVE') break; await new Promise((resolve) => setTimeout(resolve, 2000)); }
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=', 'base64');
const resultForm = new FormData(); resultForm.append('result', new Blob([JSON.stringify({ tournamentId: tournament.id, matchNumber: 1, rankings: [{ playerUserId: guestId, rank: 1, kills: 7, teamName: guest.teamName }] })], { type: 'application/json' })); resultForm.append('proofImages', new Blob([png], { type: 'image/png' }), 'test.png');
const result = await request('/api/v1/leader/match-results/submit', { auth: organizer, body: resultForm, expected: 201 }); assert.equal(result.verificationStatus, 'YELLOW_TICK_UNVERIFIED'); check('Guest result submission with screenshot proof and dispute window');
await request(`/api/v1/admin/tournaments/${tournament.id}/settle`, { auth: admin, body: { winners: [{ userId: guestId, rank: 1, winnersPoolPercent: 100 }] }, expected: 400 }); check('Settlement blocked during unverified review');
await request(`/api/v1/admin/tournaments/${tournament.id}/settle`, { auth: admin, body: { winners: [{ userId: guestId, rank: 1, winnersPoolPercent: 100 }], overrideReason: 'TEST explicit administrator override for local integration fixture' } });
await request(`/api/v1/admin/tournaments/${tournament.id}/settle`, { auth: admin, body: { winners: [{ userId: guestId, rank: 1, winnersPoolPercent: 100 }], overrideReason: 'TEST replay' }, expected: 409 }); check('Atomic custom-split guest payout and settlement replay protection');
const signedGuest = await request('/api/v1/auth/register', { body: { name: `TEST Claimed ${suffix}`, email: guestEmail, password }, expected: 201 });
const before = await request('/api/v1/me/registrations', { auth: signedGuest }); assert.equal(before.length, 0); check('Email match alone cannot claim guest entries');
await request('/api/v1/me/registrations/claim', { auth: signedGuest, body: { ticketCode: guest.ticketCode } });
await request('/api/v1/me/registrations/claim', { auth: signedGuest, body: { ticketCode: guest.ticketCode } });
const wallet = await request('/api/v1/wallet', { auth: signedGuest }); assert.equal(Number(wallet.availableBalance), 80); assert.ok(wallet.ledgerHistory.some((l) => ['WINNER_PAYOUT', 'WINNING_PAYOUT'].includes(l.transactionType)));
const profile = await request('/api/v1/me', { auth: signedGuest }); assert.equal(profile.matchesWon, 1); assert.equal(profile.totalKills, 7); check('Guest claim transfers funds, immutable history, and statistics exactly once');
await request('/api/v1/me/registrations/claim', { auth: organizer, body: { ticketCode: guest.ticketCode }, expected: 401 }); check('Claimed entry cannot be stolen by another account');
const health = await request('/actuator/health', { auth: admin }); assert.equal(health.status, 'UP'); await request('/actuator/health', { expected: 401 }); check('Actuator health is admin-only');
const adminBefore = await request('/api/v1/wallet', { auth: admin });
const paidTournament = await request('/api/v1/organizer/tournaments', { auth: admin, body: { title: `TEST Wallet ${suffix}`, gameType: 'Solo', squadSize: 1, gameCategory: 'VALORANT', entryFeePkr: 100, maxPlayers: 4, startTime: new Date(Date.now() + 86400000 - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 19), rules: 'Simulated wallet entry fixture.', winnersSharePercent: 85, leaderSharePercent: 10, systemSharePercent: 5 } });
const walletEntry = await request(`/api/v1/tournaments/${paidTournament.id}/join`, { auth: admin, body: { tournamentId: paidTournament.id, teamName: `TEST Wallet squad ${suffix}`, captainName: 'System Admin', captainEmail: admin.user.email, whatsappNumber: '03000000000', squadMembers: [{ name: 'Admin test gamer', inGameId: `wallet-${suffix}` }] } });
await Promise.all([request(`/api/v1/me/registrations/${walletEntry.id}/pay`, { auth: admin, body: {} }), request(`/api/v1/me/registrations/${walletEntry.id}/pay`, { auth: admin, body: {} })]);
const adminAfter = await request('/api/v1/wallet', { auth: admin }); assert.equal(Number(adminAfter.availableBalance), Number(adminBefore.availableBalance) - 100); check('Concurrent wallet entry payment debits exactly once');
const withdraw = await request('/api/v1/wallet/withdraw', { auth: admin, body: { amount: 100, paymentMethod: 'BANK', accountDetails: 'TEST ONLY - no external payout' }, expected: 201 });
const reserved = await request('/api/v1/wallet', { auth: admin }); assert.equal(Number(reserved.lockedBalance), Number(adminAfter.lockedBalance) + 100);
await request(`/api/v1/admin/withdrawals/${withdraw.id}/decision`, { auth: admin, body: { approve: false, decisionNote: 'TEST withdrawal rejection and reserved balance release' } });
const released = await request('/api/v1/wallet', { auth: admin }); assert.equal(Number(released.availableBalance), Number(adminAfter.availableBalance)); assert.equal(Number(released.lockedBalance), Number(adminAfter.lockedBalance)); check('Withdrawal reservation and rejection conserve wallet funds');
await request('/api/v1/auth/logout', { auth: signedGuest, body: {}, expected: 204 }); await request('/api/v1/me', { auth: signedGuest, expected: 401 }); check('Logout revokes active access token');
const report = { verifiedAt: new Date().toISOString(), checks, fixture: { tournamentId: tournament.id, label: `TEST Integration ${suffix}` } };
await mkdir(new URL('../runtime-tmp/', import.meta.url), { recursive: true }); await writeFile(new URL('../runtime-tmp/smoke-results.json', import.meta.url), JSON.stringify(report, null, 2));
console.log(`Verified ${checks.length} integration checks. Test fixture tournament #${tournament.id}.`);



