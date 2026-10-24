import {readdir,readFile} from 'node:fs/promises';
export async function verifyTestAccount(email,password){
 if(!email.endsWith('@example.com'))throw new Error('Test verification helper accepts only example.com fixtures.');
 const folder=new URL('../runtime-tmp/smtp-capture/',import.meta.url);let token;
 for(let attempt=0;attempt<30&&!token;attempt++){const files=await readdir(folder).catch(()=>[]);for(const file of files){const message=JSON.parse(await readFile(new URL(file,folder),'utf8'));if(message.to===email){token=message.body.replace(/=\r?\n/g,'').match(/verify-email\?token=([A-Za-z0-9_-]{43})/)?.[1];if(token)break;}}if(!token)await new Promise(r=>setTimeout(r,100));}
 if(!token)throw new Error('No verification mail captured. Run smtp-capture.mjs and configure the backend test SMTP transport.');
 const verified=await fetch('http://localhost:8080/api/v1/auth/verify-email',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token})});if(!verified.ok)throw new Error(`Test email verification failed: ${verified.status}`);
 const response=await fetch('http://localhost:8080/api/v1/auth/login',{method:'POST',headers:{Origin:'http://localhost:3000','Content-Type':'application/json'},body:JSON.stringify({email,password})});if(!response.ok)throw new Error(`Verified test login failed: ${response.status}`);const result=await response.json();result.cookie=response.headers.getSetCookie()[0]?.split(';')[0];return result;
}
