import {DirectRoom} from '@/components/direct-room';
export const metadata={title:'Tournament room | BattleTrust',robots:{index:false,follow:false},referrer:'no-referrer'};
export default async function Page({params}:{params:Promise<{id:string}>}){const {id}=await params;return <DirectRoom id={Number(id)}/>;}
