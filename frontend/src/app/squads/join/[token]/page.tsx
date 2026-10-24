import {SquadJoin} from '@/components/squad-invite';
export const metadata={title:'Join your squad | BattleTrust',robots:{index:false,follow:false},referrer:'no-referrer'};
export default async function Page({params}:{params:Promise<{token:string}>}){const {token}=await params;return <SquadJoin token={token}/>;}
