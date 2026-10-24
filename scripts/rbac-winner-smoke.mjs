// Local-only fixtures: use smtp-capture.mjs and local media overrides from RUNBOOK.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {verifyTestAccount} from './test-email-verification.mjs';
const env=Object.fromEntries((await readFile(new URL('../backend/BattleTrust-Engine/.env',import.meta.url),'utf8')).split(/\r?\n/).filter(x=>/^\w+=/.test(x)).map(x=>{const i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1).trim()]}));
const suffix=randomUUID().slice(0,8);let checks=0;
async function req(path,{body,auth,method=body?'POST':'GET',status=200}={}){const r=await fetch('http://localhost:8080'+path,{method,headers:{Origin:'http://localhost:3000',...(auth?{Authorization:'Bearer '+auth.token}:{}),...(body && !(body instanceof FormData)?{'Content-Type':'application/json'}:{})},body:body instanceof FormData?body:body?JSON.stringify(body):undefined});assert.equal(r.status,status,`${method} ${path}`);checks++;const text=await r.text();return text?JSON.parse(text):null;}
async function account(role){const email=`test-rbac-${role.toLowerCase()}-${randomUUID().slice(0,8)}@example.com`,password=randomUUID()+'!';const pending=await req('/api/v1/auth/register',{body:{name:'TEST RBAC '+role,email,password,role},status:201});assert.equal(pending.verificationRequired,true);const user=await verifyTestAccount(email,password);assert.equal(user.role,role);return user;}
const admin=await req('/api/v1/auth/login',{body:{email:env.ADMIN_EMAIL,password:env.ADMIN_PASSWORD}});
const player=await account('ROLE_PLAYER'),organizer=await account('ROLE_ORGANIZER'),other=await account('ROLE_ORGANIZER');
await req('/api/v1/auth/register',{body:{name:'TEST invalid role',email:`test-invalid-${suffix}@example.com`,password:randomUUID(),role:'ROLE_ADMIN'},status:400});console.log('PASS verified player/organizer signup; admin role injection rejected');
const policy=await req('/api/v1/public/settings/finance');const staking=await req('/api/v1/public/settings/staking');
// If staking is enforced, fund only the test organizer using the existing dev admin wallet.
if(staking.enabled)throw Error('Run this fixture with staking disabled; this script never changes live policy.');
const localTime=ms=>new Date(Date.now()+ms-new Date().getTimezoneOffset()*60000).toISOString().slice(0,19);
const roomBody={title:'TEST RBAC '+suffix,gameType:'Solo',squadSize:1,gameCategory:'PUBG',entryFeePkr:100,maxPlayers:4,startTime:localTime(70000),winnersSharePercent:100-Number(policy.platformFeePercent),leaderSharePercent:0,systemSharePercent:Number(policy.platformFeePercent),rules:'TEST local RBAC fixture'};
for(const path of ['/api/v1/tournaments','/api/v1/organizer/tournaments'])await req(path,{auth:player,body:roomBody,status:403});
await req('/api/v1/tournaments',{auth:organizer,body:{...roomBody,entryFeePkr:99},status:400});
const room=await req('/api/v1/tournaments',{auth:organizer,body:roomBody});
await req(`/api/v1/organizer/tournaments/${room.id}`,{auth:other,method:'PUT',body:roomBody,status:401});
await req(`/api/v1/organizer/tournaments/${room.id}/room-credentials`,{auth:organizer,body:{roomId:'TEST-'+suffix,roomPassword:randomUUID()}});console.log('PASS player creation 403, fee minimum, and organizer ownership');
for(const auth of [player,organizer,admin]){await req(`/api/v1/public/tournaments/${room.id}/room`,{auth,status:401});await req(`/api/v1/chat/tournament/${room.id}`,{auth,status:auth===player?401:200});}
const entry=await req(`/api/v1/tournaments/${room.id}/join`,{auth:player,body:{tournamentId:room.id,teamName:'TEST Team '+suffix,captainName:'TEST Captain',captainEmail:player.user.email,whatsappNumber:'03000000000',squadMembers:[{name:'TEST Captain',inGameId:suffix}]}});
await req(`/api/v1/public/tournaments/${room.id}/room`,{auth:player,status:401});
const voucher=await req(`/api/v1/public/registrations/${entry.id}/voucher`,{auth:player});await req('/api/v1/payments/simulate-pay/'+voucher.voucherId,{auth:player,body:{}});
await req(`/api/v1/public/tournaments/${room.id}/room`,{auth:player});await req(`/api/v1/chat/tournament/${room.id}`,{auth:player});
await req('/api/v1/chat/send',{auth:player,body:{tournamentId:room.id,senderName:'TEST',messageText:'TEST paid-room access'},status:201});
const room2=await req('/api/v1/tournaments',{auth:other,body:{...roomBody,title:'TEST isolated '+suffix,startTime:localTime(86400000)}});
await req(`/api/v1/public/tournaments/${room2.id}/room`,{auth:player,status:401});console.log('PASS paid-room-only chat/lobby, with organizer/admin moderation access');
for(let i=0;i<150;i++){const listing=await req('/api/v1/public/tournaments/'+room.id);if(listing.tournament.status==='LIVE')break;await new Promise(r=>setTimeout(r,1000));if(i===149)throw Error('Room did not become LIVE');}
function resultForm(rank=1){const f=new FormData();f.set('result',new Blob([JSON.stringify({tournamentId:room.id,matchNumber:1,rankings:[{playerUserId:player.user.id,rank,kills:3,teamName:'TEST Team '+suffix}]})],{type:'application/json'}));f.append('proofImages',new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6ioAAAAASUVORK5CYII=','base64')],{type:'image/png'}),'proof.png');return f;}
await req('/api/v1/organizer/winner-portal/submit',{auth:player,body:resultForm(),status:403});await req('/api/v1/organizer/winner-portal/submit',{auth:other,body:resultForm(),status:401});await req('/api/v1/organizer/winner-portal/submit',{auth:organizer,body:resultForm(2),status:400});
const result=await req('/api/v1/organizer/winner-portal/submit',{auth:organizer,body:resultForm(),status:201});assert.equal(result.verificationStatus,'YELLOW_TICK_UNVERIFIED');const after=await req('/api/v1/public/tournaments/'+room.id);assert.equal(after.tournament.status,'UNDER_REVIEW');await req(`/api/v1/organizer/tournaments/${room.id}/winner-portal`,{auth:organizer});
const dispute=new FormData();dispute.set('claim',new Blob([JSON.stringify({matchResultId:result.id,reason:'TEST incorrect points review'})],{type:'application/json'}));dispute.set('screenshot',new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6ioAAAAASUVORK5CYII=','base64')],{type:'image/png'}),'dispute.png');await req('/api/v1/disputes/file-claim',{auth:player,body:dispute,status:201});console.log('PASS Winner Portal ownership, rank-1 winner, room review closure, and player dispute');console.log(`PASS ${checks} API assertions; local TEST rooms retained for audit.`);
