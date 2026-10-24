'use client';
import {useQueryClient} from '@tanstack/react-query';
import {useState} from 'react';
import {ShieldCheck,LogOut} from 'lucide-react';
import {AdminPanel} from './admin-panel';
import {useAuth} from '@/stores/auth';
import {authApi,errorMessage} from '@/lib/api';

export function AdminShell(){
 const session=useAuth(s=>s.session);const cache=useQueryClient();const [error,setError]=useState('');
 return <><header className="dashboard-header"><a className="brand" href="/"><ShieldCheck className="cyan"/> BATTLE<span>TRUST</span></a><button className="text-link" onClick={async()=>{try{await authApi.logout();cache.clear();useAuth.getState().setSession(null);window.location.assign('/');}catch(e){setError(errorMessage(e));}}}><LogOut size={15}/> Sign out</button></header><main className="dashboard container panel-form"><div className="section-heading"><div><p className="eyebrow">SYSTEM ADMINISTRATION</p><h1>Platform command center</h1><p className="muted">Manage organizers, review tournament finances and disputes, and authorize escrow releases.</p></div><span className="badge">SYSTEM ADMIN</span></div><p className="muted">Signed in as {session?.user.email}</p>{error&&<p className="error" role="alert">{error}</p>}<AdminPanel/></main></>;
}
