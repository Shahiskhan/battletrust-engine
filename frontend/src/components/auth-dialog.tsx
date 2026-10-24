'use client';
import { useEffect, useRef, useState } from 'react';
import Script from 'next/script';
import { X, ShieldCheck, ArrowUpRight, LoaderCircle } from 'lucide-react';
import { authApi, errorMessage } from '@/lib/api';
import { useAuth } from '@/stores/auth';
import { VerificationPanel } from './email-verification';
type GoogleIdentity = { accounts: { id: { initialize: (options: { client_id: string; callback: (response: { credential: string }) => void }) => void; renderButton: (element: HTMLElement, options: object) => void } } };
export function AuthDialog({ initial, close }: { initial: 'login' | 'register'; close: () => void }) {
  const [mode, setMode] = useState<'login' | 'register' | 'verify'>(initial);
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const dialog = useRef<HTMLDialogElement>(null);
  const googleButton = useRef<HTMLDivElement>(null);
  const setSession = useAuth((s) => s.setSession);
  useEffect(() => { const el = dialog.current; el?.showModal(); return () => el?.close(); }, []);
  async function googleLogin(credential: string) {
    setBusy(true); setError('');
    try { setSession((await authApi.google(credential)).data); close(); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  function initGoogle() {
    const google = (window as Window & { google?: GoogleIdentity }).google;
    if (!google || !googleButton.current) return;
    google.accounts.id.initialize({ client_id: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID!, callback: (r) => void googleLogin(r.credential) });
    google.accounts.id.renderButton(googleButton.current, { theme: 'outline', size: 'large', width: 320 });
  }
  return <dialog ref={dialog} className="auth-dialog" onCancel={close} onClick={(e) => { if (e.target === dialog.current) close(); }}>
    <button className="close icon-button" onClick={close} aria-label="Close authentication"><X size={20}/></button>
    <ShieldCheck className="cyan" size={36}/><p className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</p><h2>{mode === 'login' ? 'Welcome back, player.' : mode === 'verify' ? 'Verify your email.' : 'Claim your place.'}</h2><p className="muted">One account. A whole new arena.</p>
    {message && <p className="success" role="status">{message}</p>}{mode === 'verify' ? <VerificationPanel initialEmail={email} onVerified={() => { setMode('login'); setMessage('Email verified. Sign in to continue.'); }}/> : <form onSubmit={async (event) => { event.preventDefault(); const data = new FormData(event.currentTarget); setBusy(true); setError(''); try { const body = { name: String(data.get('name') || ''), email: String(data.get('email')), password: String(data.get('password')), role: String(data.get('role') || 'ROLE_PLAYER') as 'ROLE_PLAYER' | 'ROLE_ORGANIZER' }; setEmail(body.email); if (mode === 'login') { setSession((await authApi.login(body)).data); close(); } else { const pending = (await authApi.register(body)).data; setMessage(pending.message); setMode('verify'); } } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } }}>
      {mode === 'register' && <label>Player name<input name="name" required maxLength={100} autoComplete="nickname" placeholder="Your in-game alias"/></label>}
      {mode === 'register' && <label>Account role<select name="role" defaultValue="ROLE_PLAYER"><option value="ROLE_PLAYER">Player — join rooms and compete</option><option value="ROLE_ORGANIZER">Organizer — create and manage tournaments</option></select></label>}<label>Email address<input name="email" type="email" required defaultValue={email} autoComplete="email" placeholder="player@example.com"/></label>
      <label>Password<input name="password" type="password" required minLength={mode === 'register' ? 8 : 1} maxLength={72} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} placeholder="Enter your password"/></label>
      {error && <p role="alert" className="error">{error}</p>}
      <button className="button primary" disabled={busy}>{busy ? <LoaderCircle className="spin" size={18}/> : mode === 'login' ? 'Enter the arena' : 'Create account'}<ArrowUpRight size={18}/></button>
    </form>}
    {mode !== 'verify' && process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && <><Script src="https://accounts.google.com/gsi/client" onReady={initGoogle}/><div className="google-button" ref={googleButton}/></>}
    <button className="text-link verification-help" onClick={() => { setMode('verify'); setError(''); }}>Need to verify your email?</button><p className="auth-switch">{mode === 'login' ? 'New to BattleTrust?' : 'Already a contender?'} <button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>{mode === 'login' ? 'Join the arena' : 'Sign in'}</button></p>
  </dialog>;
}

