import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readdir,readFile} from 'node:fs/promises';
import {verifyTestAccount} from './test-email-verification.mjs';
const email=`test-verification-${randomUUID().slice(0,8)}@example.com`,password=randomUUID()+'!';
const base='http://localhost:8080';
async function post(path,body,token){return fetch(base+path,{method:'POST',headers:{Origin:'http://localhost:3000',...(body instanceof FormData?{}:{'Content-Type':'application/json'}),...(token?{Authorization:`Bearer ${token}`}:{})},body:body instanceof FormData?body:JSON.stringify(body)});}
const registered=await post('/api/v1/auth/register',{email,password,name:'Verification Test'});
assert.equal(registered.status,201);const pending=await registered.json();assert.equal(pending.verificationRequired,true);assert.equal(pending.token,undefined);assert.equal(registered.headers.get('set-cookie'),null);console.log('PASS registration requires verification and issues no session');
assert.equal((await post('/api/v1/auth/login',{email,password})).status,401);console.log('PASS unverified login denied');
assert.equal((await post('/api/v1/auth/verify-email',{token:'invalid'})).status,400);
const account=await verifyTestAccount(email,password);assert.ok(account.token);console.log('PASS SMTP delivery, verification, and verified login');
const folder=new URL('../runtime-tmp/smtp-capture/',import.meta.url);let token;for(const file of await readdir(folder)){const message=JSON.parse(await readFile(new URL(file,folder),'utf8'));if(message.to===email)token=message.body.replace(/=\r?\n/g,'').match(/verify-email\?token=([A-Za-z0-9_-]{43})/)?.[1];}
assert.equal((await post('/api/v1/auth/verify-email',{token})).status,400);console.log('PASS verification token replay denied');
const invalid=new FormData();invalid.set('file',new Blob(['invalid'],{type:'image/png'}),'invalid.png');assert.equal((await post('/api/v1/me/avatar',invalid,account.token)).status,400);console.log('PASS invalid image content rejected');
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6ioAAAAASUVORK5CYII=','base64');const valid=new FormData();valid.set('file',new Blob([png],{type:'image/png'}),'avatar.png');const uploaded=await post('/api/v1/me/avatar',valid,account.token);assert.equal(uploaded.status,200);const {url}=await uploaded.json();assert.ok(url);assert.equal((await fetch(base+url)).status,200);console.log('PASS authenticated avatar upload and image retrieval');
