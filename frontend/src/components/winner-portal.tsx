'use client';
import {useQuery} from '@tanstack/react-query';
import {get,type MatchResult} from '@/lib/platform';
import {errorMessage} from '@/lib/api';
export function WinnerPortalStatus({id}:{id:number}){
 const results=useQuery({queryKey:['winner-portal',id],queryFn:()=>get<MatchResult[]>(`/api/v1/organizer/tournaments/${id}/winner-portal`),refetchInterval:15000});
 return <section className="dashboard-panel"><p className="eyebrow">OFFICIAL WINNER PORTAL</p><h2>Published standings</h2><p className="muted">Rank 1 marks the winning team. Closing play submits results for review; final room completion follows escrow settlement after dispute checks.</p>{results.isError&&<p className="error">{errorMessage(results.error)}</p>}{results.isPending&&<p className="muted">Loading standings…</p>}{results.data?.length===0&&<p className="muted">No official result yet. Publish standings below when the room is live.</p>}{results.data?.map(result=><article className="result-card" key={result.id}><span className="badge">{result.verificationStatus.replaceAll('_',' ')}</span><h3>Match {result.matchNumber}</h3>{result.rankings.map(row=><p key={row.playerUserId}>{row.rank===1?'🏆 Winner':'Rank '+row.rank} · {row.teamName||'Captain #'+row.playerUserId} · {row.kills} kills</p>)}<p className="muted">Dispute deadline: {new Date(result.disputeDeadline).toLocaleString()}</p></article>)}</section>;
}
