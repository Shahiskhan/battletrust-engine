import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuth } from '@/stores/auth';
import type { AuthResponse, LeaderboardEntry, Page } from './types';

const config = { baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080', withCredentials: true, timeout: 15000 };
const authTransport = axios.create(config);
export const api = axios.create(config);
let refreshing: Promise<AuthResponse> | null = null;
export function refreshSession() {
  if (!refreshing) refreshing = authTransport.post<AuthResponse>('/api/v1/auth/refresh').then(({ data }) => { useAuth.getState().setSession(data); return data; }).catch((error) => { useAuth.getState().setSession(null); throw error; }).finally(() => { refreshing = null; });
  return refreshing;
}
api.interceptors.request.use((request) => {
  const token = useAuth.getState().session?.token;
  if (token) request.headers.Authorization = `Bearer ${token}`;
  const match=request.url?.match(/^\/api\/v1\/public\/tournaments\/(\d+)$/);
  if(match && typeof window!=='undefined'){const invite=sessionStorage.getItem('bte-room-invite-'+match[1]);if(invite)request.headers['X-Room-Invite']=invite;}
  return request;
});
api.interceptors.response.use((response) => response, async (error: AxiosError) => {
  const request = error.config as (InternalAxiosRequestConfig & { retried?: boolean }) | undefined;
  if (error.response?.status === 401 && useAuth.getState().session && request && !request.retried && !request.url?.startsWith('/api/v1/auth/')) {
    request.retried = true;
    const session = await refreshSession();
    request.headers.Authorization = `Bearer ${session.token}`;
    return api(request);
  }
  return Promise.reject(error);
});
export const authApi = {
  login: (body: { email: string; password: string }) => authTransport.post<AuthResponse>('/api/v1/auth/login', body),
  register: (body: { name: string; email: string; password: string; role?: 'ROLE_PLAYER' | 'ROLE_ORGANIZER' }) => authTransport.post<{email:string;verificationRequired:boolean;message:string}>('/api/v1/auth/register', body),
  verifyEmail: (token:string) => authTransport.post<{message:string}>('/api/v1/auth/verify-email',{token}),
  resendVerification: (email:string) => authTransport.post<{message:string}>('/api/v1/auth/resend-verification',{email}),
  google: (idToken: string) => authTransport.post<AuthResponse>('/api/v1/auth/google', { idToken }),
  logout: () => api.post('/api/v1/auth/logout'),
};
export const getLeaderboard = async () => (await api.get<Page<LeaderboardEntry>>('/api/v1/users/leaderboard', { params: { size: 5 } })).data;
export function errorMessage(error: unknown) {
  return axios.isAxiosError(error) ? error.response?.data?.message || (error.response ? 'Request failed. Please try again.' : 'Cannot reach BattleTrust. Check that the backend is running.') : 'Something went wrong. Please try again.';
}
