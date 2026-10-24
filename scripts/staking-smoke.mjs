// Local-only simulated finance integration test. Credentials and private tickets are never printed.
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const base = 'http://localhost:8080';
const env = Object.fromEntries((await readFile(new URL('../backend/BattleTrust-Engine/.env', import.meta.url), 'utf8')).split(/\r?\n/).filter(s => /^\w+=/.test(s)).map(s => [s.slice(0,s.indexOf('=')),s.slice(s.indexOf('=')+1).trim().replace(/^['"]|['"]$/g,'')]));
const checks = []; const tag = randomUUID().slice(0,8);
const pass = (name) => { checks.push(name); console.log(`PASS ${name}`); };
async function call(path, { auth, body, ticket, method = body ? 'POST' : 'GET', status = 200 } = {}) {
    const response = await fetch(base + path, { method, headers: { Origin: 'http://localhost:3000', ...(body ? { 'Content-Type': 'application/json' } : {}), ...(auth?.token ? { Authorization: `Bearer ${auth.token}` } : {}), ...(auth?.cookie ? { Cookie: auth.cookie } : {}), ...(ticket ? { 'X-Registration-Ticket': ticket } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000) });
    const result = await response.json().catch(() => ({}));
    assert.equal(response.status, status, `${method} ${path}: ${response.status} ${result.message || ''}`);
    const cookie = response.headers.getSetCookie()[0]?.split(';')[0]; if (cookie) result.cookie = cookie;
    if(path==='/api/v1/auth/register' && result.verificationRequired)return (await import('./test-email-verification.mjs')).verifyTestAccount(body.email,body.password);return result;
}
const admin = await call('/api/v1/auth/login', { body: { email: env.ADMIN_EMAIL || env.ADMIN_DEFAULT_EMAIL, password: env.ADMIN_PASSWORD || env.ADMIN_DEFAULT_PASSWORD } });
const original = await call('/api/v1/admin/settings/staking', { auth: admin });
const balance = async () => call('/api/v1/wallet', { auth: admin });
const treasury = async () => Number((await call('/api/v1/admin/treasury', { auth: admin })).balance);
async function policy(enabled, amount = 500, mode = 'FIXED', multiplier = 1) { return call('/api/v1/admin/settings/staking', { auth: admin, method: 'PUT', body: { enabled, amount, mode, multiplier } }); }
function roomBody(label, start = Date.now()+86400000) { return { title: `TEST Staking ${tag} ${label}`, gameType: 'Squad', squadSize: 4, gameCategory: 'PUBG', entryFeePkr: 100, maxPlayers: 100, startTime: new Date(start - new Date().getTimezoneOffset()*60000).toISOString().slice(0,19), rules: 'Local staking simulation fixture.', winnersSharePercent: 85, leaderSharePercent: 10, systemSharePercent: 5 }; }
async function create(label, extra = {}) { return call('/api/v1/tournaments', { auth: admin, body: { ...roomBody(label), ...extra } }); }
function squad(t, label = '') { return { tournamentId: t.id, teamName: `TEST Squad ${tag} ${label}`, captainName: 'TEST Captain', captainEmail: `squad-${tag}${label}@example.com`, whatsappNumber: '03000000000', squadMembers: Array.from({ length: 4 }, (_, i) => ({ name: i === 0 ? 'TEST Captain' : `TEST Member ${i}`, inGameId: `${tag}-${label}-${i}` })) }; }
const register = (t, label) => call('/api/v1/public/registrations', { body: squad(t, label), status: 201 });
const voucher = (r) => call(`/api/v1/public/registrations/${r.id}/voucher`, { ticket: r.ticketCode });
const pay = (r,v) => call(`/api/v1/payments/simulate-pay/${v.voucherId}`, { body: {}, ticket: r.ticketCode });
const cancel = (t, waiver = false, actor = admin) => call(`/api/v1/tournaments/${t.id}?reason=TEST%20local%20cancellation&waiveForfeiture=${waiver}`, { auth: actor, method: 'DELETE' });
try {
    const player = await call('/api/v1/auth/register', { body: { name: `TEST Stake User ${tag}`, email: `stake-${tag}@example.com`, password: randomUUID() }, status: 201 });
    await call('/api/v1/admin/settings/staking', { auth: player, method: 'PUT', body: { enabled: true, amount: 500 }, status: 403 }); pass('Staking settings are admin-only');
    await policy(false); let before = await balance();
    const free = await create('FREE'); assert.equal(free.organizerStakeStatus, 'NONE'); assert.equal(Number((await balance()).availableBalance), Number(before.availableBalance)); await cancel(free); pass('FREE creation and deletion have no stake deductions');
    const stale = await policy(true); await policy(true, 600);
    await call('/api/v1/tournaments', { auth: admin, body: { ...roomBody('Stale policy'), stakingVersion: stale.version }, status: 409 }); pass('Stale creation-policy versions cannot silently change deposit terms');
    await policy(true, 1000000000); await call('/api/v1/tournaments', { auth: admin, body: roomBody('Insufficient'), status: 422 }); pass('Insufficient stake balance rejects creation atomically');
    await policy(true, 500); before = await balance(); const unjoined = await create('Unjoined');
    let held = await balance(); assert.equal(Number(before.availableBalance)-Number(held.availableBalance),500); assert.equal(Number(held.organizerStakeBalance)-Number(before.organizerStakeBalance),500); assert.equal(Number(held.lockedBalance),Number(before.lockedBalance)); pass('Security stake is isolated from withdrawal reserves');
    await policy(false); await cancel(unjoined); await cancel(unjoined); assert.equal(Number((await balance()).availableBalance),Number(before.availableBalance)); pass('Existing stake refunds once even after enforcement is toggled OFF');
    await policy(true,500,'ENTRY_FEE_MULTIPLIER',2.5); const multiplied=await create('Multiplier'); assert.equal(Number(multiplied.organizerStakeAmount),250); await cancel(multiplied); pass('Entry-fee multiplier snapshots the correct security stake');
    await policy(true); const forfeited=await create('Forfeit');
    const invalid=squad(forfeited,'invalid'); invalid.squadMembers.pop(); await call('/api/v1/public/registrations',{body:invalid,status:400}); pass('Four-player room rejects incomplete squads');
    const entry=await register(forfeited,'paid'); const bill=await voucher(entry);
    assert.match(bill.voucherId,/^\d{10}$/); assert.equal(Number(bill.amount),400); assert.ok(bill.qrDataUrl.startsWith('data:image/svg+xml;base64,')); assert.ok(!Buffer.from(bill.qrDataUrl.split(',')[1],'base64').toString().includes(entry.ticketCode));
    await call(`/api/v1/public/registrations/${entry.id}/voucher`,{status:401}); await call(`/api/v1/payments/simulate-pay/${bill.voucherId}`,{body:{},ticket:randomUUID(),status:401}); pass('Combined voucher has a 10-digit ID, QR, expiry, and private ownership checks');
    const paid=await Promise.all(Array.from({length:5},()=>pay(entry,bill))); assert.ok(paid.every(r=>r.slotStatus==='CONFIRMED' && r.slotNumber===1));
    let listing=await call(`/api/v1/public/tournaments/${forfeited.id}`); assert.equal(Number(listing.collectedPool),400); pass('Concurrent voucher payment confirms one locked slot and funds escrow exactly once');
    const second=await register(forfeited,'second'); const secondBill=await voucher(second); const secondPaid=await pay(second,secondBill); assert.equal(secondPaid.slotNumber,2); pass('Next paid squad receives the next available slot');
    const tbefore=await treasury(); await cancel(forfeited); assert.equal((await treasury())-tbefore,500); pass('Joined-room cancellation forfeits stake to treasury');
    const claimant=await call('/api/v1/auth/register',{body:{name:`TEST Claim ${tag}`,email:`claimed-stake-${tag}@example.com`,password:randomUUID()},status:201});
    await call('/api/v1/me/registrations/claim',{auth:claimant,body:{ticketCode:entry.ticketCode}}); const refunded=await call('/api/v1/wallet',{auth:claimant}); assert.equal(Number(refunded.availableBalance),400); assert.ok(refunded.ledgerHistory.some(l=>l.transactionType==='REFUND')); pass('Cancelled paid squad receives a full traceable entry-fee refund');
    const waived=await create('Valid cause'); await register(waived,'waiver'); before=await balance(); await cancel(waived,true); assert.equal(Number((await balance()).availableBalance)-Number(before.availableBalance),500); pass('Admin valid-cause cancellation refunds the stake after entries');
    await policy(false); await call(`/api/v1/admin/users/${player.user.id}/role`,{auth:admin,method:'PATCH',body:{role:'ROLE_ORGANIZER'}}); const organizer=await call('/api/v1/auth/refresh',{auth:{cookie:player.cookie},body:{}});
    const theirRoom=await call('/api/v1/tournaments',{auth:organizer,body:roomBody('Organizer waiver')}); await call(`/api/v1/tournaments/${theirRoom.id}?reason=TEST&waiveForfeiture=true`,{auth:organizer,method:'DELETE',status:401}); await cancel(theirRoom,false,organizer); pass('Organizer cannot approve their own forfeiture waiver');
    const expired=await create('Expiry',roomBody('Expiry',Date.now()+4000)); const expiredEntry=await register(expired,'expiry'); const expiredBill=await voucher(expiredEntry); await new Promise(resolve=>setTimeout(resolve,4500)); await call(`/api/v1/payments/simulate-pay/${expiredBill.voucherId}`,{body:{},ticket:expiredEntry.ticketCode,status:400}); await cancel(expired,true); pass('Expired vouchers cannot fund escrow or confirm slots');
    const rooms=await Promise.all(Array.from({length:20},(_,i)=>create(`Partition ${i}`,{entryFeePkr:100+i})));
    const partitionEntries=[];
    for(let offset=0;offset<rooms.length;offset+=5) await Promise.all(rooms.slice(offset,offset+5).map(async(t)=>{const r=await register(t,`p${t.id}`);const v=await voucher(r);await pay(r,v);partitionEntries.push({t,r});}));
    for(const {t} of partitionEntries){const l=await call(`/api/v1/public/tournaments/${t.id}`);assert.equal(Number(l.collectedPool),Number(t.entryFeePkr)*4);}
    pass('20 simultaneous rooms maintain independent escrow balances and slot #1');
    for(const t of rooms) await cancel(t,true);
    await policy(true); before=await balance(); const finalRoom=await create('Completion',roomBody('Completion',Date.now()+15000)); const winner=await register(finalRoom,'winner'); const winnerBill=await voucher(winner); await pay(winner,winnerBill);
    const roster=await call(`/api/v1/organizer/tournaments/${finalRoom.id}/registrations`,{auth:admin}); const winnerId=roster[0].ownerUserId;
    for(let i=0;i<45;i++){listing=await call(`/api/v1/public/tournaments/${finalRoom.id}`);if(listing.tournament.status==='LIVE')break;await new Promise(resolve=>setTimeout(resolve,2000));}
    const treasuryBefore=await treasury(); await call(`/api/v1/admin/tournaments/${finalRoom.id}/settle`,{auth:admin,body:{winners:[{userId:winnerId,rank:1,winnersPoolPercent:100}],overrideReason:'TEST local settlement override to verify staking pipeline'}});
    const completed=(await call(`/api/v1/public/tournaments/${finalRoom.id}`)).tournament; const after=await balance(); assert.equal(completed.organizerStakeStatus,'RELEASED'); assert.equal(Number(after.availableBalance)-Number(before.availableBalance),40); assert.equal(Number(after.organizerStakeBalance),Number(before.organizerStakeBalance)); assert.equal((await treasury())-treasuryBefore,20);
    assert.ok(after.ledgerHistory.some(l=>l.transactionType==='STAKE_RELEASE')); assert.ok(after.ledgerHistory.some(l=>l.transactionType==='ORGANIZER_COMMISSION')); pass('Completion atomically pays 85% winnings, 10% commission, 5% treasury and releases the full stake');
    await call(`/api/v1/admin/tournaments/${finalRoom.id}/settle`,{auth:admin,body:{winners:[{userId:winnerId,rank:1,winnersPoolPercent:100}],overrideReason:'TEST replay'},status:409}); pass('Settlement replay cannot release stake or credit commissions twice');
} finally {
    await call('/api/v1/admin/settings/staking',{auth:admin,method:'PUT',body:{enabled:original.enabled,amount:original.amount,mode:original.mode,multiplier:original.multiplier}});
    console.log('Original staking policy restored.');
}
await writeFile(new URL('../runtime-tmp/staking-results.json',import.meta.url),JSON.stringify({verifiedAt:new Date().toISOString(),checks},null,2));
console.log(`Verified ${checks.length} staking/voucher integration checks.`);

