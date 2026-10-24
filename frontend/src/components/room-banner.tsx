'use client';
import {useEffect,useState} from 'react';
import {mediaUrl} from '@/lib/platform';
export function RoomBanner({url,title}:{url?:string|null;title:string}){const [failed,setFailed]=useState(false);useEffect(()=>setFailed(false),[url]);return <img className="dynamic-room-banner" src={url&&!failed?mediaUrl(url):'/room-placeholder.svg'} alt={title+' tournament banner'} onError={()=>setFailed(true)}/>;}
