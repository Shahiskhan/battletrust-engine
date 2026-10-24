// Local integration-test SMTP sink. Never relays email or exposes an HTTP mailbox.
import net from 'node:net';
import {mkdir,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const folder=new URL('../runtime-tmp/smtp-capture/',import.meta.url);await mkdir(folder,{recursive:true});
net.createServer(socket=>{socket.setEncoding('utf8');socket.write('220 BattleTrust local test SMTP\r\n');let buffer='',data=false,lines=[],recipient='';
socket.on('data',async chunk=>{buffer+=chunk;let boundary;while((boundary=buffer.indexOf('\r\n'))>=0){const line=buffer.slice(0,boundary);buffer=buffer.slice(boundary+2);
if(data){if(line==='.') {data=false;await writeFile(new URL(`${randomUUID()}.json`,folder),JSON.stringify({to:recipient,body:lines.join('\r\n'),capturedAt:new Date().toISOString()}));lines=[];socket.write('250 Message captured locally\r\n');}else lines.push(line.startsWith('..')?line.slice(1):line);continue;}
if(/^(EHLO|HELO)/i.test(line))socket.write('250-BattleTrust\r\n250-SIZE 6291456\r\n250 8BITMIME\r\n');else if(/^RCPT TO:/i.test(line)){recipient=line.match(/<([^>]+)>/)?.[1]||'';socket.write('250 OK\r\n');}else if(/^MAIL FROM:/i.test(line)||/^RSET/i.test(line)||/^NOOP/i.test(line))socket.write('250 OK\r\n');else if(/^DATA$/i.test(line)){data=true;lines=[];socket.write('354 End with dot\r\n');}else if(/^QUIT/i.test(line)){socket.end('221 Bye\r\n');}else socket.write('502 Unsupported test SMTP command\r\n');}});
socket.on('error',()=>{});
}).listen(1025,'127.0.0.1',()=>console.log('Local-only test SMTP ready on 127.0.0.1:1025; messages stay in ignored runtime-tmp.'));
