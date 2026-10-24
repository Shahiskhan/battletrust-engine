import { api } from './api';
export type Tournament = { plannedRounds:number; registrationOpen:boolean; createdAt:string; visibility: 'PUBLIC' | 'PRIVATE'; roomChatEnabled:boolean; tournamentLogoUrl?: string | null; id: number; title: string; gameType: string; gameCategory: string; entryFeePkr: number; maxPlayers: number; startTime: string; status: string; rules: string; leaderId: number; leaderName: string; winnersSharePercent: number; leaderSharePercent: number; systemSharePercent: number; squadSize: number; organizerStakeAmount: number; organizerStakeStatus: string };
export type Listing = { tournament: Tournament; registeredPlayers: number; collectedPool: number; potentialPool: number };
export type Registration = { id: number; tournamentId: number; teamName: string; captainName: string; paymentStatus: string; transactionId: string | null; ticketCode: string; slotNumber: number | null; slotStatus: string; squadMembers: { name: string; inGameId: string }[] };
export type Ledger = { id: number; amount: number; transactionType: string; referenceId: string; createdAt: string };
export type Wallet = { availableBalance: number; lockedBalance: number; totalEarned: number; organizerStakeBalance: number; escrowReservedBalance: number; ledgerHistory: Ledger[] };
export type Ranking = { placementPoints?:number; playerKills?:Record<number,number>; playerUserId: number; rank: number; kills: number; teamName: string };
export type MatchResult = { revision:number; id: number; tournamentId: number; matchNumber: number; rankings: Ranking[]; proofImageUrls: string[]; verificationStatus: string; disputeDeadline: string };
export type Message = { id: number; senderName: string; messageText: string; timestamp: string; isGuest: boolean };
export type Entry = { registration: Registration; tournament: Tournament };
export type Dispute = { id: number; tournamentId: number; matchResultId: number; reason: string; screenshotUrl: string; status: string; reporterEmail: string };
export type Approval = { id: number; tournamentId: number; requestedBy: string; payload: string; status: string };
export type Withdrawal = { id: number; email: string; amount: number; accountDetails: string; status: string };
export type Profile = { matchesPlayed: number; matchesWon: number; totalKills: number; reputationScore: number; winRatePercent: number };
export const get = async <T>(path: string, ticket?: string): Promise<T> => (await api.get<T>(path, { headers: ticket ? { 'X-Registration-Ticket': ticket } : {} })).data;
export const post = async <T>(path: string, data: unknown, ticket?: string): Promise<T> => (await api.post<T>(path, data, { headers: ticket ? { 'X-Registration-Ticket': ticket } : {} })).data;
export const money = (value: number | string) => `Rs ${Number(value).toLocaleString('en-PK', { maximumFractionDigits: 2 })}`;
export const mediaUrl = (url: string) => /^https?:\/\//i.test(url) ? url : `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}${url.startsWith('/')?'':'/'}${url}`;
export const ticketKey = (id: number) => `bte-ticket-${id}`;
export function savedTicket(id: number) { return typeof window === 'undefined' ? '' : sessionStorage.getItem(ticketKey(id)) || ''; }
export function saveTicket(entry: Registration) { sessionStorage.setItem(ticketKey(entry.tournamentId), entry.ticketCode); }
export function multipart(part: string, payload: unknown, files: File[], filePart: string) {
  const form = new FormData(); form.append(part, new Blob([JSON.stringify(payload)], { type: 'application/json' })); files.forEach((file) => form.append(filePart, file)); return form;
}

