import {TournamentDiscovery} from '@/components/tournament-discovery';
import {ShieldCheck,ArrowLeft} from 'lucide-react';
export const metadata={title:'Tournaments — BattleTrust'};
export default function TournamentsPage(){return <><header className="dashboard-header"><a className="brand" href="/"><ShieldCheck className="cyan"/> BATTLE<span>TRUST</span></a><a className="text-link" href="/"><ArrowLeft size={15}/>Home</a><a className="text-link" href="/dashboard">Dashboard</a></header><main><TournamentDiscovery/></main></>;}
