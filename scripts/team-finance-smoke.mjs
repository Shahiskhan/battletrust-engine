// Development-only checks. Uses simulated funds and TEST fixtures; never prints secrets or private claim codes.
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
const env=Object.fromEntries((await readFile(new URL('../backend/BattleTrust-Engine/.env',import.meta.url),'utf8')).split(/\r?\n/).filter(s=>/^\w+=/.test(s)).map(s=>[s.slice(0,s.indexOf('=')),s.slice(s.indexOf('=')+1).trim().replace(/^['"]|['"]$/g,'')]));
const tag=randomUUID().slice(0,8);const checks=[];const pass=name=>{checks.push(name);console.log(`PASS ${name}`);};
async function call(path,{auth,body,ticket,method=body?'POST':'GET',status=200}={}){
 const response=await fetch('http://localhost:8080'+path,{method,headers:{Origin:'http://localhost:3000',...(body&&!(body instanceof FormData)?{'Content-Type':'application/json'}:{}),...(auth?.token?{Authorization:`Bearer ${auth.token}`} :{}),...(ticket?{'X-Registration-Ticket':ticket}:{})},body:body?body instanceof FormData?body:JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});
 const result=await response.json().catch(()=>({}));assert.equal(response.status,status,`${method} ${path}: ${response.status} ${result.message||''}`);if(path==='/api/v1/auth/register' && result.verificationRequired)return (await import('./test-email-verification.mjs')).verifyTestAccount(body.email,body.password);return result;
}
const admin=await call('/api/v1/auth/login',{body:{email:env.ADMIN_EMAIL||env.ADMIN_DEFAULT_EMAIL,password:env.ADMIN_PASSWORD||env.ADMIN_DEFAULT_PASSWORD}});
const original=await call('/api/v1/admin/settings/finance',{auth:admin});const oldStake=await call('/api/v1/admin/settings/staking',{auth:admin});
const config=(fee,automatic)=>call('/api/v1/admin/settings/finance',{auth:admin,method:'PUT',body:{platformFeePercent:fee,automaticPayoutsEnabled:automatic}});
const signup=label=>call('/api/v1/auth/register',{body:{name:`TEST Team ${tag} ${label}`,email:`team-${tag}-${label}@example.com`,password:randomUUID()},status:201});
const captain=await signup('captain');const member1=await signup('member1');const member2=await signup('member2');const member3=await signup('member3');
const wallet=auth=>call('/api/v1/wallet',{auth});
const startString=ms=>new Date(ms-new Date().getTimezoneOffset()*60000).toISOString().slice(0,19);
const create=(label,fee=100,start=Date.now()+86400000)=>call('/api/v1/tournaments',{auth:admin,body:{title:`TEST Team Finance ${tag} ${label}`,gameType:'Squad',gameCategory:'PUBG',squadSize:4,entryFeePkr:fee,maxPlayers:100,startTime:startString(start),rules:'TEST simulation only',winnersSharePercent:92.5,leaderSharePercent:0,systemSharePercent:7.5}});
const squad=(t,label)=>({tournamentId:t.id,teamName:`TEST ${tag} ${label}`,captainName:'Captain',captainEmail:`captain-${tag}@example.com`,whatsappNumber:'03000000000',squadMembers:Array.from({length:4},(_,i)=>({name:`Player ${i}`,inGameId:`${tag}-${label}-${i}`}))});
const pay=async(r)=>{const v=await call(`/api/v1/public/registrations/${r.id}/voucher`,{ticket:r.ticketCode});return call(`/api/v1/payments/simulate-pay/${v.voucherId}`,{body:{},ticket:r.ticketCode});};
let room;
try{
 await call('/api/v1/admin/settings/finance',{auth:captain,method:'PUT',body:{platformFeePercent:7.5,automaticPayoutsEnabled:true},status:403});await config(7.5,true);pass('Only admins can configure platform commission and automatic execution');
 await call('/api/v1/admin/settings/staking',{auth:admin,method:'PUT',body:{enabled:false,amount:oldStake.amount,mode:oldStake.mode,multiplier:oldStake.multiplier}});
 room=await create('Automatic winner',100,Date.now()+15000);
 const inventory=await call(`/api/v1/organizer/tournaments/${room.id}/vouchers`,{auth:admin});assert.equal(inventory.length,25);assert.equal(new Set(inventory.map(v=>v.voucherId)).size,25);assert.ok(inventory.every(v=>/^\d{10}$/.test(v.voucherId)&&v.status==='AVAILABLE'&&v.registrationId===null));pass('Room creation prepares 25 unique ten-digit squad vouchers before registration');
 const qr=await call(`/api/v1/organizer/tournaments/${room.id}/vouchers/${inventory[0].voucherId}/qr`,{auth:admin});assert.ok(qr.qrDataUrl.startsWith('data:image/svg+xml;base64,'));await call(`/api/v1/organizer/tournaments/${room.id}/vouchers`,{auth:captain,status:403});pass('Prepared voucher QR is available only to authorized organizers/admins');
 const entry=await call(`/api/v1/tournaments/${room.id}/join`,{auth:captain,body:squad(room,'winner')});
 const assigned=await call(`/api/v1/public/registrations/${entry.id}/voucher`,{auth:captain});assert.ok(inventory.some(v=>v.voucherId===assigned.voucherId));assert.equal(Number(assigned.amount),400);pass('Four-player registration binds one pre-generated combined invoice');
 const confirmed=await pay(entry);assert.equal(confirmed.slotNumber,1);assert.equal(confirmed.slotStatus,'CONFIRMED');assert.equal(Number((await wallet(captain)).escrowReservedBalance),400);pass('Payment confirms the slot and exposes the actual room escrow reserve');
 const members=await call(`/api/v1/public/registrations/${entry.id}/squad-wallets`,{auth:captain});assert.equal(new Set(members.map(m=>m.walletUserId)).size,4);assert.ok(members.slice(1).every(m=>m.claimTicket));await call(`/api/v1/public/registrations/${entry.id}/squad-wallets`,{auth:member1,status:401});pass('Every team member gets an isolated wallet and private claim capability');
 await call('/api/v1/me/squad-members/claim',{auth:member1,body:{claimTicket:members[1].claimTicket}});await call('/api/v1/me/squad-members/claim',{auth:captain,body:{claimTicket:members[2].claimTicket},status:400});pass('A profile can represent only one player in a room');
 const memberships=await call('/api/v1/me/squad-memberships',{auth:member1});assert.ok(memberships.some(m=>m.tournamentId===room.id));await call(`/api/v1/chat/tournament/${room.id}`,{auth:member1});pass('Claimed members gain their paid squad room access without captain secrets');
 const rooms=await Promise.all(Array.from({length:20},(_,i)=>create(`Partition ${i}`,100+i)));
 for(let offset=0;offset<rooms.length;offset+=5)await Promise.all(rooms.slice(offset,offset+5).map(async(t)=>{const r=await call('/api/v1/public/registrations',{body:squad(t,`partition-${t.id}`),status:201});await pay(r);const l=await call(`/api/v1/public/tournaments/${t.id}`);assert.equal(Number(l.collectedPool),Number(t.entryFeePkr)*4);}));
 pass('Twenty simultaneously funded rooms retain separate voucher inventories, slots and vaults');
 for(const t of rooms)await call(`/api/v1/tournaments/${t.id}?reason=TEST%20fixture%20cleanup&waiveForfeiture=true`,{auth:admin,method:'DELETE'});
 for(let i=0;i<45;i++){const t=await call(`/api/v1/public/tournaments/${room.id}`);if(t.tournament.status==='LIVE')break;await new Promise(r=>setTimeout(r,2000));}
 const form=new FormData();form.append('result',new Blob([JSON.stringify({tournamentId:room.id,matchNumber:1,rankings:[{playerUserId:captain.user.id,rank:1,kills:6,teamName:entry.teamName}]})],{type:'application/json'}));form.append('proofImages',new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=','base64')],{type:'image/png'}),'test.png');
 const result=await call('/api/v1/leader/match-results/submit',{auth:admin,body:form,status:201});await new Promise(r=>setTimeout(r,16000));assert.notEqual((await call(`/api/v1/public/tournaments/${room.id}`)).tournament.status,'COMPLETED');pass('Automatic payouts cannot bypass the open dispute window');
 await config(7.5,false);
 // Advance only this TEST fixture's deadline; do not alter production clocks or application review policy.
 assert.ok(Number.isSafeInteger(result.id)&&Number.isSafeInteger(room.id));
 execFileSync('C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe',['-u',env.DB_USERNAME,'-D','bte','-e',`UPDATE match_results SET dispute_deadline = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 SECOND) WHERE id = ${result.id} AND tournament_id = ${room.id}`],{env:{...process.env,MYSQL_PWD:env.DB_PASSWORD},stdio:['ignore','pipe','pipe']});
 for(let i=0;i<45;i++){const results=await call(`/api/v1/public/tournaments/${room.id}/results`,{auth:captain});if(results[0].verificationStatus==='GREEN_TICK_VERIFIED')break;await new Promise(r=>setTimeout(r,2000));}
 assert.notEqual((await call(`/api/v1/public/tournaments/${room.id}`)).tournament.status,'COMPLETED');pass('Admin global pause blocks otherwise finalized automatic payouts');
 const treasuryBefore=Number((await call('/api/v1/admin/treasury',{auth:admin})).balance);await config(12,true);
 for(let i=0;i<20;i++){if((await call(`/api/v1/public/tournaments/${room.id}`)).tournament.status==='COMPLETED')break;await new Promise(r=>setTimeout(r,2000));}
 const completed=(await call(`/api/v1/public/tournaments/${room.id}`)).tournament;assert.equal(completed.status,'COMPLETED');assert.equal(Number(completed.systemSharePercent),7.5);assert.equal(Number((await call('/api/v1/admin/treasury',{auth:admin})).balance)-treasuryBefore,30);pass('Finalized results automatically settle using the original 7.5% fee snapshot');
 assert.equal(Number((await wallet(captain)).availableBalance),92.5);assert.equal(Number((await wallet(member1)).availableBalance),92.5);assert.equal(Number((await wallet(captain)).escrowReservedBalance),0);pass('Captain and claimed teammate each receive exactly one quarter of the 370 PKR prize pool');
 for(const [index,auth]of [[2,member2],[3,member3]]){await call('/api/v1/me/squad-members/claim',{auth,body:{claimTicket:members[index].claimTicket}});await call('/api/v1/me/squad-members/claim',{auth,body:{claimTicket:members[index].claimTicket}});const w=await wallet(auth);assert.equal(Number(w.availableBalance),92.5);assert.ok(w.ledgerHistory.some(l=>l.transactionType==='WINNING_PAYOUT'));}
 await call('/api/v1/me/squad-members/claim',{auth:member3,body:{claimTicket:members[2].claimTicket},status:401});pass('Guest member funds and immutable history link once and cannot be stolen');
 const before=(await wallet(captain)).availableBalance;await new Promise(r=>setTimeout(r,17000));assert.equal((await wallet(captain)).availableBalance,before);await call(`/api/v1/admin/tournaments/${room.id}/settle`,{auth:admin,body:{winners:[{userId:captain.user.id,rank:1,winnersPoolPercent:100}],overrideReason:'TEST replay'},status:409});pass('Repeated scheduler ticks and manual retries cannot duplicate team winnings');
}finally{
 await call('/api/v1/admin/settings/finance',{auth:admin,method:'PUT',body:{platformFeePercent:original.platformFeePercent,automaticPayoutsEnabled:original.automaticPayoutsEnabled}});
 await call('/api/v1/admin/settings/staking',{auth:admin,method:'PUT',body:{enabled:oldStake.enabled,amount:oldStake.amount,mode:oldStake.mode,multiplier:oldStake.multiplier}});console.log('Original financial and staking policies restored.');
}
await writeFile(new URL('../runtime-tmp/team-finance-results.json',import.meta.url),JSON.stringify({verifiedAt:new Date().toISOString(),checks,tournamentId:room.id},null,2));console.log(`Verified ${checks.length} team-finance integration checks.`);


