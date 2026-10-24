import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const env = Object.fromEntries((await readFile(new URL('../backend/BattleTrust-Engine/.env', import.meta.url), 'utf8')).split(/\r?\n/).filter((s) => /^\w+=/.test(s)).map((s) => [s.slice(0, s.indexOf('=')), s.slice(s.indexOf('=') + 1)]));
let token;
async function call(path, body) {
  const r = await fetch('http://localhost:8080' + path, { method: body ? 'POST' : 'GET', headers: { Origin: 'http://localhost:3000', 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const data = await r.json(); if (!r.ok) throw new Error(`${path}: ${r.status} ${data.message || ''}`); return data;
}
token = (await call('/api/v1/auth/login', { email: env.ADMIN_EMAIL || env.ADMIN_DEFAULT_EMAIL, password: env.ADMIN_PASSWORD || env.ADMIN_DEFAULT_PASSWORD })).token;
const suffix = randomUUID().slice(0, 8);
const t = await call('/api/v1/organizer/tournaments', { title: `TEST Concurrent ${suffix}`, gameType: 'Solo', squadSize: 1, gameCategory: 'PUBG', entryFeePkr: 10, maxPlayers: 4, startTime: new Date(Date.now() + 86400000 - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 19), rules: 'Local concurrency test.', winnersSharePercent: 85, leaderSharePercent: 10, systemSharePercent: 5 });
const r = await call(`/api/v1/tournaments/${t.id}/join`, { tournamentId: t.id, teamName: 'TEST concurrency squad', captainName: 'TEST', captainEmail: 'test-concurrency@example.com', whatsappNumber: '03000000000', squadMembers: [{ name: 'TEST', inGameId: suffix }] });
const before = await call('/api/v1/wallet');
await Promise.all(Array.from({ length: 5 }, () => call(`/api/v1/me/registrations/${r.id}/pay`, {})));
const after = await call('/api/v1/wallet');
assert.equal(Number(before.availableBalance) - Number(after.availableBalance), 10);
const list = await call(`/api/v1/public/tournaments/${t.id}`); assert.equal(Number(list.collectedPool), 10);
console.log('PASS 5 concurrent entry payments return success; exactly one debit and one escrow credit.');

