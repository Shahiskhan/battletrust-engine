'use client';
import {OverallStandings} from './multi-match';
import {RoomBanner} from './room-banner';
import {SquadInvite} from './squad-invite';
import { mediaUrl } from '@/lib/platform';
import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { X, ShieldCheck, Send, LockKeyhole } from 'lucide-react';
import { errorMessage, refreshSession } from '@/lib/api';
import { useAuth } from '@/stores/auth';
import { VoucherCard } from './staking-controls';
import { SquadWalletLinks, type Membership } from './finance-controls';
import { get, post, money, saveTicket, savedTicket, type Listing, type Registration, type Message, type MatchResult, multipart } from '@/lib/platform';

export function Countdown({ start }: { start: string }) {
  const [remaining, setRemaining] = useState<number | null>(null);
  useEffect(() => { const update = () => setRemaining(Math.max(0, new Date(start).getTime() - Date.now())); update(); const interval = setInterval(update, 1000); return () => clearInterval(interval); }, [start]);
  return <span className="countdown">{remaining === null ? 'Checking room lock…' : remaining === 0 ? 'Registration locked' : `Registration locks in ${Math.floor(remaining / 3600000)}h ${Math.floor(remaining / 60000) % 60}m ${Math.floor(remaining / 1000) % 60}s`}</span>;
}
export function TournamentDialog({ id, close }: { id: number; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const session = useAuth((s) => s.session);
  const cache = useQueryClient();
  const [ticket, setTicket] = useState(() => savedTicket(id));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const listing = useQuery({ queryKey: ['tournament', id], queryFn: () => get<Listing>(`/api/v1/public/tournaments/${id}`) });
  const entry = useQuery({ queryKey: ['ticket', id, ticket], queryFn: () => get<Registration>('/api/v1/public/registrations/ticket', ticket), enabled: !!ticket, refetchInterval: 10000 });

  const memberships = useQuery({ queryKey: ['squad-memberships'], queryFn: () => get<Membership[]>('/api/v1/me/squad-memberships'), enabled: !!session });
  const isManager = !!session && (session.role === 'ROLE_ADMIN' || (session.role === 'ROLE_ORGANIZER' && session.user.id === listing.data?.tournament.leaderId));
  const memberJoined = memberships.data?.some((m) => m.tournamentId === id && m.paymentStatus === 'PAID');
  const paidAccess = entry.data?.paymentStatus === 'PAID' || !!memberJoined; const allowed = paidAccess || isManager;
  const room = useQuery({ queryKey: ['room', id, ticket, session?.user.id], queryFn: () => get<{ roomId: string; roomPassword: string }>(`/api/v1/public/tournaments/${id}/room`, ticket), enabled: paidAccess, refetchInterval: 15000 });
  const results = useQuery({ queryKey: ['results', id, ticket, session?.user.id], queryFn: () => get<MatchResult[]>(`/api/v1/public/tournaments/${id}/results`, ticket), enabled: allowed, refetchInterval: 30000 });
  useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close(); }, []);
  async function action(task: () => Promise<unknown>, message: string) { setBusy(true); setError(''); try { await task(); setInfo(message); await cache.invalidateQueries(); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } }
  const t = listing.data?.tournament;
  return <dialog ref={dialog} className="tournament-dialog panel-form" onCancel={close} aria-label="Tournament room" onClick={(e) => { if (e.target === dialog.current) close(); }}><button className="close icon-button" onClick={close} aria-label="Close tournament"><X/></button>
    <p className="eyebrow">BATTLETRUST ARENA</p><h2>{t?.title || 'Loading tournament…'}</h2>
    {listing.isError && <p className="error">{errorMessage(listing.error)}</p>}
    {t && <><div className="card-art" style={{height:180}}><RoomBanner url={t.tournamentLogoUrl} title={t.title}/></div><p className="muted">{t.gameCategory.replaceAll('_', ' ')} · Hosted by {t.leaderName}</p><div className="room-summary"><div><span>ENTRY / PLAYER</span><strong>{money(t.entryFeePkr)}</strong></div><div><span>COLLECTED IN ESCROW</span><strong>{money(listing.data!.collectedPool)}</strong></div><span className="badge">{t.status}</span></div><Countdown start={t.startTime}/><p className="room-rules">{t.rules}</p>
    {error && <p className="error" role="alert">{error}</p>}{info && <p className="success" role="status">{info}</p>}
    {!entry.data && !isManager && !memberJoined && <><h3>Join this {t.squadSize}-player room</h3><p className="muted">No account needed. Keep your private ticket to access your entry later.</p><form onSubmit={(e) => { e.preventDefault(); const form = new FormData(e.currentTarget); const members = [{ name: String(form.get('captainName')).trim(), inGameId: String(form.get('captainUid')).trim() }]; void action(async () => { const body = { tournamentId: id, teamName: String(form.get('teamName')), captainName: String(form.get('captainName')), captainEmail: String(form.get('captainEmail')), whatsappNumber: String(form.get('phone')), squadMembers: members, roomInviteToken: sessionStorage.getItem('bte-room-invite-'+id) }; const registration = await post<Registration>(session ? `/api/v1/tournaments/${id}/join` : '/api/v1/public/registrations', body); saveTicket(registration); setTicket(registration.ticketCode); }, 'Entry reserved. Submit your payment reference for verification or pay from your wallet.'); }}>
      <div className="form-grid"><label>Squad name<input name="teamName" required maxLength={100}/></label><label>Captain / gamer tag<input name="captainName" required maxLength={100} defaultValue={session?.user.name}/></label><label>Contact email<input name="captainEmail" type="email" required defaultValue={session?.user.email}/></label><label>WhatsApp / phone<input name="phone" required maxLength={32}/></label></div><div className="form-grid"><label>Captain in-game UID<input name="captainUid" required maxLength={100}/></label></div><button className="button primary" disabled={busy || !t.registrationOpen || t.status !== 'UPCOMING' || new Date(t.startTime).getTime() <= Date.now()}>Reserve entry</button></form>
      <form onSubmit={(e) => { e.preventDefault(); const value = String(new FormData(e.currentTarget).get('ticket')).trim(); setTicket(value); }}><label>Already joined? Restore your private ticket<input name="ticket" required placeholder="Registration ticket"/></label><button className="button secondary">Restore entry</button></form></>}
    {entry.isError && <p className="error">Ticket could not be restored. Check your private ticket and try again.</p>}
    {entry.data && <div className="entry-panel"><h3>{entry.data.teamName} <span className="badge">{entry.data.slotNumber ? `CONFIRMED · Slot #${entry.data.slotNumber}` : entry.data.paymentStatus}</span></h3><details><summary>Your private registration ticket</summary><code className="ticket-code">{entry.data.ticketCode}</code><p className="muted">Save this securely. Anyone with this ticket can access your entry.</p></details>
      {t.squadSize>1 && <SquadInvite id={entry.data.id} ticket={ticket}/>}<VoucherCard registration={entry.data} ticket={ticket}/><SquadWalletLinks id={entry.data.id} ticket={ticket}/>{entry.data.paymentStatus === 'PENDING' && <><p className="muted">Total entry: {money(Number(t.entryFeePkr) * t.squadSize)}. A submitted reference stays pending until an administrator verifies receipt.</p><form onSubmit={(e) => { e.preventDefault(); const value = String(new FormData(e.currentTarget).get('reference')); void action(() => post('/api/v1/public/registrations/payment-reference', { registrationId: entry.data!.id, ticketCode: ticket, transactionId: value }), 'Reference submitted for admin verification.'); }}><label>Bank / payment transaction reference<input name="reference" required maxLength={128}/></label><button className="button secondary" disabled={busy}>Submit reference</button></form>{session && <button className="button primary" disabled={busy} onClick={() => void action(async () => { await post('/api/v1/me/registrations/claim', { ticketCode: ticket }); return post(`/api/v1/me/registrations/${entry.data!.id}/pay`, {}); }, 'Entry paid from your wallet. Tournament room unlocked.')}>Pay {money(Number(t.entryFeePkr) * t.squadSize)} from wallet</button>}</>}
      {session && <button className="text-link" disabled={busy} onClick={() => void action(() => post('/api/v1/me/registrations/claim', { ticketCode: ticket }), 'Entry and its existing ledger records linked to your profile.')}>Link entry to my account <ShieldCheck size={15}/></button>}
    </div>}
    {allowed ? <>{paidAccess && <div className="room-credentials"><LockKeyhole size={20}/><div><span>CONFIDENTIAL MATCH LOBBY</span><p>{room.data?.roomId ? `Room: ${room.data.roomId} · Password: ${room.data.roomPassword}` : 'The organizer has not published room credentials yet.'}</p>{room.isError && <p className="error">{errorMessage(room.error)}</p>}</div></div>}<TournamentChat id={id} ticket={ticket} canSend={isManager || t.roomChatEnabled}/>{t.plannedRounds>0&&<OverallStandings id={id} ticket={ticket}/>}<h3>Match results & dispute window</h3>{results.isError && <p className="error">{errorMessage(results.error)}</p>}{results.data?.length === 0 && <p className="muted">No match results submitted yet.</p>}{results.data?.map((result) => <div className="result-card" key={result.id}><span className="badge">{result.verificationStatus.replaceAll('_', ' ')}</span><p>Match {result.matchNumber} · Dispute deadline: {new Date(result.disputeDeadline).toLocaleString()}</p><div className="proof-images">{result.proofImageUrls.map((url) => <a key={url} href={mediaUrl(url)} target="_blank" rel="noreferrer"><img alt="Match result evidence" src={mediaUrl(url)}/></a>)}</div>{session && new Date(result.disputeDeadline).getTime() > Date.now() && result.verificationStatus === 'YELLOW_TICK_UNVERIFIED' && <form onSubmit={(e) => { e.preventDefault(); const data = new FormData(e.currentTarget); const file = data.get('proof') as File; void action(() => post('/api/v1/disputes/file-claim', multipart('claim', { matchResultId: result.id, reason: data.get('reason') }, [file], 'screenshot')), 'Dispute filed for admin review.'); }}><label>Dispute reason<textarea name="reason" required maxLength={2000}/></label><label>Evidence screenshot<input name="proof" type="file" accept="image/png,image/jpeg,image/gif" required/></label><button className="button secondary" disabled={busy}>File dispute</button></form>}</div>)}</> : <p className="locked-notice"><LockKeyhole size={16}/> Verified entry unlocks your tournament chat and lobby credentials.</p>}
    </>}
  </dialog>;
}
function TournamentChat({ id, ticket, canSend=true }: { id: number; ticket: string; canSend?:boolean }) {
  const session = useAuth((s) => s.session);
  const [messages, setMessages] = useState<Message[]>([]);
  const [status, setStatus] = useState('Connecting…');
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController(); let stopped = false;
    async function connect() {
      while (!stopped) {
        try {
          const headers: Record<string, string> = { Accept: 'text/event-stream' };
          const token = useAuth.getState().session?.token;
          if (token) headers.Authorization = `Bearer ${token}`;
          if (ticket) headers['X-Registration-Ticket'] = ticket;
          const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/v1/chat/tournament/${id}/stream`, { headers, credentials: 'include', signal: controller.signal });
          if (response.status === 401 && token) { await refreshSession(); continue; }
          if (!response.ok || !response.body) throw new Error('Room access unavailable. Your entry must be verified.');
          setStatus('Live · private tournament channel');
          const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
          while (!stopped) { const { value, done } = await reader.read(); if (done) break; buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n'); let boundary; while ((boundary = buffer.indexOf('\n\n')) >= 0) { const event = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2); const data = event.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n'); if (data) setMessages((JSON.parse(data) as Message[]).slice().reverse()); } }
        } catch (e) { if (stopped) return; setStatus(e instanceof Error ? e.message : 'Reconnecting…'); }
        if (!stopped) await new Promise((resolve) => setTimeout(resolve, 3000));
      }
    }
    void connect(); return () => { stopped = true; controller.abort(); };
  }, [id, ticket, session?.user.id]);
  return <div className="chat-room"><div className="chat-heading"><h3>Squad comms</h3><span>{status}</span></div><div className="chat-messages" aria-live="polite">{messages.length === 0 && <p className="muted">Your room is ready. Start the conversation.</p>}{messages.map((m) => <div key={m.id}><b>{m.senderName}</b><time>{new Date(m.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time><p>{m.messageText}</p></div>)}</div>{error && <p className="error">{error}</p>}<form onSubmit={async (e) => { e.preventDefault(); if (!text.trim() || sending) return; setSending(true); try { await post('/api/v1/chat/send', { tournamentId: id, senderName: session?.user.name || 'Guest', messageText: text }, ticket); setText(''); setError(''); } catch (error) { setError(errorMessage(error)); } finally { setSending(false); } }}><input aria-label="Tournament message" value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} placeholder={canSend?"Message your tournament…":"Chat muted by the organizer"} disabled={!canSend} required/><button className="button primary" disabled={sending || !canSend} aria-label="Send message"><Send size={17}/></button></form></div>;
}





