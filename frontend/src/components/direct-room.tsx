'use client';
import {useEffect,useState} from 'react';
import {TournamentDialog} from './tournament-dialog';
export function DirectRoom({id}:{id:number}){const [ready,setReady]=useState(false);useEffect(()=>{const invite=new URLSearchParams(window.location.search).get('invite');if(invite){sessionStorage.setItem('bte-room-invite-'+id,invite);window.history.replaceState(null,'','/rooms/'+id);}setReady(true);},[id]);return <main className="dashboard container"><a className="brand" href="/tournaments">BATTLE<span>TRUST</span></a>{ready&&<TournamentDialog id={id} close={()=>window.location.assign('/tournaments')}/>}</main>;}
