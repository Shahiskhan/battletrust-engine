'use client';
import {useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {api,errorMessage} from '@/lib/api';
import {mediaUrl} from '@/lib/platform';
import {useAuth} from '@/stores/auth';
export function MediaUpload({kind,tournamentId}:{kind:'avatar'|'banner';tournamentId?:number}){
 const cache=useQueryClient();const[busy,setBusy]=useState(false);const[error,setError]=useState('');const[url,setUrl]=useState('');const[message,setMessage]=useState('');
 return <section className="media-upload"><h3>{kind==='avatar'?'Your player avatar':'Tournament banner'}</h3><p className="muted">Upload a JPEG, PNG or GIF image, up to 5 MB.</p>{url&&<img src={mediaUrl(url)} alt={kind==='avatar'?'Updated player avatar':'Updated tournament banner'}/>}<form onSubmit={async e=>{e.preventDefault();const data=new FormData(e.currentTarget);const file=data.get('file') as File;if(file.size>5*1024*1024){setError('Image must not exceed 5 MB.');return;}setBusy(true);setError('');setMessage('');try{const result=(await api.post<{url:string}>(kind==='avatar'?'/api/v1/me/avatar':`/api/v1/organizer/tournaments/${tournamentId}/banner`,data)).data;setUrl(result.url);if(kind==='avatar'){const current=useAuth.getState().session;if(current)useAuth.getState().setSession({...current,user:{...current.user,profilePictureUrl:result.url}});}await cache.invalidateQueries();setMessage('Image updated.');}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}}><label>{kind==='avatar'?'Avatar image':'Banner image'}<input type="file" name="file" accept="image/png,image/jpeg,image/gif" required/></label><button className="button secondary" disabled={busy}>{busy?'Uploading…':'Upload image'}</button></form>{error&&<p className="error" role="alert">{error}</p>}{message&&<p className="success" role="status">{message}</p>}</section>;
}
