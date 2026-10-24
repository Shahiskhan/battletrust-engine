'use client';
import {useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {api,errorMessage} from '@/lib/api';
import {useAuth} from '@/stores/auth';

export function AdminPassword(){
 const [busy,setBusy]=useState(false),[error,setError]=useState('');const cache=useQueryClient();
 return <section className="dashboard-panel panel-form"><p className="eyebrow">ACCOUNT SECURITY</p><h2>Change admin password</h2><p className="muted">Use at least 12 characters, up to 72 UTF-8 bytes. Updating your password signs out all sessions.</p>{error&&<p className="error" role="alert">{error}</p>}<form onSubmit={async e=>{e.preventDefault();const form=e.currentTarget,data=new FormData(form);const next=String(data.get('newPassword'));if(next!==data.get('confirmPassword')){setError('New passwords do not match.');return;}if(new TextEncoder().encode(next).length>72){setError('Password must not exceed 72 UTF-8 bytes.');return;}setBusy(true);setError('');try{await api.put('/api/v1/admin/account/password',{currentPassword:data.get('currentPassword'),newPassword:next});form.reset();await cache.cancelQueries();cache.clear();useAuth.getState().setSession(null);window.location.assign('/dashboard?passwordChanged=1');}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}}><label>Current password<input type="password" name="currentPassword" autoComplete="current-password" required maxLength={72}/></label><label>New password<input type="password" name="newPassword" autoComplete="new-password" required minLength={12} maxLength={72}/></label><label>Confirm new password<input type="password" name="confirmPassword" autoComplete="new-password" required minLength={12} maxLength={72}/></label><button className="button primary" disabled={busy}>{busy?'Updating…':'Update password and sign out'}</button></form></section>;
}
